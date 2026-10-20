package ru.local.qcatalog;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** RAM prefetch, reuse, bounds and cancellation; no network or media files. */
final class ImageCacheChecks {
    private static int checks;
    private static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static int run(Context context,Instrumentation instrumentation)throws Exception {
        checks=0;Set<String> before=MediaChecks.files(context.getFilesDir());before.addAll(MediaChecks.files(context.getCacheDir()));
        AtomicInteger loads=new AtomicInteger(),concurrent=new AtomicInteger(),peak=new AtomicInteger();CountDownLatch complete=new CountDownLatch(CardImages.MAX_IMAGES);
        List<String> keys=new ArrayList<>();for(int i=0;i<CardImages.MAX_IMAGES;i++)keys.add("photo-"+i);
        CardImages cache=new CardImages(()->new CardImages.Source(){
            public Bitmap load(String key,int pixels)throws Exception{loads.incrementAndGet();int current=concurrent.incrementAndGet();peak.updateAndGet(old->Math.max(old,current));try{Thread.sleep(10);return Bitmap.createBitmap(1200,1000,Bitmap.Config.ARGB_8888);}finally{concurrent.decrementAndGet();}}
            public void close(){}
        },(key,bitmap)->complete.countDown());
        List<Bitmap> retained=new ArrayList<>();
        try{
            cache.prefetch(keys);require(complete.await(15,TimeUnit.SECONDS),"Every image is prefetched before selection");
            require(cache.size()==CardImages.MAX_IMAGES&&cache.pending()==0,"All card images are resident together");require(loads.get()==CardImages.MAX_IMAGES,"Each image loads exactly once");require(peak.get()<=2,"Prefetch uses at most two concurrent decoders");require(cache.bytes()>0&&cache.bytes()<=cache.limit,"Decoded RAM remains within card memory budget");
            for(String key:keys){Bitmap bitmap=cache.get(key);require(bitmap!=null&&!bitmap.isRecycled(),"Prefetched bitmap is immediately available");retained.add(bitmap);cache.request(key);require(cache.get(key)==bitmap,"Selecting again uses the same decoded bitmap");}
            cache.prefetch(keys);require(loads.get()==CardImages.MAX_IMAGES,"Revisiting frames and prefetching twice cause no extra requests");
            Set<String> after=MediaChecks.files(context.getFilesDir());after.addAll(MediaChecks.files(context.getCacheDir()));require(before.equals(after),"Prefetch creates no files in private storage or disk cache");
        }finally{cache.close();}
        require(cache.size()==0&&cache.bytes()==0&&cache.pending()==0,"Closing card removes all cache entries and byte accounting");for(Bitmap bitmap:retained)require(bitmap.isRecycled(),"Closing releases decoded pixel memory");
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean cancelled=new AtomicBoolean();AtomicReference<Bitmap> late=new AtomicReference<>();AtomicInteger callbacks=new AtomicInteger();
        CardImages pending=new CardImages(()->new CardImages.Source(){
            public Bitmap load(String key,int pixels)throws Exception{started.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}Bitmap result=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);late.set(result);return result;}
            public void close(){cancelled.set(true);release.countDown();}
        },(key,bitmap)->callbacks.incrementAndGet());
        try{pending.request("slow");require(started.await(5,TimeUnit.SECONDS),"Slow request started");pending.close();long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline&&(late.get()==null||!late.get().isRecycled())){Thread.sleep(25);instrumentation.waitForIdleSync();}require(cancelled.get(),"Closing cancels in-flight network source");require(late.get()!=null&&late.get().isRecycled(),"Late result after close releases its bitmap");require(callbacks.get()==0&&pending.size()==0,"Late result cannot repopulate a closed card");}finally{pending.close();}
        return checks;
    }
}
