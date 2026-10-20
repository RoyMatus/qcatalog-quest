package ru.local.qcatalog;

import android.app.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Images preload into a card-scoped RAM cache and are released when details close. */
final class MediaCarousel extends LinearLayout implements AutoCloseable {
    final ImageView image;final FrameLayout frame;final Button previous,next;final VideoPane video;
    private final Activity activity;final CardImages images;
    private final List<String> urls=new ArrayList<>();private Bitmap bitmap;
    private String pkg;private int position;private boolean closed,navigationVisible;private Dialog enlarged;private float touchX,touchY;
    private ViewGroup originalParent;private ViewGroup.LayoutParams originalParams;private int originalIndex;private View originalRoot,activityRoot;private float rootAlpha,activityAlpha;
    private Dialog hostDialog;private int hostFlags;private float hostDim,hostWindowAlpha,activityWindowAlpha;
    private boolean mediaHovered;private int scrollDirection;private long scrollStepAt,scrollEventAt;
    private final Runnable hideArrows=()->{mediaHovered=false;showArrows(false);if(enlarged==null)stopNavigation();};
    private int heldDirection,stickDirection,keyDirection;
    private final Runnable repeatNavigation=new Runnable(){public void run(){if(closed||!navigationActive()||heldDirection==0)return;select(position+heldDirection);frame.postDelayed(this,1000);}};
    private void navigationDirection(int direction){if(heldDirection==direction)return;frame.removeCallbacks(repeatNavigation);heldDirection=direction;if(direction!=0){select(position+direction);frame.postDelayed(repeatNavigation,1000);}}
    private void stopNavigation(){stickDirection=keyDirection=scrollDirection=0;navigationDirection(0);}
    private boolean navigationActive(){return enlarged!=null||mediaHovered;}
    private boolean pointerOnMedia(MotionEvent event){android.graphics.Rect bounds=new android.graphics.Rect();if(!frame.getLocalVisibleRect(bounds))return false;int[] screen=new int[2];frame.getLocationOnScreen(screen);bounds.offset(screen[0],screen[1]);return bounds.contains((int)event.getRawX(),(int)event.getRawY());}
    boolean navigateMotion(MotionEvent event){
        if(closed)return false;
        // Horizon OS maps Touch thumbsticks to pointer scroll, not joystick axes.
        if(event.getActionMasked()==MotionEvent.ACTION_SCROLL){
            if(enlarged==null&&!pointerOnMedia(event)){stopNavigation();return false;}
            float x=event.getAxisValue(MotionEvent.AXIS_HSCROLL),y=event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if(Math.abs(x)<.01f||Math.abs(y)>Math.abs(x)){stopNavigation();return false;}
            stickDirection=keyDirection=0;navigationDirection(0);
            long now=event.getEventTime();int direction=x>0?1:-1;
            // Pointer scroll has no release event: a quiet gap ends the previous stick gesture.
            if(direction!=scrollDirection||now-scrollEventAt>150||now-scrollStepAt>=1000){select(position+direction);scrollStepAt=now;}
            scrollDirection=direction;scrollEventAt=now;return true;
        }
        if(!navigationActive()||(!event.isFromSource(InputDevice.SOURCE_JOYSTICK)&&!event.isFromSource(InputDevice.SOURCE_GAMEPAD)))return false;
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){stopNavigation();return true;}
        if(event.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
        int[][] axes={{MotionEvent.AXIS_X,MotionEvent.AXIS_Y},{MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ},{MotionEvent.AXIS_RX,MotionEvent.AXIS_RY},{MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y}};
        float x=0,y=0;for(int[] pair:axes){float value=event.getAxisValue(pair[0]);if(Math.abs(value)>Math.abs(x)){x=value;y=event.getAxisValue(pair[1]);}}
        boolean wasHeld=stickDirection!=0;
        if(Math.abs(x)<.25f||Math.abs(y)>Math.abs(x))stickDirection=0;
        else if(Math.abs(x)>=.65f)stickDirection=x>0?1:-1;
        navigationDirection(stickDirection!=0?stickDirection:keyDirection);return wasHeld||stickDirection!=0;
    }
    boolean navigateKey(KeyEvent event){
        if(closed||!navigationActive())return false;int code=event.getKeyCode();if(code!=KeyEvent.KEYCODE_DPAD_LEFT&&code!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
        int direction=code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
        if(event.getAction()==KeyEvent.ACTION_DOWN)keyDirection=direction;
        else if(event.getAction()==KeyEvent.ACTION_UP&&keyDirection==direction)keyDirection=0;
        navigationDirection(stickDirection!=0?stickDirection:keyDirection);return true;
    }
    MediaCarousel(Activity c,String packageName){super(c);activity=c;pkg=packageName;setOrientation(VERTICAL);
        frame=new FrameLayout(c){@Override public boolean dispatchGenericMotionEvent(MotionEvent event){return navigateMotion(event)||super.dispatchGenericMotionEvent(event);}};addView(frame,new LayoutParams(-1,-2));
        image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageResource(R.drawable.qc_cover_placeholder);image.setContentDescription("Обложка игры");frame.addView(image,new FrameLayout.LayoutParams(-1,Cards.dp(c,360)));
        image.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();return true;}if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-touchX,dy=event.getY()-touchY;if(Math.abs(dx)>Cards.dp(c,80)&&Math.abs(dx)>Math.abs(dy))select(position+(dx<0?1:-1));else v.performClick();return true;}return false;});
        video=new VideoPane(c);video.setVisibility(GONE);frame.addView(video,new FrameLayout.LayoutParams(-1,-2));
        previous=Cards.button(c,"‹",false);next=Cards.button(c,"›",false);previous.setTextSize(48);next.setTextSize(48);previous.setPadding(0,0,0,0);next.setPadding(0,0,0,0);previous.setContentDescription("Предыдущий кадр");next.setContentDescription("Следующий кадр");for(Button arrow:Arrays.asList(previous,next)){arrow.setBackgroundColor(Color.TRANSPARENT);arrow.setTextColor(0xBBFFFFFF);arrow.setShadowLayer(Cards.dp(c,2),0,0,0x99000000);arrow.setStateListAnimator(null);}
        frame.addView(previous,new FrameLayout.LayoutParams(Cards.dp(c,64),-1,Gravity.START));frame.addView(next,new FrameLayout.LayoutParams(Cards.dp(c,64),-1,Gravity.END));
        video.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->alignArrowHeight());
        View.OnHoverListener hover=(v,event)->{if(event.getAction()==MotionEvent.ACTION_HOVER_ENTER||event.getAction()==MotionEvent.ACTION_HOVER_MOVE){mediaHovered=true;removeCallbacks(hideArrows);showArrows(true);}else if(event.getAction()==MotionEvent.ACTION_HOVER_EXIT)postDelayed(hideArrows,180);return false;};
        for(View target:Arrays.asList(frame,image,previous,next))target.setOnHoverListener(hover);video.setNavigationHover(hover);
        View.OnFocusChangeListener focus=(v,hasFocus)->{removeCallbacks(hideArrows);if(hasFocus)showArrows(true);else postDelayed(hideArrows,180);};previous.setOnFocusChangeListener(focus);next.setOnFocusChangeListener(focus);
        previous.setOnClickListener(v->select(position-1));next.setOnClickListener(v->select(position+1));image.setOnClickListener(v->enlarge());image.setFocusable(true);video.setContentClick(this::enlarge);
        images=new CardImages(c,pkg,(key,loaded)->{if(!closed&&!urls.isEmpty()&&urls.get(position).equals(key)){bitmap=loaded;if(loaded!=null)image.setImageBitmap(loaded);}});
        urls.add("");select(0);
    }
    void configure(MediaInfo info){if(closed)return;for(String url:info.images)if(!urls.contains(url)&&urls.size()<CardImages.MAX_IMAGES)urls.add(url);images.prefetch(urls);if(!info.trailer.isEmpty())urls.add("video:"+info.trailer);video.configure(info.trailer,info.trailerSize);video.setVisibility(position==urls.size()-1&&!info.trailer.isEmpty()?VISIBLE:GONE);updateControls();}
    private void showArrows(boolean visible){navigationVisible=visible;previous.setAlpha(visible&&previous.isEnabled()?1f:0f);next.setAlpha(visible&&next.isEnabled()?1f:0f);}
    private void alignArrowHeight(){if(closed||urls.isEmpty())return;boolean movie=urls.get(position).startsWith("video:");int height=movie?Math.max(Cards.dp(activity,64),video.getHeight()-Cards.dp(activity,128)):-1;for(Button button:Arrays.asList(previous,next)){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)button.getLayoutParams();if(lp.height!=height){lp.height=height;button.setLayoutParams(lp);}}}
    private void updateControls(){previous.setEnabled(position>0);next.setEnabled(position<urls.size()-1);showArrows(navigationVisible);}
    void select(int value){if(closed||value<0||value>=urls.size())return;position=value;video.pause();updateControls();String url=urls.get(value);boolean movie=url.startsWith("video:");image.setVisibility(movie?GONE:VISIBLE);video.setVisibility(movie?VISIBLE:GONE);alignArrowHeight();if(movie)return;bitmap=images.get(url);if(bitmap!=null)image.setImageBitmap(bitmap);else{image.setImageResource(R.drawable.qc_cover_placeholder);images.request(url);}image.setContentDescription(value==0?"Обложка игры":"Скриншот игры "+value);
    }
    boolean isExpanded(){return enlarged!=null;}
    void setHostDialog(Dialog dialog){hostDialog=dialog;}
    void enlarge(){if(enlarged!=null){collapse();return;}if(closed||(!urls.get(position).startsWith("video:")&&bitmap==null))return;
        originalParent=(ViewGroup)frame.getParent();originalParams=frame.getLayoutParams();originalIndex=originalParent.indexOfChild(frame);originalRoot=frame.getRootView();activityRoot=activity.getWindow().getDecorView();rootAlpha=originalRoot.getAlpha();activityAlpha=activityRoot.getAlpha();
        originalParent.removeView(frame);Dialog dialog=new Dialog(activity){
            @Override public boolean dispatchGenericMotionEvent(MotionEvent event){return navigateMotion(event)||super.dispatchGenericMotionEvent(event);}
            @Override public boolean dispatchKeyEvent(KeyEvent event){return navigateKey(event)||super.dispatchKeyEvent(event);}
            @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)stopNavigation();}
        };dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout canvas=new FrameLayout(activity);canvas.setBackgroundColor(Color.TRANSPARENT);canvas.addView(frame,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));dialog.setContentView(canvas);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0);window.getDecorView().setPadding(0,0,0,0);enlarged=dialog;
        image.setLayoutParams(new FrameLayout.LayoutParams(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.94)));video.setExpanded(true);dialog.setOnDismissListener(d->{if(enlarged!=null)collapse();});dialog.show();window.setLayout(-1,-1);frame.post(()->video.setExpanded(true));if(hostDialog!=null){Window host=hostDialog.getWindow();hostFlags=host.getAttributes().flags;hostDim=host.getAttributes().dimAmount;hostWindowAlpha=host.getAttributes().alpha;host.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);host.setDimAmount(0f);windowAlpha(host,0f);}activityWindowAlpha=activity.getWindow().getAttributes().alpha;windowAlpha(activity.getWindow(),0f);originalRoot.setAlpha(0f);activityRoot.setAlpha(0f);
    }
    private static void windowAlpha(Window window,float value){WindowManager.LayoutParams lp=window.getAttributes();lp.alpha=value;window.setAttributes(lp);}
    void collapse(){mediaHovered=false;stopNavigation();Dialog dialog=enlarged;if(dialog==null)return;enlarged=null;ViewGroup parent=(ViewGroup)frame.getParent();if(parent!=null)parent.removeView(frame);image.setLayoutParams(new FrameLayout.LayoutParams(-1,Cards.dp(activity,360)));video.setExpanded(false);if(originalParent!=null)originalParent.addView(frame,Math.min(originalIndex,originalParent.getChildCount()),originalParams);if(originalRoot!=null)originalRoot.setAlpha(rootAlpha);if(activityRoot!=null)activityRoot.setAlpha(activityAlpha);if(hostDialog!=null){Window host=hostDialog.getWindow();host.setFlags(hostFlags,WindowManager.LayoutParams.FLAG_DIM_BEHIND);host.setDimAmount(hostDim);windowAlpha(host,hostWindowAlpha);}windowAlpha(activity.getWindow(),activityWindowAlpha);dialog.dismiss();originalParent=null;originalRoot=null;activityRoot=null;frame.post(()->video.setExpanded(false));}
    void pause(){mediaHovered=false;stopNavigation();video.pause();}
    @Override public void close(){closed=true;collapse();removeCallbacks(hideArrows);video.close();image.setImageDrawable(null);bitmap=null;images.close();urls.clear();}
}
