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
    private final Set<HttpURLConnection> requests=Collections.synchronizedSet(new HashSet<>());
    private JSONObject metadata;
    volatile boolean closed;
    GameDetails(Context c) { context = c.getApplicationContext(); }
    byte[] get(String address, int maxBytes) throws Exception {
        return get(address,maxBytes,0);
    }
    private byte[] get(String address,int maxBytes,int redirects)throws Exception {
        if (closed) throw new InterruptedIOException();
        URI uri = Engine.validateUrl(address);
        HttpURLConnection http = (HttpURLConnection)uri.toURL().openConnection(); connection = http;requests.add(http);
        http.setConnectTimeout(10000); http.setReadTimeout(12000); http.setUseCaches(false); http.setInstanceFollowRedirects(false);
        http.setRequestProperty("Cache-Control","no-store"); http.setRequestProperty("Pragma","no-cache");
        http.setRequestProperty("User-Agent","QLoader/1.2.0"); http.setRequestProperty("Accept-Language","ru,en;q=0.7");
        try {
            if (closed) throw new InterruptedIOException();
            int code = http.getResponseCode();
            if(code>=300&&code<400){if(redirects>=3)throw new IOException("Слишком много перенаправлений.");String location=http.getHeaderField("Location");if(location==null)throw new IOException("Некорректное перенаправление.");String next=uri.resolve(location).toString();URI target=Engine.validateUrl(next);if(!target.getHost().equalsIgnoreCase(uri.getHost())&&MediaInfo.https(next).isEmpty())throw new IOException("Недопустимый адрес медиа.");return get(next,maxBytes,redirects+1);}
            if (code == 404) throw new FileNotFoundException();
            if (code != 200) throw new IOException("Сервис ответил HTTP " + code);
            try (InputStream in = http.getInputStream()) { return PrivateStore.read(in,maxBytes); }
        } finally { http.disconnect();requests.remove(http); if (connection == http) connection = null; }
    }
    private synchronized JSONObject metadata(String pkg)throws Exception {
        if(metadata==null)metadata=new JSONObject(new String(get(STORE_BASE+pkg,2*1024*1024),StandardCharsets.UTF_8));
        String actual=metadata.optString("package_name");if(!actual.isEmpty()&&!actual.equalsIgnoreCase(pkg))throw new IOException("Источник вернул сведения о другом приложении.");return metadata;
    }
    static String originalPackage(String pkg) {
        return pkg.replaceFirst("^(mr|mrf)\\.", "").replaceFirst("\\.jjb$", "");
    }
    Info description(String pkg) throws Exception {
        return description(pkg,info -> {});
    }
    Info description(String pkg,java.util.function.Consumer<Info> originalReady) throws Exception {
        pkg = originalPackage(pkg); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя приложения.");
        Info result = new Info();
        JSONObject json;
        try { json = metadata(pkg); }
        catch (FileNotFoundException e) { result.note = "В источнике QLoader нет описания этой игры."; return result; }
        String returnedPackage = json.optString("package_name");
        if (!returnedPackage.isEmpty() && !returnedPackage.equalsIgnoreCase(pkg)) throw new IOException("Источник вернул сведения о другом приложении.");
        result.original = DescriptionText.plain(json.optString("description", ""));
        result.developer = DescriptionText.plain(json.optString("developer_name", ""));
        if (result.original.isEmpty()) { result.note = "В источнике QLoader нет описания этой игры."; return result; }
        if (DescriptionText.hasRussianText(result.original)) {
            result.russian = DescriptionText.brief(result.original,1500); result.note = "Описание из QLoader"; return result;
        }
        // Display the source immediately; translation must not delay a readable description.
        originalReady.accept(result);
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
    private MediaInfo publicMedia(String pkg)throws Exception {
        MediaInfo info;try{info=MediaInfo.provider(metadata(pkg));}catch(Exception e){if(closed)throw new InterruptedIOException();info=new MediaInfo();}
        if(!info.storeId.isEmpty())try{info.metaPage(new String(get("https://www.meta.com/experiences/quest/"+info.storeId+"/",4*1024*1024),StandardCharsets.UTF_8));}catch(Exception ignored){}
        if(closed)throw new InterruptedIOException();return info;
    }
    MediaInfo media(Catalog.Game game,String pkg)throws Exception {
        pkg=originalPackage(pkg);if(!Catalog.safePackage(pkg))throw new IOException("Некорректное имя приложения.");
        MediaInfo info=publicMedia(pkg);
        // Do not hold a usable gallery behind slow/unavailable mirror directory scans.
        if(info.note.equals("QLoader → галерея Meta")&&!info.images.isEmpty()&&!info.trailer.isEmpty())return info;
        String mirror=context.getSharedPreferences("state",0).getString("mirror","");
        if(mirror.matches("FFA-\\d+")) {
            Engine engine=new Engine(context);imageEngine=engine;
            try {
                JSONArray videos=Engine.fileListing(engine.run(Arrays.asList("lsjson",mirror+":Quest Games/.meta/videos/","--files-only"),null));
                for(int i=0;i<videos.length();i++){JSONObject row=videos.getJSONObject(i);if(row.getString("Path").equalsIgnoreCase(pkg+".mp4")&&row.getLong("Size")>0){info.trailer="ffa:"+mirror+":Quest Games/.meta/videos/"+row.getString("Path");info.trailerSize=row.getLong("Size");break;}}
            }catch(Exception ignored){}
            if(Catalog.safeRelease(game.release))try {
                JSONArray files=Engine.fileListing(engine.run(Arrays.asList("lsjson",mirror+":Quest Games/"+game.release,"--files-only"),null));
                for(int i=0;i<files.length()&&info.images.size()<16;i++){String path=files.getJSONObject(i).getString("Path");if(!path.contains("/")&&!path.contains("\\")&&path.toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png|webp)"))info.images.add("ffa:"+mirror+":Quest Games/"+game.release+"/"+path);}
            }catch(Exception ignored){}
        }
        if(closed)throw new InterruptedIOException();return info;
    }
    Bitmap image(String address)throws Exception {
        return image(address,1600*1600);
    }
    Bitmap image(String address,int maxPixels)throws Exception {
        byte[] data;
        if(address.startsWith("ffa:")){Engine engine=new Engine(context);imageEngine=engine;if(closed)throw new InterruptedIOException();data=engine.runBytes(Arrays.asList("cat",address.substring(4)),null,8*1024*1024);}
        else {if(MediaInfo.https(address).isEmpty())throw new IOException("Недопустимая ссылка изображения.");data=get(address,8*1024*1024);}
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
        if(options.outWidth<1||options.outHeight<1)throw new IOException("Изображение недоступно.");options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>1600||options.outHeight/options.inSampleSize>1600)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)throw new IOException("Не удалось прочитать изображение.");return limitImage(bitmap,maxPixels);
    }
    Bitmap cover(String pkg) throws Exception {
        return cover(pkg,1000*1000);
    }
    Bitmap cover(String pkg,int maxPixels) throws Exception {
        pkg = originalPackage(pkg); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя приложения.");
        // List thumbnails never discover a gallery or trailer. Refresh only a failed cover URL.
        try{JSONObject info=metadata(pkg);String cover=MediaInfo.https(info.optString("cover_url"));if(!cover.isEmpty())try{Bitmap bitmap=image(cover,maxPixels);if(bitmap!=null)return bitmap;}catch(Exception ignored){if(closed)throw new InterruptedIOException();}
            String id=info.optString("id");if(id.matches("[0-9]{5,30}")){String page=new String(get("https://www.meta.com/experiences/quest/"+id+"/",4*1024*1024),StandardCharsets.UTF_8);cover=MediaInfo.coverFromPage(page);if(!cover.isEmpty()){Bitmap bitmap=image(cover,maxPixels);if(bitmap!=null)return bitmap;}}
        }catch(Exception ignored){if(closed)throw new InterruptedIOException();}
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
                if (bitmap != null) return limitImage(bitmap,maxPixels);
            } catch (Exception e) { if (closed) throw new InterruptedIOException(); }
        }
        return null;
    }
    static Bitmap limitImage(Bitmap bitmap,int maxPixels){
        if(bitmap==null||(long)bitmap.getWidth()*bitmap.getHeight()<=maxPixels)return bitmap;
        double scale=Math.sqrt((double)Math.max(1,maxPixels)/((long)bitmap.getWidth()*bitmap.getHeight()));
        Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);if(scaled!=bitmap)bitmap.recycle();return scaled;
    }
    @Override public void close() { closed = true; synchronized(requests){for(HttpURLConnection http:requests)http.disconnect();requests.clear();} Engine engine = imageEngine; if (engine != null) engine.cancel();metadata=null; }
}
