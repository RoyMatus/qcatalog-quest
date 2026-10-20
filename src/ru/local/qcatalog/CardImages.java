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
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Bitmap> cache=new HashMap<>();private final Set<String> requested=new HashSet<>(),pending=new HashSet<>();private final Set<Source> active=new HashSet<>();
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
    synchronized int total(){return requested.size();}
    synchronized long bytes(){return bytes;}
    synchronized int pending(){return pending.size();}
    void prefetch(Collection<String> keys){for(String key:keys)if(!key.startsWith("video:"))request(key);}
    synchronized void request(String key){
        if(closed||requested.contains(key)||requested.size()>=MAX_IMAGES)return;requested.add(key);pending.add(key);
        io.execute(()->{
            Source source=null;Bitmap loaded=null;
            try{source=factory.open();synchronized(this){if(closed)return;active.add(source);}loaded=GameDetails.limitImage(source.load(key,pixels),pixels);}
            catch(Exception|OutOfMemoryError ignored){}
            finally{if(source!=null)source.close();synchronized(this){active.remove(source);}}
            final Bitmap result=loaded;
            main.post(()->{synchronized(this){
                pending.remove(key);if(closed){if(result!=null)result.recycle();return;}
                if(result!=null){int count=result.getAllocationByteCount();if(bytes+count<=limit){cache.put(key,result);bytes+=count;}else result.recycle();}
                ready.accept(key,cache.get(key));
            }});
        });
    }
    @Override public void close(){List<Source> running;synchronized(this){if(closed)return;closed=true;running=new ArrayList<>(active);for(Bitmap bitmap:cache.values())bitmap.recycle();cache.clear();requested.clear();pending.clear();bytes=0;}for(Source source:running)source.close();io.shutdownNow();}
}
