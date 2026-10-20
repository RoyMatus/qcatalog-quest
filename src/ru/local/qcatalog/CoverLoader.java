package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.*;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.util.*;
import java.util.concurrent.*;

/** Visible covers first, then a small scroll window. Shared requests and a bounded RAM cache. */
final class CoverLoader implements AutoCloseable {
    static final int PIXELS=360*240,MAX_AHEAD=10,WORKERS=4;
    private final CardImages.Factory factory;
    private final ExecutorService workers=Executors.newFixedThreadPool(WORKERS);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<ImageView,String> bound=new WeakHashMap<>();
    private final Set<ImageView> observed=Collections.newSetFromMap(new WeakHashMap<ImageView,Boolean>());
    private final Set<ImageView> detached=Collections.newSetFromMap(new WeakHashMap<ImageView,Boolean>());
    private final Map<String,Request> active=new LinkedHashMap<>();
    private final Set<String> ahead=new LinkedHashSet<>();
    private final Map<String,Integer> failures=new HashMap<>();
    private final Map<String,Long> retryAt=new HashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private boolean closed;
    private static final class Request {final CardImages.Source source;Future<?> future;Request(CardImages.Source source){this.source=source;}}
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){detached.remove(view);pump();}
        public void onViewDetachedFromWindow(View view){detached.add((ImageView)view);pump();}
    };
    CoverLoader(Context c){this(()->new CardImages.Source(){final GameDetails session=new GameDetails(c);public Bitmap load(String pkg,int pixels)throws Exception{return session.cover(pkg,pixels);}public void close(){session.close();}});}
    CoverLoader(CardImages.Factory factory){this.factory=factory;}
    void bind(ImageView view,String pkg){
        if(closed)return;String previous=bound.put(view,pkg);if(observed.add(view))view.addOnAttachStateChangeListener(attachment);
        if(!pkg.equals(previous))view.setImageResource(R.drawable.qc_cover_placeholder);
        pump();
    }
    void prefetch(Collection<String> packages){if(closed)return;ahead.clear();for(String pkg:packages){ahead.add(pkg);if(ahead.size()>=MAX_AHEAD)break;}pump();}
    private LinkedHashSet<String> wanted(){
        LinkedHashSet<String> wanted=new LinkedHashSet<>();
        for(Map.Entry<ImageView,String> entry:bound.entrySet()){ImageView image=entry.getKey();if(!detached.contains(image)&&image.isAttachedToWindow()&&image.isShown()){String pkg=entry.getValue();Bitmap cached=cache.get(pkg);if(cached!=null&&(!(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)||((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap()!=cached))image.setImageBitmap(cached);wanted.add(pkg);}}
        return wanted;
    }
    private void pump(){
        if(closed)return;Set<String> visible=wanted(),wanted=new LinkedHashSet<>(visible);wanted.addAll(ahead);
        for(String pkg:new ArrayList<>(active.keySet()))if(!wanted.contains(pkg))cancel(pkg);
        boolean visibleWaiting=false;for(String pkg:visible)if(cache.get(pkg)==null&&!active.containsKey(pkg)&&failures.getOrDefault(pkg,0)<3&&SystemClock.uptimeMillis()>=retryAt.getOrDefault(pkg,0L)){visibleWaiting=true;break;}
        if(visibleWaiting)for(String pkg:new ArrayList<>(active.keySet()))if(!visible.contains(pkg))cancel(pkg);
        failures.keySet().retainAll(wanted);retryAt.keySet().retainAll(wanted);
        for(String pkg:wanted){if(active.size()>=WORKERS)break;if(cache.get(pkg)!=null||active.containsKey(pkg)||failures.getOrDefault(pkg,0)>=3||SystemClock.uptimeMillis()<retryAt.getOrDefault(pkg,0L))continue;
            Request request=new Request(factory.open());active.put(pkg,request);
            request.future=workers.submit(()->{Bitmap loaded=null;try{loaded=request.source.load(pkg,PIXELS);}catch(Exception|OutOfMemoryError ignored){}finally{request.source.close();}final Bitmap result=loaded;
                ui.post(()->{if(closed||active.get(pkg)!=request){if(result!=null)result.recycle();return;}active.remove(pkg);
                    if(result!=null){cache.put(pkg,result);failures.remove(pkg);retryAt.remove(pkg);}else{int count=failures.getOrDefault(pkg,0)+1;failures.put(pkg,count);retryAt.put(pkg,SystemClock.uptimeMillis()+3000);if(count<3)ui.postDelayed(CoverLoader.this::pump,3000);}pump();});
            });
        }
    }
    private void cancel(String pkg){Request request=active.remove(pkg);if(request!=null){request.source.close();if(request.future!=null)request.future.cancel(true);}}
    void release(ImageView view){bound.remove(view);pump();}
    @Override public void close(){closed=true;ui.removeCallbacksAndMessages(null);for(String pkg:new ArrayList<>(active.keySet()))cancel(pkg);for(ImageView view:new ArrayList<>(observed))view.removeOnAttachStateChangeListener(attachment);observed.clear();detached.clear();bound.clear();ahead.clear();failures.clear();retryAt.clear();workers.shutdownNow();cache.evictAll();}
}
