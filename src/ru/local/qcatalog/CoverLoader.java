package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.util.*;
import java.util.concurrent.*;

/** Attached cells load fresh covers into bounded RAM; detach cancels and reattach restarts. */
final class CoverLoader implements AutoCloseable {
    private final CardImages.Factory factory;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Map<ImageView,Request> active=new IdentityHashMap<>();
    private final Map<ImageView,String> bound=new WeakHashMap<>();
    private final Set<ImageView> observed=Collections.newSetFromMap(new WeakHashMap<ImageView,Boolean>());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private boolean closed;
    private static final class Request {final String pkg;final CardImages.Source source;Future<?> future;Request(String pkg,CardImages.Source source){this.pkg=pkg;this.source=source;}}
    private final View.OnAttachStateChangeListener attachment=new View.OnAttachStateChangeListener(){
        public void onViewAttachedToWindow(View view){ImageView image=(ImageView)view;String pkg=bound.get(image);if(pkg!=null)load(image,pkg,0);}
        public void onViewDetachedFromWindow(View view){cancel((ImageView)view);}
    };
    CoverLoader(Context c){this(()->new CardImages.Source(){final GameDetails session=new GameDetails(c);public Bitmap load(String pkg,int pixels)throws Exception{return session.cover(pkg,pixels);}public void close(){session.close();}});}
    CoverLoader(CardImages.Factory factory){this.factory=factory;}
    void bind(ImageView view,String pkg){
        if(closed)return;String previous=bound.put(view,pkg);if(observed.add(view))view.addOnAttachStateChangeListener(attachment);
        if(!pkg.equals(previous)){cancel(view);view.setImageResource(R.drawable.qc_cover_placeholder);}
        // GridView may bind before attaching. The attachment listener owns starting that request.
        load(view,pkg,0);
    }
    private void load(ImageView view,String pkg,int attempt){
        if(closed||!pkg.equals(bound.get(view))||!view.isAttachedToWindow()||!view.isShown())return;
        Bitmap cached=cache.get(pkg);if(cached!=null){view.setImageBitmap(cached);return;}
        if(active.containsKey(view))return;
        Request request=new Request(pkg,factory.open());active.put(view,request);
        request.future=workers.submit(()->{
            Bitmap loaded=null;try{loaded=request.source.load(pkg,1000*1000);}catch(Exception|OutOfMemoryError ignored){}finally{request.source.close();}
            final Bitmap result=loaded;view.post(()->{
                if(closed||active.get(view)!=request||!pkg.equals(bound.get(view))){if(result!=null)result.recycle();return;}
                active.remove(view);
                if(result!=null){cache.put(pkg,result);view.setImageBitmap(result);}
                else if(attempt<2)view.postDelayed(()->load(view,pkg,attempt+1),3000);
            });
        });
    }
    private void cancel(ImageView view){Request r=active.remove(view);if(r!=null){r.source.close();if(r.future!=null)r.future.cancel(true);}}
    void release(ImageView view){bound.remove(view);cancel(view);}
    @Override public void close(){closed=true;for(ImageView view:new ArrayList<>(active.keySet()))cancel(view);for(ImageView view:new ArrayList<>(observed))view.removeOnAttachStateChangeListener(attachment);observed.clear();bound.clear();workers.shutdownNow();cache.evictAll();}
}
