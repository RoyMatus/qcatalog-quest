package ru.local.qcatalog;

import android.os.Looper;
import java.util.concurrent.*;

/** Network teardown must not stall UI input while a socket is still reading. */
final class MediaCleanup {
    private static final ExecutorService workers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"qcatalog-media-close");t.setDaemon(true);return t;});
    static void run(Runnable cleanup){if(Looper.myLooper()==Looper.getMainLooper())workers.execute(cleanup);else cleanup.run();}
}
