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
    private final Map<String,Request> active=new LinkedHashMap<>();
    private final Set<String> ahead=new LinkedHashSet<>();
    private final Set<String> viewport=new LinkedHashSet<>();
    private boolean explicitViewport;
    private final Map<String,Integer> failures=new HashMap<>();
    private final Map<String,Long> retryAt=new HashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private boolean closed,paused;
    private final Runnable update=this::pump;
    private void schedule(){if(!closed){ui.removeCallbacks(update);ui.post(update);}}
    private static final class Request {final CardImages.Source source;Future<?> future;Request(CardImages.Source source){this.source=source;}}
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){schedule();}
        public void onViewDetachedFromWindow(View view){schedule();}
    };
    CoverLoader(Context c){this(()->new CardImages.Source(){final GameDetails session=new GameDetails(c);public Bitmap load(String pkg,int pixels)throws Exception{return session.cover(pkg,pixels);}public void close(){session.close();}});}
    CoverLoader(CardImages.Factory factory){this.factory=factory;}
    Bitmap peek(String pkg){return cache.get(pkg);}
    void accept(String pkg,Bitmap cover){
        if(closed||cover==null||cover.isRecycled())return;
        double scale=Math.min(1d,Math.sqrt((double)PIXELS/((long)cover.getWidth()*cover.getHeight())));
        Bitmap small=Bitmap.createScaledBitmap(cover,Math.max(1,(int)(cover.getWidth()*scale)),Math.max(1,(int)(cover.getHeight()*scale)),true);
        // CardImages recycles its pixels on close. The list must own an independent thumbnail.
        Bitmap owned=small==cover?cover.copy(Bitmap.Config.ARGB_8888,false):small;
        if(owned==null)return;
        cancel(pkg);cache.put(pkg,owned);failures.remove(pkg);retryAt.remove(pkg);
        wanted();pump();
    }
    void pause(boolean value){paused=value;if(paused){for(String pkg:new ArrayList<>(active.keySet()))if(!viewport.contains(pkg))cancel(pkg);}else schedule();}
    void bind(ImageView view,String pkg){
        if(closed)return;String previous=bound.put(view,pkg);if(observed.add(view))view.addOnAttachStateChangeListener(attachment);
        if(!pkg.equals(previous))view.setImageResource(R.drawable.qc_cover_placeholder);
        Bitmap ready=cache.get(pkg);if(ready!=null)view.setImageBitmap(ready);schedule();
    }
    void prefetch(Collection<String> packages){if(closed)return;ahead.clear();for(String pkg:packages){ahead.add(pkg);if(ahead.size()>=MAX_AHEAD)break;}pump();}
    void viewport(Collection<String> visible,Collection<String> following){
        if(closed)return;explicitViewport=true;viewport.clear();viewport.addAll(visible);ahead.clear();
        for(String pkg:following){ahead.add(pkg);if(ahead.size()>=MAX_AHEAD)break;}schedule();
    }
    private LinkedHashSet<String> wanted(){
        LinkedHashSet<String> wanted=new LinkedHashSet<>();
        // GridView can reuse a child with a temporarily cleared attach flag. Its live parent/root
        // and viewport, not a stale child callback, determine whether it needs the cached pixels.
        for(Map.Entry<ImageView,String> entry:bound.entrySet()){ImageView image=entry.getKey();if(image.getParent()!=null&&image.getRootView().isAttachedToWindow()&&image.isShown()){String pkg=entry.getValue();Bitmap cached=cache.get(pkg);if(cached!=null&&(!(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)||((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap()!=cached))image.setImageBitmap(cached);wanted.add(pkg);}}
        return wanted;
    }
    private void pump(){
        if(closed)return;Set<String> boundVisible=wanted();if(paused)return;Set<String> visible=explicitViewport?new LinkedHashSet<>(viewport):boundVisible,wanted=new LinkedHashSet<>(visible);wanted.addAll(ahead);
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
    void release(ImageView view){bound.remove(view);schedule();}
    @Override public void close(){closed=true;ui.removeCallbacksAndMessages(null);for(String pkg:new ArrayList<>(active.keySet()))cancel(pkg);for(ImageView view:new ArrayList<>(observed))view.removeOnAttachStateChangeListener(attachment);observed.clear();bound.clear();viewport.clear();ahead.clear();failures.clear();retryAt.clear();workers.shutdownNow();cache.evictAll();}
}
