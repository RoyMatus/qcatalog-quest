package ru.local.qcatalog;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.GridView;

/** Persistent, separately reserved VR pointer target. Never overlaps game tiles. */
final class ScrollRail extends View {
    static final class Grid extends GridView {
        Grid(Context context){super(context);}
        int range(){return computeVerticalScrollRange();}
        int extent(){return computeVerticalScrollExtent();}
        int offset(){return computeVerticalScrollOffset();}
        void seek(float fraction){
            if(getCount()==0||getChildCount()==0)return;
            if(fraction<=0){setSelection(0);return;}
            if(fraction>=1){setSelection(getCount()-1);return;}
            float row=fraction*Math.max(0,range()-extent())/100f;
            int whole=(int)row,columns=Math.max(1,getNumColumns());
            // GridView handles touch-mode selection itself; inherited setSelectionFromTop does not.
            setSelection(Math.min(getCount()-1,whole*columns));
        }
    }
    private final Grid grid;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumb=new RectF();
    private boolean dragging;
    private float grab,dragFraction;
    ScrollRail(Context context,Grid grid){
        super(context);this.grid=grid;setMinimumWidth(Cards.dp(context,64));setFocusable(true);setClickable(true);
        setContentDescription("Прокрутка списка");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float inset(){return Cards.dp(getContext(),8);}
    boolean scrollable(){return grid.getCount()>0&&(grid.canScrollList(-1)||grid.canScrollList(1));}
    private float fraction(){int max=grid.range()-grid.extent();return max<=0?0:Math.max(0,Math.min(1,grid.offset()/(float)max));}
    RectF thumbBounds(){
        float top=inset(),height=Math.max(0,getHeight()-2*top);
        float ratio=grid.range()<=0?1:Math.min(1,grid.extent()/(float)grid.range());
        float size=Math.min(height,Math.max(Cards.dp(getContext(),72),height*ratio));
        float y=top+(height-size)*(dragging?dragFraction:fraction());
        float width=Cards.dp(getContext(),isHovered()||dragging?36:28);
        thumb.set((getWidth()-width)/2,y,(getWidth()+width)/2,y+size);return new RectF(thumb);
    }
    void sync(){if(dragging&&!scrollable())finishDrag();invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!scrollable())return;
        float center=getWidth()/2f,half=Cards.dp(getContext(),5);
        paint.setColor(getContext().getColor(R.color.qc_surface_alt));
        canvas.drawRoundRect(center-half,inset(),center+half,getHeight()-inset(),half,half,paint);
        RectF rect=thumbBounds();paint.setColor(getContext().getColor(dragging||isHovered()||isFocused()?R.color.qc_primary:R.color.qc_text_secondary));
        canvas.drawRoundRect(rect,Cards.dp(getContext(),14),Cards.dp(getContext(),14),paint);
        paint.setColor(getContext().getColor(R.color.qc_surface));paint.setStrokeWidth(Cards.dp(getContext(),2));
        for(int i=-1;i<=1;i++){float y=rect.centerY()+Cards.dp(getContext(),6)*i;canvas.drawLine(rect.centerX()-Cards.dp(getContext(),7),y,rect.centerX()+Cards.dp(getContext(),7),y,paint);}
    }
    private void move(float y){
        float travel=Math.max(0,getHeight()-2*inset()-thumbBounds().height());
        dragFraction=travel<=0?0:Math.max(0,Math.min(1,(y-grab-inset())/travel));
        grid.seek(dragFraction);invalidate();
    }
    private void finishDrag(){dragging=false;setPressed(false);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!scrollable()){finishDrag();return false;}
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                RectF rect=thumbBounds();dragFraction=fraction();dragging=true;
                grab=event.getY()>=rect.top&&event.getY()<=rect.bottom?event.getY()-rect.top:rect.height()/2;
                setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);move(event.getY());return true;
            case MotionEvent.ACTION_MOVE:if(dragging){move(event.getY());return true;}return false;
            case MotionEvent.ACTION_UP:if(dragging){move(event.getY());finishDrag();performClick();return true;}return false;
            case MotionEvent.ACTION_CANCEL:finishDrag();return true;
            default:return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onHoverEvent(MotionEvent event){boolean result=super.onHoverEvent(event);invalidate();return result;}
    @Override protected void onDetachedFromWindow(){finishDrag();super.onDetachedFromWindow();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setScrollable(scrollable());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0,1,fraction()));
        if(scrollable()){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(scrollable()){
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){grid.seek(Math.max(0,Math.min(1,args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE))));return true;}
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){grid.scrollListBy((action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*Math.max(1,grid.getHeight()-Cards.dp(getContext(),64)));return true;}
        }
        return super.performAccessibilityAction(action,args);
    }
}
