package ru.local.qcatalog;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;

/** User-started native player; all buffering lives in RamVideo. */
final class VideoPane extends LinearLayout implements AutoCloseable,SurfaceHolder.Callback {
    final SurfaceView surface;final Button play;final SeekBar seek;final TextView status;
    private final Handler ui=new Handler(Looper.getMainLooper());private MediaPlayer player;private RamVideo source;
    private String url="";private long bytes=-1;private boolean prepared,closed;
    private final Runnable progress=new Runnable(){public void run(){if(closed)return;if(prepared&&player!=null)try{seek.setProgress(player.getCurrentPosition());}catch(IllegalStateException ignored){}ui.postDelayed(this,500);}};
    VideoPane(Context c){super(c);setOrientation(VERTICAL);surface=new SurfaceView(c);surface.getHolder().addCallback(this);surface.setVisibility(GONE);addView(surface,new LayoutParams(-1,Cards.dp(c,360)));status=Cards.text(c,"Видео запускается по кнопке; на шлеме не сохраняется.",20,c.getColor(R.color.qc_text_secondary));addView(status);seek=new SeekBar(c);seek.setMinimumHeight(Cards.dp(c,64));seek.setVisibility(GONE);addView(seek,new LayoutParams(-1,Cards.dp(c,64)));play=Cards.button(c,"Смотреть видео",true);addView(play);play.setOnClickListener(v->toggle());seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&prepared&&player!=null)player.seekTo(value);}});}
    void configure(String address,long size){url=address;bytes=size;setVisibility(url.isEmpty()?GONE:VISIBLE);}
    void toggle(){if(closed)return;if(player==null){surface.setVisibility(VISIBLE);play.setEnabled(false);play.setText("Подготовка видео…");status.setText("Буферизация видео в памяти…");if(surface.getHolder().getSurface().isValid())start();return;}if(prepared){if(player.isPlaying()){player.pause();play.setText("Продолжить видео");}else{player.start();play.setText("Пауза видео");}}}
    private void start(){if(closed||player!=null||url.isEmpty())return;MediaPlayer active=new MediaPlayer();player=active;active.setDisplay(surface.getHolder());active.setOnVideoSizeChangedListener((p,w,h)->{if(w>0&&h>0&&!closed){int height=Math.min(Cards.dp(getContext(),420),Math.max(1,getWidth())*h/w);LayoutParams lp=new LayoutParams(height*w/h,height);lp.gravity=Gravity.CENTER_HORIZONTAL;surface.setLayoutParams(lp);}});active.setOnPreparedListener(p->{if(closed||player!=p)return;prepared=true;seek.setVisibility(VISIBLE);seek.setMax(p.getDuration());play.setEnabled(true);play.setText("Пауза видео");status.setText("Трейлер / геймплей · перемотка ползунком");p.start();ui.post(progress);});active.setOnCompletionListener(p->{if(!closed){play.setText("Смотреть ещё раз");seek.setProgress(0);p.seekTo(0);}});active.setOnErrorListener((p,what,extra)->{if(!closed){releasePlayer();surface.setVisibility(GONE);seek.setVisibility(GONE);play.setEnabled(true);play.setText("Повторить видео");status.setText("Видео недоступно или формат не поддерживается. Попробуй позже.");}return true;});
        source=new RamVideo(getContext(),url,bytes);RamVideo input=source;
        new Thread(()->{try{active.setDataSource(input);if(!closed&&player==active)active.prepareAsync();}catch(Exception e){post(()->{if(!closed&&player==active){releasePlayer();play.setEnabled(true);play.setText("Повторить видео");status.setText("Не удалось открыть видео. Попробуй позже.");}});}},"qcatalog-video-prepare").start();
    }
    void pause(){if(prepared&&player!=null)try{player.pause();play.setText("Продолжить видео");}catch(IllegalStateException ignored){}}
    private void releasePlayer(){prepared=false;ui.removeCallbacks(progress);MediaPlayer p=player;player=null;if(source!=null){source.close();source=null;}if(p!=null)p.release();}
    public void surfaceCreated(SurfaceHolder holder){if(surface.getVisibility()==VISIBLE&&!closed){if(player!=null)player.setDisplay(holder);else start();}}
    public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
    public void surfaceDestroyed(SurfaceHolder h){pause();}
    @Override public void close(){closed=true;releasePlayer();surface.getHolder().removeCallback(this);}
}
