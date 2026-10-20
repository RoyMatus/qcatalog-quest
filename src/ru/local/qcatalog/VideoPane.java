package ru.local.qcatalog;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Texture player with transparent letterboxing and bottom playback controls; media stays in RAM. */
final class VideoPane extends FrameLayout implements AutoCloseable,TextureView.SurfaceTextureListener {
    final TextureView surface;final Button play,mute;final SeekBar seek,volume;final TextView status,time;final LinearLayout controls;
    private final Handler ui=new Handler(Looper.getMainLooper());private MediaPlayer player;private RamVideo source;private Surface output;
    private String url="",fallbackUrl="";private long bytes=-1;private boolean prepared,closed,expanded,resumeSurface,wantPlay;private float level=.8f;
    private int videoWidth=16,videoHeight=9,lastVolume=80;private View.OnHoverListener navigationHover;
    private final Runnable hideControls=this::hideIfPlaying;
    private final Runnable progress=new Runnable(){public void run(){if(closed)return;if(prepared&&player!=null)try{int at=player.getCurrentPosition();seek.setProgress(at);time.setText(clock(at)+" / "+clock(player.getDuration()));}catch(IllegalStateException ignored){}ui.postDelayed(this,500);}};
    VideoPane(Context c){super(c);setBackgroundColor(Color.TRANSPARENT);
        surface=new TextureView(c);surface.setOpaque(false);surface.setSurfaceTextureListener(this);surface.setContentDescription("Видео: нажми для увеличения или возврата");addView(surface,new LayoutParams(-1,-1));
        status=Cards.text(c,"Видео запускается кнопкой ▶",20,Color.WHITE);status.setPadding(Cards.dp(c,16),Cards.dp(c,12),Cards.dp(c,16),0);addView(status,new LayoutParams(-1,-2,Gravity.TOP));
        controls=new LinearLayout(c);controls.setOrientation(LinearLayout.VERTICAL);controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00202020,0xC8202020}));addView(controls,new LayoutParams(-1,-2,Gravity.BOTTOM));
        seek=new SeekBar(c);seek.setContentDescription("Позиция видео");seek.setMinimumHeight(Cards.dp(c,64));seek.setProgressTintList(android.content.res.ColorStateList.valueOf(c.getColor(R.color.qc_primary)));controls.addView(seek,new LinearLayout.LayoutParams(-1,Cards.dp(c,64)));
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);controls.addView(row);
        play=control(c,"▶","Воспроизвести");row.addView(play,new LinearLayout.LayoutParams(Cards.dp(c,64),Cards.dp(c,64)));
        time=Cards.text(c,"0:00 / 0:00",20,Color.WHITE);time.setGravity(Gravity.CENTER);row.addView(time,new LinearLayout.LayoutParams(0,Cards.dp(c,64),1));
        mute=control(c,"","Выключить звук");volumeIcon(false);row.addView(mute,new LinearLayout.LayoutParams(Cards.dp(c,64),Cards.dp(c,64)));
        volume=new SeekBar(c);volume.setMax(100);volume.setProgress(80);volume.setMinimumHeight(Cards.dp(c,64));volume.setContentDescription("Громкость видео");row.addView(volume,new LinearLayout.LayoutParams(Cards.dp(c,112),Cards.dp(c,64)));
        play.setOnClickListener(v->toggle());mute.setOnClickListener(v->volume.setProgress(volume.getProgress()==0?lastVolume:0));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){revealControls();}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){level=value/100f;if(value>0)lastVolume=value;volumeIcon(value==0);mute.setContentDescription(value==0?"Включить звук":"Выключить звук");if(prepared&&player!=null)player.setVolume(level,level);}});
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){revealControls();}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&prepared&&player!=null)player.seekTo(value);}});
        View.OnHoverListener hover=(v,event)->{if(event.getAction()==MotionEvent.ACTION_HOVER_ENTER||event.getAction()==MotionEvent.ACTION_HOVER_MOVE)revealControls();else if(event.getAction()==MotionEvent.ACTION_HOVER_EXIT)ui.postDelayed(hideControls,1800);if(navigationHover!=null)navigationHover.onHover(v,event);return false;};
        for(View view:Arrays.asList(this,surface,controls,play,mute,seek,volume,time))view.setOnHoverListener(hover);
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)post(this::updateHeight);fit();});
    }
    private static Button control(Context c,String text,String description){Button b=Cards.button(c,text,false);b.setBackgroundColor(Color.TRANSPARENT);b.setTextColor(Color.WHITE);b.setPadding(0,0,0,0);b.setContentDescription(description);b.setStateListAnimator(null);return b;}
    private void volumeIcon(boolean silent){android.graphics.drawable.Drawable icon=getContext().getDrawable(silent?android.R.drawable.ic_lock_silent_mode:android.R.drawable.ic_lock_silent_mode_off).mutate();icon.setTint(Color.WHITE);icon.setBounds(0,0,Cards.dp(getContext(),24),Cards.dp(getContext(),24));mute.setCompoundDrawables(icon,null,null,null);}
    static String clock(int ms){int seconds=Math.max(0,ms/1000);return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    void setContentClick(Runnable click){surface.setOnClickListener(v->click.run());surface.setFocusable(true);}
    void setNavigationHover(View.OnHoverListener listener){navigationHover=listener;}
    void configure(String address,long size){url=address;bytes=size;setVisibility(url.isEmpty()?GONE:VISIBLE);updateHeight();}
    void configure(String address,long size,String fallback){configure(address,size);fallbackUrl=fallback;}
    void setExpanded(boolean value){expanded=value;updateHeight();}
    private void updateHeight(){int width=getWidth()>0?getWidth():Cards.dp(getContext(),680);int max=expanded?(int)(getResources().getDisplayMetrics().heightPixels*.94):Cards.dp(getContext(),420);int h=Math.max(Cards.dp(getContext(),240),Math.min(max,width*videoHeight/videoWidth));ViewGroup.LayoutParams lp=getLayoutParams();if(lp!=null&&lp.height!=h){lp.height=h;setLayoutParams(lp);}post(this::fit);}
    private void fit(){int w=surface.getWidth(),h=surface.getHeight();if(w<=0||h<=0)return;float scale=Math.min((float)w/videoWidth,(float)h/videoHeight);Matrix matrix=new Matrix();matrix.setScale(videoWidth*scale/w,videoHeight*scale/h,w/2f,h/2f);surface.setTransform(matrix);}
    void revealControls(){ui.removeCallbacks(hideControls);controls.setAlpha(1f);}
    private void hideIfPlaying(){if(!closed&&prepared&&player!=null)try{if(player.isPlaying())controls.setAlpha(0f);}catch(IllegalStateException ignored){}}
    void toggle(){if(closed)return;revealControls();if(player==null){wantPlay=true;play.setEnabled(false);status.setVisibility(VISIBLE);status.setText("Буферизация…");if(surface.isAvailable())start();else resumeSurface=true;return;}if(prepared){if(player.isPlaying())pause();else{wantPlay=true;player.start();play.setText("Ⅱ");play.setContentDescription("Пауза");}}}
    private void start(){if(closed||player!=null||url.isEmpty()||!surface.isAvailable())return;MediaPlayer active=new MediaPlayer();player=active;attach(surface.getSurfaceTexture());
        active.setOnVideoSizeChangedListener((p,w,h)->{if(w>0&&h>0&&!closed){videoWidth=w;videoHeight=h;updateHeight();}});
        active.setOnPreparedListener(p->{if(closed||player!=p)return;prepared=true;seek.setMax(p.getDuration());play.setEnabled(true);status.setVisibility(GONE);p.setVolume(level,level);if(wantPlay&&getVisibility()==VISIBLE){play.setText("Ⅱ");play.setContentDescription("Пауза");p.start();}else{play.setText("▶");play.setContentDescription("Воспроизвести");}ui.post(progress);});
        active.setOnCompletionListener(p->{if(!closed){wantPlay=false;play.setText("▶");play.setContentDescription("Смотреть ещё раз");p.seekTo(0);revealControls();}});
        active.setOnErrorListener((p,what,extra)->{if(!closed&&player==p)failed();return true;});
        source=new RamVideo(getContext(),url,bytes,fallbackUrl);RamVideo input=source;
        new Thread(()->{try{active.setDataSource(input);if(!closed&&player==active)active.prepareAsync();}catch(Exception e){post(()->{if(!closed&&player==active)failed();});}},"qcatalog-video-prepare").start();
    }
    private void failed(){releasePlayer();play.setEnabled(true);play.setText("▶");play.setContentDescription("Повторить видео");status.setText("Не удалось открыть видео. Попробуй позже.");status.setVisibility(VISIBLE);revealControls();}
    void pause(){wantPlay=false;resumeSurface=false;if(player==null)play.setEnabled(true);if(prepared&&player!=null)try{player.pause();play.setText("▶");play.setContentDescription("Воспроизвести");revealControls();}catch(IllegalStateException ignored){}}
    private void attach(SurfaceTexture texture){if(output!=null)output.release();output=new Surface(texture);if(player!=null)player.setSurface(output);}
    private void releasePlayer(){prepared=false;ui.removeCallbacks(progress);ui.removeCallbacks(hideControls);MediaPlayer p=player;player=null;if(source!=null){source.close();source=null;}if(p!=null)p.release();if(output!=null){output.release();output=null;}}
    public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h){if(closed)return;if(player!=null){attach(texture);if(resumeSurface&&prepared&&wantPlay){player.start();play.setText("Ⅱ");play.setContentDescription("Пауза");}resumeSurface=false;}else if(resumeSurface&&wantPlay){resumeSurface=false;start();}fit();}
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h){fit();}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){if(player!=null){try{resumeSurface=prepared&&player.isPlaying();if(resumeSurface)player.pause();player.setSurface(null);}catch(IllegalStateException ignored){}}if(output!=null){output.release();output=null;}return true;}
    public void onSurfaceTextureUpdated(SurfaceTexture texture){}
    @Override public void close(){closed=true;releasePlayer();surface.setSurfaceTextureListener(null);}
}
