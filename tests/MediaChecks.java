package ru.local.qcatalog;

import android.content.Context;
import android.media.MediaPlayer;
import android.graphics.Bitmap;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Metadata identity, release versions, real image/video and no-media-on-disk checks. */
public final class MediaChecks {
    static int checks;
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Set<String> files(File root)throws Exception{Set<String> result=new TreeSet<>();if(root!=null&&root.exists())try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(root.toPath())){paths.filter(java.nio.file.Files::isRegularFile).forEach(p->result.add(p.toString()));}return result;}
    public static int run(Context c,boolean live)throws Exception {
        checks=0;
        require(new Catalog.Game("A","A v1716+1.40.8_7379 -Uploader","org.test.a",1716,"",1).versionLabel().equals("v1.40.8_7379 · код 1716"),"Marketing version and code come from this release");
        require(new Catalog.Game("A","A v2.1.3","org.test.a",99,"",1).versionLabel().equals("v2.1.3 · код 99"),"Unsplit release version remains visible");
        require(new Catalog.Game("A","No version","org.test.a",99,"",1).versionLabel().equals("Версия: код 99"),"Missing marketing version is not invented");
        MediaInfo info=MediaInfo.provider(new JSONObject("{\"id\":\"123456\",\"cover_url\":\"https://cdn.oculuscdn.com/old.jpg\"}"));
        String graph="{\"@graph\":[{\"@id\":\"https://www.meta.com/#/schema/SoftwareApplication/123456\",\"image\":[{\"@id\":\"shot\"},{\"@id\":\"shot\"}]},{\"@id\":\"shot\",\"contentUrl\":\"https://cdn.oculuscdn.com/new.webp\"},{\"@id\":\"https://www.meta.com/#/schema/SoftwareApplication/999999\",\"image\":[\"https://cdn.oculuscdn.com/wrong.jpg\"]},{\"@id\":\"trailer\",\"@type\":\"VideoObject\",\"about\":{\"@id\":\"https://www.meta.com/#/schema/SoftwareApplication/123456\"},\"contentUrl\":\"https://cdn.oculuscdn.com/video.mp4\"}]}";
        info.metaPage("<script type='application/ld+json'>"+graph+"</script>");require(info.images.size()==1&&info.images.get(0).endsWith("new.webp"),"Fresh gallery replaces expired links, deduplicates and excludes other games");require(info.trailer.endsWith("video.mp4"),"Only linked game's video is accepted");
        for(String unsafe:Arrays.asList("http://cdn.oculuscdn.com/a","https://oculuscdn.com.evil.invalid/a","file:///sdcard/a","https://u:p@cdn.oculuscdn.com/a","https://127.0.0.1/a"))require(MediaInfo.https(unsafe).isEmpty(),"Unsafe media reference rejected");
        if(live){Set<String> before=files(c.getFilesDir());before.addAll(files(c.getCacheDir()));try(GameDetails session=new GameDetails(c)) {
            MediaInfo real=session.media(new Catalog.Game("Beat Saber","Media fixture","com.beatgames.beatsaber",1,"",1),"com.beatgames.beatsaber");
            require(real.images.size()>1,"Real QLoader-linked store page provides screenshots");require(!real.trailer.isEmpty(),"Real card provides video");
            CountDownLatch photosReady=new CountDownLatch(real.images.size());CardImages photos=new CardImages(c,"com.beatgames.beatsaber",(key,bitmap)->photosReady.countDown());
            try{photos.prefetch(real.images);require(photosReady.await(60,TimeUnit.SECONDS),"Real gallery preloads before navigation");Bitmap bitmap=photos.get(real.images.get(1));require(bitmap!=null&&bitmap.getWidth()>0,"Actual screenshot is ready in RAM before selection");require(photos.size()>1&&photos.bytes()<=photos.limit,"Real gallery keeps multiple images inside its RAM budget");require(photos.get(real.images.get(1))==bitmap,"Real screenshot reuse returns the same decoded pixels");}finally{photos.close();}require(photos.size()==0&&photos.bytes()==0,"Closing real gallery clears image memory");
            if(c.getSharedPreferences("state",0).getString("mirror","").matches("FFA-\\d+"))require(real.trailer.startsWith("ffa:")&&real.trailerSize>0,"Configured QLoader mirror supplies the actual video source");
            RamVideo source=new RamVideo(c,real.trailer,real.trailerSize);MediaPlayer player=new MediaPlayer();CountDownLatch ready=new CountDownLatch(1),seek=new CountDownLatch(1);final boolean[] failed={false};player.setOnPreparedListener(p->ready.countDown());player.setOnErrorListener((p,w,e)->{failed[0]=true;ready.countDown();return true;});player.setOnSeekCompleteListener(p->seek.countDown());
            try{player.setDataSource(source);player.prepareAsync();require(ready.await(45,TimeUnit.SECONDS)&&!failed[0],"Native player prepares real video from RAM");require(player.getDuration()>1000&&player.getVideoWidth()>0,"Video has valid duration and dimensions");player.start();Thread.sleep(1200);player.pause();require(player.getCurrentPosition()>0&&!player.isPlaying(),"Real video plays and pauses");int halfway=player.getDuration()/2;player.seekTo(halfway);require(seek.await(15,TimeUnit.SECONDS),"RAM-backed video supports seeking");}
            finally{source.close();player.release();}
            boolean closed=false;try{source.readAt(0,new byte[4],0,4);}catch(IOException expected){closed=true;}require(closed,"Closing video stops reads and releases buffer");
        }Set<String> after=files(c.getFilesDir());after.addAll(files(c.getCacheDir()));require(before.equals(after),"Live descriptions/images/video create no files in application storage or cache");}
        return checks;
    }
}
