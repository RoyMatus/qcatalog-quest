package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Uses the exact resources supplied in design/cards-v1/android/res. */
final class Cards {
    static int dp(Context c,int v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    static int color(Context c,int id) { return c.getColor(id); }
    static TextView text(Context c,String value,int size,int color) { TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    static Drawable background(Context c,int resource) {
        Drawable base=c.getDrawable(resource);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_hovered},new LayerDrawable(new Drawable[]{c.getDrawable(resource),c.getDrawable(R.drawable.qc_focus_ring)}));
        states.addState(new int[]{android.R.attr.state_focused},new LayerDrawable(new Drawable[]{c.getDrawable(resource),c.getDrawable(R.drawable.qc_focus_ring)}));
        states.addState(new int[]{},base);
        return new RippleDrawable(ColorStateList.valueOf(0x22000000),states,null);
    }
    static void target(Context c,View view,int background) {
        view.setBackground(Cards.background(c,background)); view.setFocusable(true); view.setClickable(true);
        view.setMinimumHeight(c.getResources().getDimensionPixelSize(R.dimen.qc_touch_min));
    }
    static Button button(Context c,String value,boolean primary) {
        Button b=new Button(c); b.setText(value); b.setAllCaps(false); b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,c.getResources().getDimension(R.dimen.qc_label)); b.setMinHeight(c.getResources().getDimensionPixelSize(R.dimen.qc_button_height)); b.setMinimumHeight(c.getResources().getDimensionPixelSize(R.dimen.qc_touch_min));
        b.setMinWidth(dp(c,64)); b.setTextColor(c.getColor(primary ? R.color.qc_on_primary : R.color.qc_text));
        b.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,12)); b.setMaxLines(3);
        target(c,b,primary ? R.drawable.qc_bg_primary : R.drawable.qc_bg_surface); return b;
    }
    static void dialog(android.app.AlertDialog d) {
        d.setOnShowListener(x -> {
            for(int which:new int[]{-1,-2,-3}) { Button b=d.getButton(which); if(b!=null) { b.setMinHeight(dp(d.getContext(),64)); b.setTextSize(20); b.setAllCaps(false); } }
        });
    }
}
