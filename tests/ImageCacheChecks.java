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
            require(cache.size()==CardImages.MAX_IMAGES&&cache.pending()==0,"All card images are resident together");require(loads.get()==CardImages.MAX_IMAGES,"Each image loads exactly once");require(peak.get()==1,"Photos download and decode sequentially");require(cache.bytes()>0&&cache.bytes()<=cache.limit,"Decoded RAM remains within card memory budget");
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
        GameDetails session=new GameDetails(context);CountDownLatch closing=new CountDownLatch(1),finishClose=new CountDownLatch(1);
        java.net.HttpURLConnection socket=new java.net.HttpURLConnection(new java.net.URL("https://example.invalid/")){
            public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closing.countDown();try{finishClose.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}
        };
        java.lang.reflect.Field requestField=GameDetails.class.getDeclaredField("requests");requestField.setAccessible(true);
        @SuppressWarnings("unchecked") Set<java.net.HttpURLConnection> requests=(Set<java.net.HttpURLConnection>)requestField.get(session);requests.add(socket);
        long[] elapsed={0};try{instrumentation.runOnMainSync(()->{long start=android.os.SystemClock.elapsedRealtime();session.close();elapsed[0]=android.os.SystemClock.elapsedRealtime()-start;});require(closing.await(2,TimeUnit.SECONDS),"Socket cleanup starts independently");require(session.closed&&elapsed[0]<500,"Closing a slow connection does not block the UI thread");require(requests.isEmpty(),"Connection ownership is detached before blocking teardown");}finally{finishClose.countDown();}
        return checks;
    }
    static int covers(android.app.Activity activity,Instrumentation instrumentation)throws Exception{
        checks=0;AtomicInteger opens=new AtomicInteger();CountDownLatch firstStarted=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),secondReady=new CountDownLatch(1);AtomicReference<Bitmap> late=new AtomicReference<>(),expected=new AtomicReference<>();
        CoverLoader loader=new CoverLoader(()->{int attempt=opens.incrementAndGet();return new CardImages.Source(){public Bitmap load(String key,int pixels)throws Exception{if(attempt==1){firstStarted.countDown();try{releaseFirst.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}Bitmap result=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);late.set(result);return result;}Bitmap result=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);expected.set(result);secondReady.countDown();return result;}public void close(){if(attempt==1)releaseFirst.countDown();}};});
        android.widget.LinearLayout[] host={null};android.widget.ImageView[] image={null};android.app.Dialog[] dialog={null};
        try{
            instrumentation.runOnMainSync(()->{host[0]=new android.widget.LinearLayout(activity);image[0]=new android.widget.ImageView(activity);loader.bind(image[0],"org.test.cover");require(opens.get()==0,"Binding before attachment does not start an invisible request");host[0].addView(image[0],new android.widget.LinearLayout.LayoutParams(100,100));dialog[0]=new android.app.AlertDialog.Builder(activity).setView(host[0]).create();dialog[0].show();});
            require(firstStarted.await(5,TimeUnit.SECONDS),"Attaching a prebound grid cover starts its load");
            instrumentation.runOnMainSync(()->host[0].removeView(image[0]));instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->host[0].addView(image[0],new android.widget.LinearLayout.LayoutParams(100,100)));
            require(secondReady.await(5,TimeUnit.SECONDS),"Reattaching a recycled cover restarts its cancelled request");
            long deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline&&(late.get()==null||!late.get().isRecycled())){Thread.sleep(20);instrumentation.waitForIdleSync();}
            instrumentation.runOnMainSync(()->{require(image[0].getDrawable() instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)image[0].getDrawable()).getBitmap()==expected.get(),"Late cancelled cover cannot overwrite the currently bound bitmap");require(late.get()!=null&&late.get().isRecycled(),"Cancelled cover pixels are released");loader.release(image[0]);host[0].removeView(image[0]);host[0].addView(image[0]);require(opens.get()==2,"Explicit release removes the binding and prevents reattachment requests");});
            instrumentation.runOnMainSync(()->{
                loader.pause(true);loader.bind(image[0],"org.test.cover");
                Bitmap card=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888);card.eraseColor(android.graphics.Color.BLUE);
                loader.accept("org.test.cover",card);Bitmap thumbnail=loader.peek("org.test.cover");
                require(thumbnail!=null&&thumbnail!=card,"Even a small card cover is copied into separately owned list memory");card.recycle();
                require(!thumbnail.isRecycled()&&((android.graphics.drawable.BitmapDrawable)image[0].getDrawable()).getBitmap()==thumbnail,"The visible tile receives the card cover while grid downloads are paused");
                loader.pause(false);require(opens.get()==2,"Returning from details reuses the transferred thumbnail without downloading again");
            });
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
            instrumentation.runOnMainSync(()->{loader.pause(true);loader.prefetch(Collections.singletonList("resume"));require(opens.get()==CoverLoader.WORKERS+1,"Opening details pauses background grid requests");require(loader.peek("next")!=null,"The card can show the existing thumbnail immediately");loader.pause(false);});
            long deadline=System.currentTimeMillis()+3000;while(!loaded.contains("resume")&&System.currentTimeMillis()<deadline)Thread.sleep(20);require(loaded.contains("resume"),"Closing details resumes the small cover window");
        }finally{instrumentation.runOnMainSync(()->{loader.close();if(dialog[0]!=null)dialog[0].dismiss();});}
        return checks;
    }
    static int gridRebinding(android.app.Activity activity,Instrumentation test)throws Exception{
        checks=0;Map<String,AtomicInteger> fetched=new ConcurrentHashMap<>();AtomicInteger running=new AtomicInteger(),peak=new AtomicInteger();
        CoverLoader loader=new CoverLoader(()->new CardImages.Source(){
            public Bitmap load(String key,int pixels)throws Exception{fetched.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();peak.accumulateAndGet(running.incrementAndGet(),Math::max);try{Thread.sleep(350);return Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);}finally{running.decrementAndGet();}}
            public void close(){}
        });
        android.widget.GridView[] grid={null};android.app.Dialog[] dialog={null};android.widget.BaseAdapter[] adapter={null};
        try{
            test.runOnMainSync(()->{
                grid[0]=new android.widget.GridView(activity);grid[0].setNumColumns(4);
                adapter[0]=new android.widget.BaseAdapter(){
                    public int getCount(){return 100;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}
                    public android.view.View getView(int p,android.view.View recycled,android.view.ViewGroup parent){android.widget.ImageView image=recycled==null?new android.widget.ImageView(activity):(android.widget.ImageView)recycled;image.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,Cards.dp(activity,150)));loader.bind(image,"grid-"+p);return image;}
                };
                grid[0].setAdapter(adapter[0]);grid[0].setOnScrollListener(new android.widget.AbsListView.OnScrollListener(){
                    public void onScrollStateChanged(android.widget.AbsListView view,int state){}
                    public void onScroll(android.widget.AbsListView view,int first,int count,int total){List<String> visible=new ArrayList<>(),ahead=new ArrayList<>();for(int i=first;i<first+count;i++)visible.add("grid-"+i);for(int i=first+count;i<Math.min(total,first+count+8);i++)ahead.add("grid-"+i);loader.viewport(visible,ahead);}
                });
                dialog[0]=new android.app.AlertDialog.Builder(activity).setView(grid[0]).create();dialog[0].show();dialog[0].getWindow().setLayout(Cards.dp(activity,800),Cards.dp(activity,550));
            });
            for(int i=0;i<24;i++){Thread.sleep(120);test.runOnMainSync(()->adapter[0].notifyDataSetChanged());}
            Thread.sleep(300);test.waitForIdleSync();
            test.runOnMainSync(()->{
                int first=grid[0].getFirstVisiblePosition(),count=grid[0].getChildCount();require(count>0,"Real GridView has visible tiles");
                for(int i=0;i<count;i++)require(((android.widget.ImageView)grid[0].getChildAt(i)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Every visible tile loads despite repeated GridView relayouts");
                for(int i=first;i<first+count+8;i++){String key="grid-"+i;require(loader.peek(key)!=null,"Visible covers and exactly two following rows finish");require(fetched.get(key).get()==1,"Relayout does not restart an in-flight cover");}
                require(fetched.keySet().stream().noneMatch(k->Integer.parseInt(k.substring(5))>=first+count+8),"No covers beyond the two-row window are fetched");require(peak.get()<=CoverLoader.WORKERS,"Real grid retains bounded network concurrency");
                grid[0].setSelection(40);
            });
            Thread.sleep(2700);test.waitForIdleSync();
            test.runOnMainSync(()->{int first=grid[0].getFirstVisiblePosition();require(first>=40,"Real grid scrolled to another window");for(int i=0;i<grid[0].getChildCount();i++){android.widget.ImageView view=(android.widget.ImageView)grid[0].getChildAt(i);require(view.getDrawable() instanceof android.graphics.drawable.BitmapDrawable,"Recycled tile "+(first+i)+" loads after scrolling; cached="+(loader.peek("grid-"+(first+i))!=null)+"; fetched="+fetched.keySet()+"; attached="+view.isAttachedToWindow()+"; shown="+view.isShown());}});
        }finally{test.runOnMainSync(()->{loader.close();if(dialog[0]!=null)dialog[0].dismiss();});}
        return checks;
    }
    static int carousel(android.app.Activity activity,Instrumentation test)throws Exception{
        checks=0;List<String> order=new CopyOnWriteArrayList<>();CountDownLatch first=new CountDownLatch(1),release=new CountDownLatch(1),complete=new CountDownLatch(4);
        CardImages queue=new CardImages(()->new CardImages.Source(){public Bitmap load(String key,int budget)throws Exception{order.add(key);if(key.equals("0")){first.countDown();release.await(5,TimeUnit.SECONDS);}return Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);}public void close(){}},(k,b)->complete.countDown());
        try{queue.prefetch(Arrays.asList("0","1","2","3"));require(first.await(2,TimeUnit.SECONDS),"First sequential image started");queue.prioritize("3");release.countDown();require(complete.await(5,TimeUnit.SECONDS),"Prioritized image queue completes");require(order.equals(Arrays.asList("0","3","1","2")),"Next requested image moves ahead of unstarted background photos without cancelling the active image");}finally{release.countDown();queue.close();}
        CountDownLatch second=new CountDownLatch(1),allowSecond=new CountDownLatch(1),last=new CountDownLatch(1),allowLast=new CountDownLatch(1);MediaCarousel[] gallery={null};android.app.Dialog[] dialog={null};List<String> fetched=new CopyOnWriteArrayList<>();
        CoverLoader tiles=new CoverLoader(()->{throw new AssertionError("Transferred cover must not start a new request");});AtomicReference<Bitmap> fullCover=new AtomicReference<>();
        try{
            test.runOnMainSync(()->{gallery[0]=new MediaCarousel(activity,"fixture",()->new CardImages.Source(){public Bitmap load(String key,int budget)throws Exception{fetched.add(key);if(key.equals("second")){second.countDown();allowSecond.await(5,TimeUnit.SECONDS);}if(key.equals("last")){last.countDown();allowLast.await(5,TimeUnit.SECONDS);}Bitmap b=Bitmap.createBitmap(key.isEmpty()?640:320,key.isEmpty()?360:180,Bitmap.Config.ARGB_8888);b.eraseColor(key.equals("second")?android.graphics.Color.RED:key.equals("last")?android.graphics.Color.GREEN:android.graphics.Color.BLUE);return b;}public void close(){}});MediaInfo info=new MediaInfo();info.cover="https://cdn.oculuscdn.com/cover.jpg?token=one";info.images.add("https://cdn.oculuscdn.com/cover.jpg?token=two");info.images.addAll(Arrays.asList("duplicate","second","last"));gallery[0].configure(info);dialog[0]=new android.app.AlertDialog.Builder(activity).setView(gallery[0]).create();gallery[0].setHostDialog(dialog[0]);dialog[0].show();});
            require(second.await(3,TimeUnit.SECONDS),"Cover and duplicate finish before the second distinct photo");
            test.runOnMainSync(()->{gallery[0].setCoverReady(cover->{fullCover.set(cover);tiles.accept("fixture",cover);});require(tiles.peek("fixture")!=null,"Registering the listener also transfers an already loaded card cover");require((long)tiles.peek("fixture").getWidth()*tiles.peek("fixture").getHeight()<=CoverLoader.PIXELS,"Only a small thumbnail returns to the list cache");});
            test.runOnMainSync(()->{require(!gallery[0].next.isEnabled(),"Duplicate cover does not enable browsing as a second image");require(gallery[0].loading.getVisibility()==android.view.View.VISIBLE,"Loading stays visible until a distinct next photo is ready");});
            allowSecond.countDown();require(last.await(3,TimeUnit.SECONDS),"Later photos continue sequentially in the background");
            test.runOnMainSync(()->{require(gallery[0].images.pending()>0&&gallery[0].next.isEnabled(),"Two distinct cached photos enable browsing without waiting for the last photo");require(gallery[0].loading.getVisibility()==android.view.View.GONE,"Loading overlay disappears as soon as browsing is possible");gallery[0].next.performClick();Bitmap displayed=((android.graphics.drawable.BitmapDrawable)gallery[0].image.getDrawable()).getBitmap();require(displayed.getPixel(0,0)==android.graphics.Color.RED,"Second displayed photo is different from the blue cover");require(!gallery[0].next.isEnabled(),"An unloaded following photo cannot show a blank frame");gallery[0].previous.performClick();require(((android.graphics.drawable.BitmapDrawable)gallery[0].image.getDrawable()).getBitmap().getPixel(0,0)==android.graphics.Color.BLUE,"Previous returns to the original cover");});
            require(!fetched.contains("https://cdn.oculuscdn.com/cover.jpg?token=two"),"Cover URL with another signature is not downloaded as a screenshot");
        }finally{allowSecond.countDown();allowLast.countDown();test.runOnMainSync(()->{if(gallery[0]!=null)gallery[0].close();if(dialog[0]!=null)dialog[0].dismiss();});}
        test.runOnMainSync(()->{require(fullCover.get()!=null&&fullCover.get().isRecycled(),"Closing the card still releases the full cover");require(tiles.peek("fixture")!=null&&!tiles.peek("fixture").isRecycled()&&tiles.peek("fixture").getPixel(0,0)==android.graphics.Color.BLUE,"Thumbnail survives card closure and is not replaced by screenshots");tiles.close();});
        return checks;
    }
}
