package ru.local.qcatalog;

import android.content.Context;
import android.media.MediaDataSource;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.LongConsumer;

/** Progressive MP4 source with seeking inside a bounded RAM buffer. Never creates a media file. */
final class RamVideo extends MediaDataSource {
    static final int LIMIT=64*1024*1024,CHUNK=256*1024;
    interface Transfer extends AutoCloseable {void read(OutputStream sink,LongConsumer length)throws Exception;void close();}
    private final List<byte[]> chunks=new ArrayList<>();
    private int received;private long size=-1;private boolean closed,finished;private IOException failure;
    private final Transfer primary,fallback;
    RamVideo(Context c,String source,long expected){this(c,source,expected,"");}
    RamVideo(Context c,String source,long expected,String backup){this(expected,new Remote(c,source),backup.isEmpty()?null:new Remote(c,backup));}
    RamVideo(long expected,Transfer primary,Transfer fallback) {
        this.primary=primary;this.fallback=fallback;if(expected>0)size=expected;
        Thread worker=new Thread(()->{
            try {
                OutputStream sink=new OutputStream(){public void write(int b)throws IOException{write(new byte[]{(byte)b});}public void write(byte[] b,int off,int n)throws IOException{append(b,off,n);}};
                try{if(expected>LIMIT)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");primary.read(sink,this::setSize);synchronized(this){if(received==0)throw new IOException("Источник вернул пустое видео.");}}
                catch(Exception first){synchronized(this){if(closed||received>0||fallback==null)throw first;size=-1;}primary.close();fallback.read(sink,this::setSize);}
                synchronized(this){if(!closed){if(size>0&&received!=size)throw new IOException("Видео загружено не полностью.");if(received==0)throw new IOException("Источник вернул пустое видео.");size=received;finished=true;notifyAll();}}
            }catch(Exception e){synchronized(this){if(!closed)failure=e instanceof IOException?(IOException)e:new IOException("Не удалось прочитать видео.",e);finished=true;notifyAll();}}
            finally{primary.close();if(fallback!=null)fallback.close();}
        },"qcatalog-video-ram");worker.setDaemon(true);worker.start();
    }
    private static final class Remote implements Transfer {
        final Context context;final String source;volatile boolean closed;volatile Engine engine;volatile HttpURLConnection http;
        Remote(Context context,String source){this.context=context;this.source=source;}
        public void read(OutputStream sink,LongConsumer length)throws Exception {
            if(closed)throw new InterruptedIOException();
            if(source.startsWith("ffa:")){engine=new Engine(context);if(closed){engine.cancel();throw new InterruptedIOException();}engine.mediaTo(Arrays.asList("cat",source.substring(4)),sink,LIMIT);return;}
            String address=source;
            for(int redirects=0;redirects<=3;redirects++){
                address=MediaInfo.https(address);if(address.isEmpty())throw new IOException("Недопустимая ссылка видео.");
                HttpURLConnection h=(HttpURLConnection)new URL(address).openConnection();http=h;
                h.setUseCaches(false);h.setConnectTimeout(10000);h.setReadTimeout(15000);h.setInstanceFollowRedirects(false);h.setRequestProperty("Cache-Control","no-store");h.setRequestProperty("User-Agent","QLoader/1.2.0");
                try{if(closed)throw new InterruptedIOException();int code=h.getResponseCode();
                    if(code>=300&&code<400){String next=h.getHeaderField("Location");if(next==null||redirects==3)throw new IOException("Некорректное перенаправление видео.");address=new URI(address).resolve(next).toString();continue;}
                    if(code!=200)throw new IOException("Источник видео ответил HTTP "+code);
                    long size=h.getContentLengthLong();if(size>LIMIT)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");if(size>0)length.accept(size);
                    try(InputStream in=h.getInputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(closed)throw new InterruptedIOException();sink.write(b,0,n);}}return;
                }finally{h.disconnect();if(http==h)http=null;}
            }
        }
        public void close(){closed=true;Engine e=engine;HttpURLConnection h=http;MediaCleanup.run(()->{if(e!=null)e.cancel();if(h!=null)h.disconnect();});}
    }
    private synchronized void setSize(long value){if(!closed){size=value;notifyAll();}}
    private synchronized void append(byte[] b,int off,int count)throws IOException {
        if(closed)throw new InterruptedIOException();if(count>LIMIT-received)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");
        while(count>0){int index=received/CHUNK,at=received%CHUNK;if(index==chunks.size())chunks.add(new byte[CHUNK]);int n=Math.min(count,CHUNK-at);System.arraycopy(b,off,chunks.get(index),at,n);received+=n;off+=n;count-=n;}notifyAll();
    }
    private void awaitData()throws IOException {try{wait(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}
    @Override public synchronized long getSize()throws IOException {long deadline=System.currentTimeMillis()+30000;while(size<0&&!finished&&!closed&&System.currentTimeMillis()<deadline)awaitData();if(closed)throw new InterruptedIOException();if(failure!=null)throw failure;return size;}
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int count)throws IOException {
        if(closed)throw new InterruptedIOException();if(position<0||offset<0||count<0||offset>buffer.length-count)throw new IOException("Некорректное чтение видео.");if(count==0)return 0;
        if(size>=0&&position>=size)return -1;
        long deadline=System.currentTimeMillis()+30000;
        while(position>=received&&!finished&&!closed&&System.currentTimeMillis()<deadline)awaitData();
        if(closed)throw new InterruptedIOException();if(failure!=null)throw failure;if(position>=received){if(finished)return -1;throw new IOException("Видео слишком долго загружается.");}
        int remaining=(int)Math.min(count,received-position),copied=0,at=(int)position;
        while(copied<remaining){int n=Math.min(remaining-copied,CHUNK-at%CHUNK);System.arraycopy(chunks.get(at/CHUNK),at%CHUNK,buffer,offset+copied,n);copied+=n;at+=n;}return copied;
    }
    @Override public void close(){synchronized(this){closed=true;chunks.clear();notifyAll();}primary.close();if(fallback!=null)fallback.close();}
}
