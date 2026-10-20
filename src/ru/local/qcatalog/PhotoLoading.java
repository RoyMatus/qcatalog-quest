package ru.local.qcatalog;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Decorative, memory-only loading overlay; never captures media input. */
final class PhotoLoading extends LinearLayout {
    private final ValueAnimator pulse=ValueAnimator.ofFloat(0f,1f);
    private final TextView[] dots=new TextView[3];
    private final TextView count;
    private boolean loading=true,disposed;
    PhotoLoading(Context context){super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView label=Cards.text(context,"Загрузка",26,0xBBFFFFFF);label.setShadowLayer(Cards.dp(context,3),0,0,0xCC000000);addView(label);
        count=Cards.text(context,"Загружено 0 · всего: …",20,0xBBFFFFFF);count.setGravity(Gravity.CENTER);count.setShadowLayer(Cards.dp(context,3),0,0,0xCC000000);count.setPadding(0,Cards.dp(context,4),0,0);addView(count);
        LinearLayout line=new LinearLayout(context);line.setGravity(Gravity.CENTER);addView(line);
        for(int i=0;i<dots.length;i++){TextView dot=Cards.text(context,"•",28,Color.WHITE);dot.setGravity(Gravity.CENTER);dot.setShadowLayer(Cards.dp(context,2),0,0,0xAA000000);line.addView(dot,new LinearLayout.LayoutParams(Cards.dp(context,24),Cards.dp(context,36)));dots[i]=dot;}
        pulse.setDuration(1200);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setInterpolator(new android.view.animation.LinearInterpolator());pulse.addUpdateListener(animation->{float phase=(Float)animation.getAnimatedValue();for(int i=0;i<dots.length;i++)dots[i].setAlpha(.2f+.6f*(float)((Math.cos((phase-i/3f)*Math.PI*2)+1)/2));});
    }
    void progress(int loaded,int total,boolean known){count.setText(known?"Загружено "+loaded+" из "+total:"Загружено "+loaded+" · всего: …");}
    void showLoading(boolean value){loading=value&&!disposed;setVisibility(loading?VISIBLE:GONE);animateIfNeeded();}
    private void animateIfNeeded(){if(loading&&!disposed&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE){if(!pulse.isStarted())pulse.start();}else pulse.cancel();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animateIfNeeded();}
    @Override protected void onDetachedFromWindow(){pulse.cancel();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(pulse!=null)animateIfNeeded();}
    void close(){disposed=true;showLoading(false);pulse.removeAllUpdateListeners();}
}
