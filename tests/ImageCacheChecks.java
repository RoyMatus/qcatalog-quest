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
        AtomicInteger attempts=new AtomicInteger();CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1);
        CardImages retry=new CardImages(()->new CardImages.Source(){public Bitmap load(String key,int pixels){return attempts.incrementAndGet()==1?null:Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);}public void close(){}},(key,bitmap)->{if(bitmap==null)first.countDown();else second.countDown();});
        try{retry.request("retry");require(first.await(5,TimeUnit.SECONDS),"Failed image attempt completes");retry.request("retry");require(second.await(5,TimeUnit.SECONDS)&&retry.get("retry")!=null,"Selecting a failed frame retries instead of permanently keeping its placeholder");}finally{retry.close();}
        return checks;
    }
    static int covers(android.app.Activity activity,Instrumentation instrumentation)throws Exception{
        checks=0;AtomicInteger opens=new AtomicInteger();CountDownLatch firstStarted=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondReady=new CountDownLatch(1);AtomicReference<Bitmap> late=new AtomicReference<>(),expected=new AtomicReference<>();
        CoverLoader loader=new CoverLoader(()->{int attempt=opens.incrementAndGet();return new CardImages.Source(){public Bitmap load(String key,int pixels)throws Exception{if(attempt==1){firstStarted.countDown();try{releaseFirst.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}Bitmap result=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);late.set(result);return result;}Bitmap result=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);expected.set(result);secondReady.countDown();return result;}public void close(){if(attempt==1)releaseFirst.countDown();}};});
        android.widget.LinearLayout[] host={null};android.widget.ImageView[] image={null};android.app.Dialog[] dialog={null};
        try{
            instrumentation.runOnMainSync(()->{host[0]=new android.widget.LinearLayout(activity);image[0]=new android.widget.ImageView(activity);loader.bind(image[0],"org.test.cover");require(opens.get()==0,"Binding before attachment does not start an invisible request");host[0].addView(image[0],new android.widget.LinearLayout.LayoutParams(100,100));dialog[0]=new android.app.AlertDialog.Builder(activity).setView(host[0]).create();dialog[0].show();});
            require(firstStarted.await(5,TimeUnit.SECONDS),"Attaching a prebound grid cover starts its load");
            instrumentation.runOnMainSync(()->{host[0].removeView(image[0]);host[0].addView(image[0],new android.widget.LinearLayout.LayoutParams(100,100));});
            require(secondReady.await(5,TimeUnit.SECONDS),"Reattaching a recycled cover restarts its cancelled request");
            long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline&&(late.get()==null||!late.get().isRecycled())){Thread.sleep(20);instrumentation.waitForIdleSync();}
            instrumentation.runOnMainSync(()->{require(image[0].getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)image[0].getDrawable()).getBitmap()==expected.get(),"Late cancelled cover cannot overwrite the currently bound bitmap");require(late.get()!=null&&late.get().isRecycled(),"Cancelled cover pixels are released");loader.release(image[0]);host[0].removeView(image[0]);host[0].addView(image[0]);require(opens.get()==2,"Explicit release removes the binding and prevents reattachment requests");});
        }finally{instrumentation.runOnMainSync(()->{loader.close();if(dialog[0]!=null)dialog[0].dismiss();});}
        return checks;
    }
    static int coverWindow(android.app.Activity activity,Instrumentation instrumentation)throws Exception{
        checks=0;List<String> loaded=new CopyOnWriteArrayList<>();AtomicInteger opens=new AtomicInteger(),pixels=new AtomicInteger();CountDownLatch initial=new CountDownLatch(CoverLoader.WORKERS),nextReady=new CountDownLatch(1);
        CoverLoader loader=new CoverLoader(()->{opens.incrementAndGet();CountDownLatch cancel=new CountDownLatch(1);return new CardImages.Source(){public Bitmap load(String key,int budget)throws Exception{loaded.add(key);pixels.set(budget);if(key.startsWith("far-")){initial.countDown();try{cancel.await(8,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}if(key.equals("next"))nextReady.countDown();return Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);}public void close(){cancel.countDown();}};});
        android.widget.LinearLayout[] host={null};android.app.Dialog[] dialog={null};
        try{
            List<String> window=new ArrayList<>();for(int i=0;i<100;i++)window.add("far-"+i);
            instrumentation.runOnMainSync(()->{loader.prefetch(window);try{java.lang.reflect.Field ahead=CoverLoader.class.getDeclaredField("ahead");ahead.setAccessible(true);require(((Set<?>)ahead.get(loader)).size()==CoverLoader.MAX_AHEAD,"Cover lookahead is capped even if caller supplies an entire catalogue");}catch(Exception e){throw new RuntimeException(e);}});
            require(initial.await(5,TimeUnit.SECONDS)&&opens.get()==CoverLoader.WORKERS,"Only the bounded number of cover requests starts concurrently");
            instrumentation.runOnMainSync(()->loader.prefetch(Collections.singletonList("next")));
            require(nextReady.await(5,TimeUnit.SECONDS),"Changing the viewport cancels obsolete work and starts its new cover window");Thread.sleep(80);instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{host[0]=new android.widget.LinearLayout(activity);for(int i=0;i<2;i++){android.widget.ImageView image=new android.widget.ImageView(activity);loader.bind(image,"next");host[0].addView(image,new android.widget.LinearLayout.LayoutParams(100,100));}dialog[0]=new android.app.AlertDialog.Builder(activity).setView(host[0]).create();dialog[0].show();});instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{require(opens.get()==CoverLoader.WORKERS+1,"Two visible tiles reuse one prefetched package without duplicate downloads");for(int i=0;i<2;i++)require(((android.widget.ImageView)host[0].getChildAt(i)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Prefetched cover is available as soon as its tile attaches");require(pixels.get()==CoverLoader.PIXELS&&pixels.get()<1000*1000,"List covers use a thumbnail pixel budget instead of gallery-sized images");require(loaded.stream().noneMatch(key->key.startsWith("far-")&&Integer.parseInt(key.substring(4))>=CoverLoader.MAX_AHEAD),"Distant catalogue covers are never requested");});
        }finally{instrumentation.runOnMainSync(()->{loader.close();if(dialog[0]!=null)dialog[0].dismiss();});}
        return checks;
    }
}
