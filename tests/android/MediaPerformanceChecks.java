package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Opt-in measurements in the real app. No downloads, installs, or configuration changes. */
final class MediaPerformanceChecks {
    final Instrumentation test; final Context context; MainActivity activity;
    MediaPerformanceChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    static void call(Object o,String name,Class<?> type,Object arg){try{Method m=o.getClass().getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(o,arg);}catch(Exception e){throw new RuntimeException(e);}}
    void report(String message){Bundle b=new Bundle();b.putString("measurement",message);test.sendStatus(0,b);}
    int[] counts(View view){int loaded=0,total=0;if(view instanceof ImageView&&view.isShown()){total++;if(((ImageView)view).getDrawable() instanceof BitmapDrawable)loaded++;}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){int[] n=counts(g.getChildAt(i));loaded+=n[0];total+=n[1];}}return new int[]{loaded,total};}
    void window(String name)throws Exception {
        long start=SystemClock.elapsedRealtime(),first=-1,most=-1;int[] latest={0,0};
        for(int i=0;i<120;i++){final int[][] n={null};test.runOnMainSync(()->n[0]=counts((GridView)field(activity,"grid")));latest=n[0];long elapsed=SystemClock.elapsedRealtime()-start;if(latest[0]>0&&first<0)first=elapsed;if(latest[1]>0&&latest[0]>=Math.ceil(latest[1]*.9)&&most<0)most=elapsed;if(latest[1]>0&&latest[0]==latest[1])break;Thread.sleep(250);}
        report(name+": first="+first+"ms; 90%="+most+"ms; elapsed="+(SystemClock.elapsedRealtime()-start)+"ms; covers="+latest[0]+"/"+latest[1]);
        test.runOnMainSync(()->{CoverLoader loader=(CoverLoader)field(activity,"covers");@SuppressWarnings("unchecked") Map<ImageView,String> bound=(Map<ImageView,String>)field(loader,"bound");List<String> missing=new ArrayList<>();for(Map.Entry<ImageView,String> e:bound.entrySet())if(e.getKey().isShown()&&e.getKey().isAttachedToWindow()&&!(e.getKey().getDrawable() instanceof BitmapDrawable))missing.add(e.getValue());report("missing visible covers="+missing);});
    }
    @SuppressWarnings("unchecked") void run(String gameName)throws Exception {
        if(gameName.startsWith("sources:")){sources(gameName.substring(8));return;}
        boolean cardOnly=gameName.startsWith("card:");if(cardOnly)gameName=gameName.substring(5);
        activity=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.waitForIdleSync();if(!cardOnly){window("catalog cold");
        test.runOnMainSync(()->{GridView g=(GridView)field(activity,"grid");g.smoothScrollToPosition(g.getLastVisiblePosition()+6);});Thread.sleep(600);window("catalog scroll");
        test.runOnMainSync(()->call(activity,"selectSection",int.class,1));Thread.sleep(300);window("library cold");
        test.runOnMainSync(()->{GridView g=(GridView)field(activity,"grid");g.smoothScrollToPosition(g.getAdapter().getCount()-1);});Thread.sleep(600);window("library scroll");}
        List<Catalog.Game> games=new Engine(context).cached();Catalog.Game selected=null;
        for(Catalog.Game game:games)if((game.name.equalsIgnoreCase(gameName)||game.pkg.equals(gameName))){selected=game;break;}
        if(selected==null)throw new IllegalArgumentException("Game not found");
        final Catalog.Game chosen=selected;long start=SystemClock.elapsedRealtime();
        test.runOnMainSync(()->call(activity,"gameDetails",Catalog.Game.class,chosen));test.waitForIdleSync();
        final MediaCarousel[] found={null};test.runOnMainSync(()->found[0]=((Collection<MediaCarousel>)field(activity,"openCarousels")).iterator().next());MediaCarousel gallery=found[0];
        report("card window="+(SystemClock.elapsedRealtime()-start)+"ms");
        if(cardOnly){
            boolean browse=false;for(int i=0;i<180;i++){final boolean[] r={false};test.runOnMainSync(()->r[0]=gallery.next.isEnabled());if(r[0]){browse=true;break;}Thread.sleep(100);}
            report("first browsing="+(SystemClock.elapsedRealtime()-start)+"ms; cached="+gallery.images.size()+"; pending="+gallery.images.pending());
            if(!browse)throw new AssertionError("Card never enabled browsing");
            test.runOnMainSync(()->{String first=((List<String>)field(gallery,"urls")).get((Integer)field(gallery,"position"));gallery.next.performClick();String second=((List<String>)field(gallery,"urls")).get((Integer)field(gallery,"position"));if(first.equals(second)||gallery.images.same(first,second))throw new AssertionError("Second frame duplicates first");report("second frame is distinct; loading overlay hidden="+(gallery.loading.getVisibility()==View.GONE));gallery.previous.performClick();});
        }
        boolean ready=false;for(int i=0;i<180;i++){final boolean[] r={false};test.runOnMainSync(()->r[0]=(Boolean)field(gallery,"configured")&&gallery.images.pending()==0);if(r[0]){ready=true;break;}Thread.sleep(250);}
        final int[] imageCount={0};test.runOnMainSync(()->imageCount[0]=((List<?>)field(gallery,"urls")).size());
        report("card media="+(SystemClock.elapsedRealtime()-start)+"ms; finished="+ready+"; slides="+imageCount[0]+"; cached photos="+gallery.images.size());
        final String[] videoUrl={""};test.runOnMainSync(()->videoUrl[0]=(String)field(gallery.video,"url"));
        if(!videoUrl[0].isEmpty()){
            test.runOnMainSync(()->{call(gallery,"select",int.class,((List<?>)field(gallery,"urls")).size()-1);gallery.video.toggle();});
            long playStart=SystemClock.elapsedRealtime();boolean prepared=false;String status="";
            for(int i=0;i<180;i++){final boolean[] p={false};final String[] s={""};test.runOnMainSync(()->{p[0]=(Boolean)field(gallery.video,"prepared");s[0]=gallery.video.status.getText().toString();});status=s[0];if(p[0]){prepared=true;break;}if(status.contains("Не удалось"))break;Thread.sleep(250);}
            report("video="+(videoUrl[0].startsWith("ffa:")?"mirror":"public")+"; prepared="+prepared+"; elapsed="+(SystemClock.elapsedRealtime()-playStart)+"ms; status="+status);
            if(!prepared)throw new AssertionError("Video did not prepare");
            Thread.sleep(2000);final int[] position={0};test.runOnMainSync(()->position[0]=((android.media.MediaPlayer)field(gallery.video,"player")).getCurrentPosition());report("video playback position="+position[0]+"ms");if(position[0]<500)throw new AssertionError("Video did not play");
        }else report("video missing");
        test.runOnMainSync(()->((Dialog)field(gallery,"hostDialog")).dismiss());
        test.runOnMainSync(()->activity.finish());
        // Compare direct QLoader thumbnail independently of the current public fallback chain.
        String mirror=context.getSharedPreferences("state",0).getString("mirror","");String pkg=GameDetails.originalPackage(chosen.pkg);
        if(mirror.matches("FFA-\\d+")){Engine engine=new Engine(context);long at=SystemClock.elapsedRealtime();try{byte[] b=engine.runBytes(Arrays.asList("cat",mirror+":Quest Games/.meta/thumbnails/"+pkg+".jpg"),null,8*1024*1024);Bitmap bitmap=BitmapFactory.decodeByteArray(b,0,b.length);report("direct mirror thumbnail="+(SystemClock.elapsedRealtime()-at)+"ms; bytes="+b.length+"; decoded="+(bitmap!=null));if(bitmap!=null)bitmap.recycle();}catch(Exception e){report("direct mirror thumbnail failed after "+(SystemClock.elapsedRealtime()-at)+"ms; type="+e.getClass().getSimpleName());}finally{engine.cancel();}}
    }
    void sources(String pkg)throws Exception {
        Catalog.Game chosen=null;for(Catalog.Game game:new Engine(context).cached())if(game.pkg.equals(pkg)){chosen=game;break;}if(chosen==null)throw new IllegalArgumentException("Game not found");
        try(GameDetails session=new GameDetails(context)){
            long at=SystemClock.elapsedRealtime();MediaInfo info=session.media(chosen,pkg);report("metadata "+pkg+": "+(SystemClock.elapsedRealtime()-at)+"ms; images="+info.images.size()+"; video="+(info.trailer.startsWith("ffa:")?"mirror":"public"));
            if(!info.trailer.isEmpty())probeVideo(info.trailer,info.trailerSize);
        }
        String mirror=context.getSharedPreferences("state",0).getString("mirror","");
        if(mirror.matches("FFA-\\d+"))probeVideo("ffa:"+mirror+":Quest Games/.meta/videos/"+pkg+".mp4",-1);
    }
    void probeVideo(String url,long expected)throws Exception{
        boolean mirror=url.startsWith("ffa:");long at=SystemClock.elapsedRealtime();
        if(!mirror){java.net.HttpURLConnection h=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();h.setUseCaches(false);h.setInstanceFollowRedirects(false);h.setConnectTimeout(10000);h.setReadTimeout(15000);try{report("video HTTP="+h.getResponseCode()+"; bytes="+h.getContentLengthLong()+"; type="+h.getContentType());}catch(Exception e){report("HTTP exception="+e.getClass().getSimpleName());}finally{h.disconnect();}}
        try(RamVideo data=new RamVideo(context,url,expected)){
            try{long size=data.getSize();byte[] sample=new byte[32];int n=data.readAt(0,sample,0,32);report((mirror?"mirror":"public")+" video first bytes="+n+"; size="+size+"; elapsed="+(SystemClock.elapsedRealtime()-at)+"ms");
                if(size>32)report("video tail bytes="+data.readAt(size-32,sample,0,32)+"; elapsed="+(SystemClock.elapsedRealtime()-at)+"ms");
            }catch(Exception e){report((mirror?"mirror":"public")+" video failure="+e.getClass().getSimpleName()+"; elapsed="+(SystemClock.elapsedRealtime()-at)+"ms; received="+field(data,"received")+"; size="+field(data,"size"));Object error=field(data,"failure");if(error!=null)report("buffer failure="+((Throwable)error).getClass().getSimpleName()+": "+((Throwable)error).getMessage());}
        }
    }
}
