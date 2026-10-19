package ru.local.qcatalog;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class QueueStore {
    private static final java.util.concurrent.atomic.AtomicBoolean RECOVERED = new java.util.concurrent.atomic.AtomicBoolean();
    static void recover(Context c) { if (RECOVERED.compareAndSet(false,true)) interrupted(c); }
    static synchronized List<JSONObject> all(Context c) {
        List<JSONObject> result = new ArrayList<>(); File file = new File(c.getFilesDir(), "queue.json");
        if (!file.exists()) return result;
        try { JSONArray array = new JSONArray(new String(PrivateStore.read(file, 4 * 1024 * 1024), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) result.add(array.getJSONObject(i));
        } catch (Exception e) { throw new IllegalStateException("Не удалось прочитать очередь загрузок."); }
        return result;
    }
    static synchronized JSONObject get(Context c, String id) {
        for (JSONObject row : all(c)) if (row.optString("id").equals(id)) return row;
        return null;
    }
    private static void write(Context c, List<JSONObject> rows) throws Exception {
        JSONArray array = new JSONArray(); for (JSONObject row : rows) array.put(row);
        PrivateStore.atomic(new File(c.getFilesDir(), "queue.json"), array.toString().getBytes(StandardCharsets.UTF_8));
    }
    static synchronized void add(Context c, Catalog.Game game) throws Exception {
        List<JSONObject> rows = all(c);
        for (JSONObject row : rows) if (row.optString("id").equals(game.id)) {
            if (Arrays.asList("downloading", "installing", "needsData").contains(row.optString("state"))) return;
            if (Arrays.asList("downloaded","installError").contains(row.optString("state")) && !row.optBoolean("filesRemoved")) return;
            row.put("state", "queued").put("message", "В очереди").remove("filesRemoved"); write(c, rows); return;
        }
        rows.add(new JSONObject().put("id", game.id).put("name", game.name).put("release", game.release).put("pkg", game.pkg)
            .put("version", game.version).put("mb", game.megabytes).put("state", "queued").put("message", "В очереди"));
        write(c, rows);
    }
    static synchronized void update(Context c, String id, String state, String message) {
        try { List<JSONObject> rows = all(c); for (JSONObject row : rows) if (row.optString("id").equals(id)) {
            row.put("state", state).put("message", message); break;
        } write(c, rows); } catch (Exception e) { throw new IllegalStateException("Не удалось сохранить очередь."); }
    }
    static synchronized void progress(Context c, String id, String message) {
        JSONObject current=get(c,id);
        if (current != null && current.optString("state").equals("downloading")) update(c,id,"downloading",message);
    }
    static synchronized void approvePackage(Context c, String id, String pkg, String sha256) throws Exception {
        if (!Catalog.safePackage(pkg) || !sha256.matches("[a-f0-9]{64}")) throw new IOException("Некорректные сведения APK.");
        List<JSONObject> rows = all(c);
        for (JSONObject row : rows) if (row.optString("id").equals(id)) {
            row.put("approvedPackage", pkg).put("approvedApkSha256", sha256); write(c, rows);
            c.getSharedPreferences("package-aliases",0).edit().putString(row.getString("pkg"),pkg).apply(); return;
        }
        throw new IOException("Загрузка не найдена.");
    }
    static String installedPackage(JSONObject job) { return job.optString("approvedPackage", job.optString("pkg")); }
    static boolean active(JSONObject row) { return Arrays.asList("queued", "downloading", "installing", "needsData").contains(row.optString("state")); }
    static synchronized void ensureIdle(Context c) throws IOException {
        if (DownloadService.RUNNING.get()) throw new IOException("Дождись завершения загрузки или поставь её на паузу.");
        for (JSONObject row : all(c)) if (active(row)) throw new IOException("Дождись завершения активных задач.");
    }
    static synchronized int clearHistory(Context c, String onlyId) throws Exception {
        ensureIdle(c); List<JSONObject> rows = all(c); int count = rows.size();
        rows.removeIf(row -> onlyId == null || row.optString("id").equals(onlyId));
        write(c,rows); return count - rows.size();
    }
    static synchronized long deleteFiles(Context c, String onlyId) throws Exception {
        ensureIdle(c); File base = base(c); long deleted = 0;
        List<String> ids = onlyId == null ? DownloadFiles.releases(base) : Collections.singletonList(onlyId);
        List<JSONObject> rows = all(c);
        for (String id : ids) {
            try { deleted = Math.addExact(deleted, DownloadFiles.delete(base,id)); }
            catch (IOException e) {
                for (JSONObject row : rows) if (row.optString("id").equals(id)) row.put("state","error").put("message","Не все скачанные файлы удалены. Повтори очистку.");
                write(c,rows); throw e;
            }
            for (JSONObject row : rows) if (row.optString("id").equals(id)) {
                boolean installed = false;
                try { installed = c.getPackageManager().getPackageInfo(installedPackage(row),0).getLongVersionCode() >= row.optLong("version"); } catch (Exception ignored) { }
                row.put("filesRemoved",true).put("state", installed ? "installed" : "removed").put("message", "Скачанные файлы удалены.");
                row.remove("approvedApkSha256");
            }
            write(c,rows);
        }
        return deleted;
    }
    static synchronized void interrupted(Context c) {
        for (JSONObject row : all(c)) {
            String state = row.optString("state");
            if (state.equals("downloading")) update(c, row.optString("id"), "queued", "Продолжу с сохранённой позиции.");
            if (state.equals("installing")) {
                try { android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(installedPackage(row), 0);
                    if (p.getLongVersionCode() == row.optLong("version")) { update(c, row.optString("id"), "dataError", "APK установлен. Проверь файлы данных."); continue; }
                } catch (Exception ignored) { }
                update(c, row.optString("id"), "downloaded", "Проверь результат установки; можно повторить.");
            }
        }
    }
    static File base(Context c) {
        File root = c.getExternalFilesDir("games"); return root == null ? new File(c.getFilesDir(),"games") : root;
    }
    static File directory(Context c, JSONObject job) throws Exception {
        String id = job.getString("id"); if (!id.matches("[a-f0-9]{64}")) throw new IOException("Некорректный идентификатор загрузки.");
        File dir = DownloadFiles.path(base(c),id); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Не удалось создать папку загрузки.");
        return dir;
    }
    static String label(String state) {
        switch (state) {
            case "queued": return "В очереди";
            case "downloading": return "Скачивается";
            case "paused": return "Пауза";
            case "downloaded": return "Готово к установке";
            case "installing": return "Установка APK";
            case "installError": return "Ошибка установки";
            case "needsData": return "Установка данных";
            case "dataError": return "APK установлен, данные не завершены";
            case "installed": return "Установлено";
            case "removed": return "Файлы удалены";
            default: return "Нужна проверка";
        }
    }
}
