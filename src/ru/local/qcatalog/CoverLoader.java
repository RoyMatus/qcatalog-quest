package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.util.*;
import java.util.concurrent.*;

/** Only attached, visible cells request covers. Bounded RAM cache, never a disk cache. */
final class CoverLoader implements AutoCloseable {
    private final Context context;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private final Map<ImageView,Request> active=new IdentityHashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(16*1024*1024) {
        protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    private boolean closed;
    private static final class Request { String pkg; GameDetails session; Future<?> future; Request(String p){pkg=p;} }
    CoverLoader(Context c){context=c.getApplicationContext();}
    void bind(ImageView view,String pkg) {
        Request old=active.get(view); if(old!=null && old.pkg.equals(pkg)) return;
        release(view); view.setImageResource(R.drawable.qc_cover_placeholder);
        Bitmap bitmap=cache.get(pkg); if(bitmap!=null){view.setImageBitmap(bitmap);return;}
        Request request=new Request(pkg); active.put(view,request);
        view.post(() -> {
            if(closed || active.get(view)!=request || !view.isAttachedToWindow() || !view.isShown()) { if(active.get(view)==request) release(view); return; }
            request.session=new GameDetails(context);
            request.future=workers.submit(() -> {
                try { Bitmap cover=request.session.cover(pkg); if(cover!=null && !request.session.closed) {
                    cache.put(pkg,cover); view.post(() -> { if(!closed && active.get(view)==request) view.setImageBitmap(cover); });
                } }catch(Exception ignored){}finally { request.session.close(); }
            });
        });
    }
    void release(ImageView view) { Request r=active.remove(view); if(r!=null){if(r.session!=null)r.session.close();if(r.future!=null)r.future.cancel(true);} }
    @Override public void close(){closed=true;for(ImageView view:new ArrayList<>(active.keySet()))release(view);workers.shutdownNow();cache.evictAll();}
}
