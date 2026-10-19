package ru.local.qcatalog;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.Locale;

final class DescriptionText {
    static String plain(String value) {
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?i)<br\\s*/?>|</p>", "\n").replaceAll("<[^>]*>", "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
    static String brief(String value, int maxBytes) {
        String text = plain(value); if (text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return text;
        BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.ENGLISH); sentences.setText(text);
        int end = 0;
        for (int next = sentences.first(); (next = sentences.next()) != BreakIterator.DONE;) {
            if (text.substring(0,next).getBytes(StandardCharsets.UTF_8).length > maxBytes) break;
            end = next;
        }
        if (end > 40) return text.substring(0,end).trim();
        int limit = 0;
        while (limit < text.length()) {
            int next = limit + Character.charCount(text.codePointAt(limit));
            if (text.substring(0,next).getBytes(StandardCharsets.UTF_8).length > maxBytes - 3) break;
            limit = next;
        }
        int space = text.lastIndexOf(' ',limit); if (space > limit / 2) limit = space;
        return text.substring(0,limit).trim() + "…";
    }
    static boolean hasRussianText(String text) {
        int cyrillic = 0, latin = 0;
        for (char ch : text.toCharArray()) { if ((ch >= 'А' && ch <= 'я') || ch == 'ё' || ch == 'Ё') cyrillic++; else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) latin++; }
        return cyrillic > 12 && cyrillic > latin;
    }
}
