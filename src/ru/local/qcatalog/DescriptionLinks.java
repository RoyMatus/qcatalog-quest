package ru.local.qcatalog;

import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

/** Linkify both source and translated text each time a description is replaced. */
final class DescriptionLinks {
    static void bind(TextView view,String text){
        view.setText(text);
        Linkify.addLinks(view,Linkify.WEB_URLS|Linkify.EMAIL_ADDRESSES);
        view.setLinksClickable(true);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLinkTextColor(view.getContext().getColor(R.color.qc_primary));
    }
}
