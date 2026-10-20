package ru.local.qcatalog;

import android.app.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Only the selected image is loaded. Session and bitmaps die with the details dialog. */
final class MediaCarousel extends LinearLayout implements AutoCloseable {
    final ImageView image;final TextView label,origin;final Button previous,next,zoom;final VideoPane video;
    private final Activity activity;private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<String> urls=new ArrayList<>();private GameDetails request;private Bitmap bitmap;
    private String pkg;private int position,generation;private boolean closed;private AlertDialog enlarged;private float touchX,touchY;
    MediaCarousel(Activity c,String packageName){super(c);activity=c;pkg=packageName;setOrientation(VERTICAL);
        image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageResource(R.drawable.qc_cover_placeholder);image.setContentDescription("Обложка игры");addView(image,new LayoutParams(-1,Cards.dp(c,360)));
        image.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();return true;}if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-touchX,dy=event.getY()-touchY;if(Math.abs(dx)>Cards.dp(c,80)&&Math.abs(dx)>Math.abs(dy))select(position+(dx<0?1:-1));else v.performClick();return true;}return false;});
        label=Cards.text(c,"Обложка · загрузка…",22,c.getColor(R.color.qc_text));addView(label);
        LinearLayout controls=new ActionRow(c);previous=Cards.button(c,"◀ Назад",false);next=Cards.button(c,"Далее ▶",false);zoom=Cards.button(c,"Увеличить",false);controls.addView(previous);controls.addView(next);controls.addView(zoom);addView(controls);previous.setOnClickListener(v->select(position-1));next.setOnClickListener(v->select(position+1));zoom.setOnClickListener(v->enlarge());image.setOnClickListener(v->enlarge());image.setFocusable(true);
        origin=Cards.text(c,"Загружаю карусель…",20,c.getColor(R.color.qc_text_secondary));addView(origin);
        video=new VideoPane(c);video.setVisibility(GONE);addView(video,1);urls.add("");select(0);
    }
    void configure(MediaInfo info){if(closed)return;for(String url:info.images)if(!urls.contains(url))urls.add(url);origin.setText(info.note+(urls.size()==1?" · отдельных скриншотов нет":" · изображения по запросу")+(info.trailer.isEmpty()?" · видео нет":""));if(!info.trailer.isEmpty())urls.add("video:"+info.trailer);video.configure(info.trailer,info.trailerSize);video.setVisibility(position==urls.size()-1&&!info.trailer.isEmpty()?VISIBLE:GONE);updateControls();}
    private void updateControls(){previous.setEnabled(position>0);next.setEnabled(position<urls.size()-1);zoom.setEnabled(bitmap!=null&&!urls.get(position).startsWith("video:"));label.setText((urls.get(position).startsWith("video:")?"Видео":position==0?"Обложка":"Скриншот "+position)+" · "+(position+1)+" / "+urls.size());}
    void select(int value){if(closed||value<0||value>=urls.size())return;position=value;int token=++generation;video.pause();if(request!=null)request.close();GameDetails session=new GameDetails(activity);request=session;bitmap=null;image.setImageResource(R.drawable.qc_cover_placeholder);updateControls();label.append(" · загрузка…");String url=urls.get(value);boolean movie=url.startsWith("video:");image.setVisibility(movie?GONE:VISIBLE);video.setVisibility(movie?VISIBLE:GONE);if(movie){updateControls();return;}image.setContentDescription(value==0?"Обложка игры":"Скриншот игры "+value);
        io.execute(()->{try{Bitmap loaded=url.isEmpty()?session.cover(pkg):session.image(url);post(()->{if(!closed&&generation==token){bitmap=loaded;if(loaded!=null)image.setImageBitmap(loaded);updateControls();if(loaded==null)label.append(" · недоступно");}});}catch(Exception e){post(()->{if(!closed&&generation==token){updateControls();label.append(" · источник недоступен");}});}finally{session.close();}});
    }
    void enlarge(){if(closed||bitmap==null)return;video.pause();ImageView large=new ImageView(activity);large.setImageBitmap(bitmap);large.setScaleType(ImageView.ScaleType.FIT_CENTER);large.setContentDescription(image.getContentDescription());AlertDialog d=new AlertDialog.Builder(activity).setTitle(label.getText()).setView(large).setNegativeButton("Закрыть",null).create();Cards.dialog(d);d.setOnDismissListener(v->{large.setImageDrawable(null);enlarged=null;});d.show();enlarged=d;int width=activity.getResources().getDisplayMetrics().widthPixels,height=activity.getResources().getDisplayMetrics().heightPixels;d.getWindow().setLayout((int)(width*.94),(int)(height*.90));}
    void pause(){video.pause();}
    @Override public void close(){closed=true;generation++;if(request!=null)request.close();video.close();if(enlarged!=null)enlarged.dismiss();io.shutdownNow();image.setImageDrawable(null);bitmap=null;urls.clear();}
}
