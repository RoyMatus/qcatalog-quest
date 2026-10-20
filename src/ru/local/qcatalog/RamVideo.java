package ru.local.qcatalog;

import android.content.Context;
import android.media.MediaDataSource;
import java.io.*;
import java.net.*;
import java.util.*;

/** Progressive MP4 source with seeking inside a bounded RAM buffer. Never creates a media file. */
final class RamVideo extends MediaDataSource {
    static final int LIMIT=64*1024*1024,CHUNK=256*1024;
    private final List<byte[]> chunks=new ArrayList<>();
    private int received;private long size=-1;private boolean closed,finished;private IOException failure;
    private volatile Engine engine;private volatile HttpURLConnection http;
    RamVideo(Context c,String source,long expected) {
        if(expected>0)size=expected;
        Thread worker=new Thread(()->{
            try {
                if(expected>LIMIT)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");
                OutputStream sink=new OutputStream(){public void write(int b)throws IOException{write(new byte[]{(byte)b});}public void write(byte[] b,int off,int n)throws IOException{append(b,off,n);}};
                if(source.startsWith("ffa:")){engine=new Engine(c);if(isClosed())throw new InterruptedIOException();engine.runTo(Arrays.asList("cat",source.substring(4)),null,sink,LIMIT);}
                else {
                    String address=MediaInfo.https(source);if(address.isEmpty())throw new IOException("Недопустимая ссылка видео.");
                    http=(HttpURLConnection)new URL(address).openConnection();http.setUseCaches(false);http.setConnectTimeout(10000);http.setReadTimeout(15000);http.setInstanceFollowRedirects(false);http.setRequestProperty("Cache-Control","no-store");
                    if(isClosed())throw new InterruptedIOException();if(http.getResponseCode()!=200)throw new IOException("Источник видео недоступен.");
                    long length=http.getContentLengthLong();if(length>LIMIT)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");if(length>0)setSize(length);
                    try(InputStream in=http.getInputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)sink.write(b,0,n);}
                }
                synchronized(this){if(!closed){if(size>0&&received!=size)throw new IOException("Видео загружено не полностью.");size=received;finished=true;notifyAll();}}
            }catch(Exception e){synchronized(this){failure=e instanceof IOException?(IOException)e:new IOException("Не удалось прочитать видео.",e);finished=true;notifyAll();}}
            finally{HttpURLConnection h=http;if(h!=null)h.disconnect();}
        },"qcatalog-video-ram");worker.setDaemon(true);worker.start();
    }
    private synchronized boolean isClosed(){return closed;}
    private synchronized void setSize(long value){size=value;notifyAll();}
    private synchronized void append(byte[] b,int off,int count)throws IOException {
        if(closed)throw new InterruptedIOException();if(count>LIMIT-received)throw new IOException("Трейлер превышает лимит памяти 64 МБ.");
        while(count>0){int index=received/CHUNK,at=received%CHUNK;if(index==chunks.size())chunks.add(new byte[CHUNK]);int n=Math.min(count,CHUNK-at);System.arraycopy(b,off,chunks.get(index),at,n);received+=n;off+=n;count-=n;}notifyAll();
    }
    private void awaitData()throws IOException {try{wait(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}
    @Override public synchronized long getSize()throws IOException {long deadline=System.currentTimeMillis()+30000;while(size<0&&!finished&&!closed&&System.currentTimeMillis()<deadline)awaitData();if(closed)throw new InterruptedIOException();if(failure!=null)throw failure;return size;}
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int count)throws IOException {
        if(position<0||offset<0||count<0||offset>buffer.length-count)throw new IOException("Некорректное чтение видео.");if(count==0)return 0;
        if(size>=0&&position>=size)return -1;
        long deadline=System.currentTimeMillis()+30000;
        while(position>=received&&!finished&&!closed&&System.currentTimeMillis()<deadline)awaitData();
        if(closed)throw new InterruptedIOException();if(failure!=null)throw failure;if(position>=received){if(finished)return -1;throw new IOException("Видео слишком долго загружается.");}
        int remaining=(int)Math.min(count,received-position),copied=0,at=(int)position;
        while(copied<remaining){int n=Math.min(remaining-copied,CHUNK-at%CHUNK);System.arraycopy(chunks.get(at/CHUNK),at%CHUNK,buffer,offset+copied,n);copied+=n;at+=n;}return copied;
    }
    @Override public void close(){synchronized(this){closed=true;chunks.clear();notifyAll();}Engine e=engine;if(e!=null)e.cancel();HttpURLConnection h=http;if(h!=null)h.disconnect();}
}
