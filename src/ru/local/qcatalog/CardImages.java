package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Per-card, bounded decoded-image memory. No files, disk cache, or cross-card lifetime. */
final class CardImages implements AutoCloseable {
    static final int MAX_IMAGES=17;
    interface Source extends AutoCloseable { Bitmap load(String key,int pixels)throws Exception; void close(); }
    interface Factory { Source open(); }
    private final Factory factory;private final BiConsumer<String,Bitmap> ready;
    private final LinkedBlockingDeque<Runnable> waiting=new LinkedBlockingDeque<>();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,waiting);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Bitmap> cache=new HashMap<>();private final Set<String> requested=new HashSet<>(),pending=new HashSet<>();private final Set<Source> active=new HashSet<>();
    private final Map<String,Runnable> tasks=new HashMap<>();
    private final Map<String,int[]> signatures=new HashMap<>();
    final long limit=Math.min(64L*1024*1024,Runtime.getRuntime().maxMemory()/4);
    final int pixels=(int)(limit/(MAX_IMAGES*4));private long bytes;private boolean closed;
    CardImages(Context context,String pkg,BiConsumer<String,Bitmap> ready){this(()->new Source(){
        final GameDetails session=new GameDetails(context);
        public Bitmap load(String key,int pixels)throws Exception{return key.isEmpty()?session.cover(pkg,pixels):session.image(key,pixels);}
        public void close(){session.close();}
    },ready);}
    CardImages(Factory factory,BiConsumer<String,Bitmap> ready){this.factory=factory;this.ready=ready;}
    synchronized Bitmap get(String key){return cache.get(key);}
    synchronized int size(){return cache.size();}
    synchronized long bytes(){return bytes;}
    synchronized int pending(){return pending.size();}
    synchronized boolean pending(String key){return pending.contains(key);}
    synchronized void prioritize(String key){Runnable task=tasks.get(key);if(task!=null&&waiting.remove(task))waiting.offerFirst(task);}
    synchronized boolean same(String a,String b){int[] x=signatures.get(a),y=signatures.get(b);if(x==null||y==null||Math.abs(x[0]-y[0])>10)return false;long difference=0;for(int i=1;i<x.length;i++){int delta=Math.abs(x[i]-y[i]);if(delta>14)return false;difference+=delta;}return difference<=(x.length-1)*2L;}
    private static int[] signature(Bitmap bitmap){int[] result=new int[1+24*24*3];result[0]=(int)(1000L*bitmap.getWidth()/bitmap.getHeight());Bitmap small=Bitmap.createScaledBitmap(bitmap,24,24,true);int n=1;for(int y=0;y<24;y++)for(int x=0;x<24;x++){int pixel=small.getPixel(x,y);result[n++]=(pixel>>16)&255;result[n++]=(pixel>>8)&255;result[n++]=pixel&255;}if(small!=bitmap)small.recycle();return result;}
    void prefetch(Collection<String> keys){for(String key:keys)if(!key.startsWith("video:"))request(key);}
    synchronized void request(String key){
        if(closed||requested.contains(key)||requested.size()>=MAX_IMAGES)return;requested.add(key);pending.add(key);
        Runnable task=()->{
            Source source=null;Bitmap loaded=null;
            try{source=factory.open();synchronized(this){if(closed)return;active.add(source);}loaded=GameDetails.limitImage(source.load(key,pixels),pixels);}
            catch(Exception|OutOfMemoryError ignored){}
            finally{if(source!=null)source.close();synchronized(this){active.remove(source);}}
            final Bitmap result=loaded;final int[] signature=result==null?null:signature(result);
            main.post(()->{synchronized(this){
                pending.remove(key);tasks.remove(key);if(closed){if(result!=null)result.recycle();return;}
                if(result!=null){int count=result.getAllocationByteCount();if(bytes+count<=limit){cache.put(key,result);signatures.put(key,signature);bytes+=count;}else result.recycle();}
                if(!cache.containsKey(key))requested.remove(key); // A failed frame can be retried on selection.
                ready.accept(key,cache.get(key));
            }});
        };tasks.put(key,task);io.execute(task);
    }
    @Override public void close(){List<Source> running;synchronized(this){if(closed)return;closed=true;running=new ArrayList<>(active);for(Bitmap bitmap:cache.values())bitmap.recycle();cache.clear();signatures.clear();tasks.clear();requested.clear();pending.clear();bytes=0;}for(Source source:running)source.close();io.shutdownNow();}
}
