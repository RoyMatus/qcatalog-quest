package ru.local.qcatalog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Card-scoped data only: no descriptions, translations, or images are written to disk. */
final class GameDetails implements AutoCloseable {
    static final String STORE_BASE = "https://cloud.dipvr.ru:8008/api/v1/oculusgames/";
    static final class Info {
        String original = "", russian = "", note = "", developer = "", source = "QLoader";
    }
    final Context context;
    private volatile HttpURLConnection connection;
    private volatile Engine imageEngine;
    volatile boolean closed;
    GameDetails(Context c) { context = c.getApplicationContext(); }
    private byte[] get(String address, int maxBytes) throws Exception {
        if (closed) throw new InterruptedIOException();
        URI uri = Engine.validateUrl(address);
        HttpURLConnection http = (HttpURLConnection)uri.toURL().openConnection(); connection = http;
        http.setConnectTimeout(10000); http.setReadTimeout(12000); http.setUseCaches(false); http.setInstanceFollowRedirects(false);
        http.setRequestProperty("Cache-Control","no-store"); http.setRequestProperty("Pragma","no-cache");
        http.setRequestProperty("User-Agent","QLoader/1.2.0"); http.setRequestProperty("Accept-Language","ru,en;q=0.7");
        try {
            if (closed) throw new InterruptedIOException();
            int code = http.getResponseCode();
            if (code == 404) throw new FileNotFoundException();
            if (code != 200) throw new IOException("Сервис ответил HTTP " + code);
            try (InputStream in = http.getInputStream()) { return PrivateStore.read(in,maxBytes); }
        } finally { http.disconnect(); if (connection == http) connection = null; }
    }
    static String originalPackage(String pkg) {
        return pkg.replaceFirst("^(mr|mrf)\\.", "").replaceFirst("\\.jjb$", "");
    }
    Info description(String pkg) throws Exception {
        pkg = originalPackage(pkg); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя приложения.");
        Info result = new Info();
        JSONObject json;
        try { json = new JSONObject(new String(get(STORE_BASE + pkg,2 * 1024 * 1024),StandardCharsets.UTF_8)); }
        catch (FileNotFoundException e) { result.note = "В источнике QLoader нет описания этой игры."; return result; }
        String returnedPackage = json.optString("package_name");
        if (!returnedPackage.isEmpty() && !returnedPackage.equalsIgnoreCase(pkg)) throw new IOException("Источник вернул сведения о другом приложении.");
        result.original = DescriptionText.plain(json.optString("description", ""));
        result.developer = DescriptionText.plain(json.optString("developer_name", ""));
        if (result.original.isEmpty()) { result.note = "В источнике QLoader нет описания этой игры."; return result; }
        if (DescriptionText.hasRussianText(result.original)) {
            result.russian = DescriptionText.brief(result.original,1500); result.note = "Описание из QLoader"; return result;
        }
        // Anonymous documented API; only a short public description is sent, never QLoader access data.
        String brief = DescriptionText.brief(result.original,450);
        try {
            JSONObject translated = new JSONObject(new String(get("https://api.mymemory.translated.net/get?langpair=en%7Cru&q=" + URLEncoder.encode(brief,"UTF-8"),256 * 1024),StandardCharsets.UTF_8));
            if (translated.optBoolean("quotaFinished") || translated.optInt("responseStatus") == 429) {
                result.note = "На сегодня исчерпан лимит онлайн-перевода. Доступен оригинал."; return result;
            }
            JSONObject data = translated.optJSONObject("responseData");
            if (translated.optInt("responseStatus") != 200 || data == null) throw new IOException("Перевод временно недоступен.");
            String text = DescriptionText.plain(data.optString("translatedText"));
            if (text.isEmpty() || !DescriptionText.hasRussianText(text)) throw new IOException("Сервис не вернул русский текст.");
            result.russian = text; result.note = "Автоперевод MyMemory · источник QLoader";
        } catch (Exception e) {
            if (closed) throw new InterruptedIOException();
            result.note = "Русский перевод сейчас недоступен. Можно посмотреть оригинал или открыть карточку позже.";
        }
        return result;
    }
    Bitmap cover(String pkg) throws Exception {
        pkg = originalPackage(pkg); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя приложения.");
        Engine engine = new Engine(context); imageEngine = engine;
        if (closed) { engine.cancel(); throw new InterruptedIOException(); }
        String mirror = context.getSharedPreferences("state",0).getString("mirror","");
        if (!mirror.matches("FFA-\\d+")) throw new IOException("Обнови каталог для загрузки обложек.");
        for (String extension : Arrays.asList("jpg","png")) {
            if (closed) throw new InterruptedIOException();
            try {
                byte[] data = engine.runBytes(Arrays.asList("cat",mirror + ":Quest Games/.meta/thumbnails/" + pkg + "." + extension),null,8 * 1024 * 1024);
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data,0,data.length,options);
                if (options.outWidth < 1 || options.outHeight < 1) continue;
                options.inJustDecodeBounds = false; options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 1000 || options.outHeight / options.inSampleSize > 1000) options.inSampleSize *= 2;
                Bitmap bitmap = BitmapFactory.decodeByteArray(data,0,data.length,options);
                if (bitmap != null) return bitmap;
            } catch (Exception e) { if (closed) throw new InterruptedIOException(); }
        }
        return null;
    }
    @Override public void close() { closed = true; HttpURLConnection http = connection; if (http != null) http.disconnect(); Engine engine = imageEngine; if (engine != null) engine.cancel(); }
}
