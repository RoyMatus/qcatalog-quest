package ru.local.qcatalog;

import android.content.Context;
import android.view.*;
import android.widget.*;

/** Equal-sized action buttons share one baseline; narrow windows use full-width rows. */
final class ActionRow extends LinearLayout {
    ActionRow(Context context) { super(context);setBaselineAligned(false);setGravity(Gravity.END|Gravity.CENTER_VERTICAL);setPadding(0,Cards.dp(context,16),0,0); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int count=getChildCount(),gap=Cards.dp(getContext(),16);
        int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        int share=count==0?available:(available-gap*(count-1))/count;
        boolean horizontal=true; int buttonWidth=Cards.dp(getContext(),160);
        for(int i=0;i<count;i++) { View child=getChildAt(i);
            if(child instanceof TextView) { TextView text=(TextView)child;float need=text.getPaint().measureText(text.getText().toString())+child.getPaddingLeft()+child.getPaddingRight();buttonWidth=Math.max(buttonWidth,(int)Math.ceil(need));if(need>share)horizontal=false; }
        }
        setOrientation(horizontal?HORIZONTAL:VERTICAL);
        buttonWidth=Math.min(buttonWidth,share);
        for(int i=0;i<count;i++) { LayoutParams p=new LayoutParams(horizontal?buttonWidth:LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
            p.rightMargin=horizontal&&i<count-1?gap:0;p.topMargin=!horizontal&&i>0?gap:0;
            android.view.ViewGroup.LayoutParams existing=getChildAt(i).getLayoutParams();
            if(!(existing instanceof LayoutParams) || existing.width!=p.width || existing.height!=p.height || ((LayoutParams)existing).weight!=0 || ((LayoutParams)existing).rightMargin!=p.rightMargin || ((LayoutParams)existing).topMargin!=p.topMargin) getChildAt(i).setLayoutParams(p);
        }
        super.onMeasure(widthSpec,heightSpec);
        if(horizontal) { int height=0;for(int i=0;i<count;i++)height=Math.max(height,getChildAt(i).getMeasuredHeight());
            for(int i=0;i<count;i++) { View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY)); }
        }
    }
}
