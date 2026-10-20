package ru.local.qcatalog;

import android.app.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Only the selected image is loaded. Session and bitmaps die with the details dialog. */
final class MediaCarousel extends LinearLayout implements AutoCloseable {
    final ImageView image;final FrameLayout frame;final TextView label,origin;final Button previous,next,zoom;final VideoPane video;
    private final Activity activity;private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<String> urls=new ArrayList<>();private GameDetails request;private Bitmap bitmap;
    private String pkg;private int position,generation;private boolean closed,navigationVisible;private AlertDialog enlarged;private float touchX,touchY;
    private final Runnable hideArrows=()->showArrows(false);
    MediaCarousel(Activity c,String packageName){super(c);activity=c;pkg=packageName;setOrientation(VERTICAL);
        frame=new FrameLayout(c);addView(frame,new LayoutParams(-1,-2));
        image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageResource(R.drawable.qc_cover_placeholder);image.setContentDescription("Обложка игры");frame.addView(image,new FrameLayout.LayoutParams(-1,Cards.dp(c,360)));
        image.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();return true;}if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-touchX,dy=event.getY()-touchY;if(Math.abs(dx)>Cards.dp(c,80)&&Math.abs(dx)>Math.abs(dy))select(position+(dx<0?1:-1));else v.performClick();return true;}return false;});
        video=new VideoPane(c);video.setVisibility(GONE);frame.addView(video,new FrameLayout.LayoutParams(-1,-2));
        previous=Cards.button(c,"‹",false);next=Cards.button(c,"›",false);previous.setTextSize(42);next.setTextSize(42);previous.setPadding(0,0,0,0);next.setPadding(0,0,0,0);previous.setContentDescription("Предыдущий кадр");next.setContentDescription("Следующий кадр");
        frame.addView(previous,new FrameLayout.LayoutParams(Cards.dp(c,64),-1,Gravity.START));frame.addView(next,new FrameLayout.LayoutParams(Cards.dp(c,64),-1,Gravity.END));
        video.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->alignArrowHeight());
        View.OnHoverListener hover=(v,event)->{if(event.getAction()==MotionEvent.ACTION_HOVER_ENTER||event.getAction()==MotionEvent.ACTION_HOVER_MOVE){removeCallbacks(hideArrows);showArrows(true);}else if(event.getAction()==MotionEvent.ACTION_HOVER_EXIT)postDelayed(hideArrows,180);return false;};
        for(View target:Arrays.asList(frame,image,previous,next,video,video.surface,video.play,video.seek))target.setOnHoverListener(hover);
        View.OnFocusChangeListener focus=(v,hasFocus)->{removeCallbacks(hideArrows);if(hasFocus)showArrows(true);else postDelayed(hideArrows,180);};previous.setOnFocusChangeListener(focus);next.setOnFocusChangeListener(focus);
        label=Cards.text(c,"Обложка · загрузка…",22,c.getColor(R.color.qc_text));label.setGravity(Gravity.CENTER);addView(label);
        LinearLayout controls=new ActionRow(c);zoom=Cards.button(c,"Увеличить",false);controls.addView(zoom);addView(controls);previous.setOnClickListener(v->select(position-1));next.setOnClickListener(v->select(position+1));zoom.setOnClickListener(v->enlarge());image.setOnClickListener(v->enlarge());image.setFocusable(true);
        origin=Cards.text(c,"Загружаю карусель…",20,c.getColor(R.color.qc_text_secondary));addView(origin);
        urls.add("");select(0);
    }
    void configure(MediaInfo info){if(closed)return;for(String url:info.images)if(!urls.contains(url))urls.add(url);origin.setText(info.note+(urls.size()==1?" · отдельных скриншотов нет":" · изображения по запросу")+(info.trailer.isEmpty()?" · видео нет":""));if(!info.trailer.isEmpty())urls.add("video:"+info.trailer);video.configure(info.trailer,info.trailerSize);video.setVisibility(position==urls.size()-1&&!info.trailer.isEmpty()?VISIBLE:GONE);updateControls();}
    private void showArrows(boolean visible){navigationVisible=visible;previous.setAlpha(visible&&previous.isEnabled()?1f:0f);next.setAlpha(visible&&next.isEnabled()?1f:0f);}
    private void alignArrowHeight(){if(closed||urls.isEmpty())return;boolean movie=urls.get(position).startsWith("video:");int height=movie?(video.surface.getVisibility()==VISIBLE&&video.surface.getHeight()>0?video.surface.getHeight():Cards.dp(activity,64)):-1;for(Button button:Arrays.asList(previous,next)){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)button.getLayoutParams();if(lp.height!=height){lp.height=height;button.setLayoutParams(lp);}}}
    private void updateControls(){previous.setEnabled(position>0);next.setEnabled(position<urls.size()-1);showArrows(navigationVisible);zoom.setEnabled(bitmap!=null&&!urls.get(position).startsWith("video:"));label.setText((urls.get(position).startsWith("video:")?"Видео":position==0?"Обложка":"Скриншот "+position)+" · "+(position+1)+" / "+urls.size());}
    void select(int value){if(closed||value<0||value>=urls.size())return;position=value;int token=++generation;video.pause();if(request!=null)request.close();GameDetails session=new GameDetails(activity);request=session;bitmap=null;image.setImageResource(R.drawable.qc_cover_placeholder);updateControls();label.append(" · загрузка…");String url=urls.get(value);boolean movie=url.startsWith("video:");image.setVisibility(movie?GONE:VISIBLE);video.setVisibility(movie?VISIBLE:GONE);alignArrowHeight();if(movie){updateControls();return;}image.setContentDescription(value==0?"Обложка игры":"Скриншот игры "+value);
        io.execute(()->{try{Bitmap loaded=url.isEmpty()?session.cover(pkg):session.image(url);post(()->{if(!closed&&generation==token){bitmap=loaded;if(loaded!=null)image.setImageBitmap(loaded);updateControls();if(loaded==null)label.append(" · недоступно");}});}catch(Exception e){post(()->{if(!closed&&generation==token){updateControls();label.append(" · источник недоступен");}});}finally{session.close();}});
    }
    void enlarge(){if(closed||bitmap==null)return;video.pause();ImageView large=new ImageView(activity);large.setImageBitmap(bitmap);large.setScaleType(ImageView.ScaleType.FIT_CENTER);large.setContentDescription(image.getContentDescription());AlertDialog d=new AlertDialog.Builder(activity).setTitle(label.getText()).setView(large).setNegativeButton("Закрыть",null).create();Cards.dialog(d);d.setOnDismissListener(v->{large.setImageDrawable(null);enlarged=null;});d.show();enlarged=d;int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;d.getWindow().setLayout((int)(width*.94),(int)(height*.90));}
    void pause(){video.pause();}
    @Override public void close(){closed=true;removeCallbacks(hideArrows);generation++;if(request!=null)request.close();video.close();if(enlarged!=null)enlarged.dismiss();io.shutdownNow();image.setImageDrawable(null);bitmap=null;urls.clear();}
}
