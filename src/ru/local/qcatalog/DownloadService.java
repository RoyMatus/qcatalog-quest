package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadService extends Service {
    static final AtomicBoolean RUNNING = new AtomicBoolean();
    private volatile Engine active;
    private volatile String activeId;
    private volatile boolean stopping;
    private PowerManager.WakeLock wakeLock;
    static void start(Context c) { c.startForegroundService(new Intent(c, DownloadService.class)); }
    static void pause(Context c, String id) { c.startService(new Intent(c, DownloadService.class).setAction("pause").putExtra("id", id)); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() { super.onCreate(); if (!RUNNING.get()) QueueStore.recover(this); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "pause".equals(intent.getAction())) {
            String id = intent.getStringExtra("id");
            QueueStore.update(this, id, "paused", "Загрузка приостановлена.");
            if (id != null && id.equals(activeId) && active != null) active.cancel();
            if (!RUNNING.get()) stopSelf();
            return START_NOT_STICKY;
        }
        notification("Готовлю загрузку");
        if (!RUNNING.compareAndSet(false, true)) return START_REDELIVER_INTENT;
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QCatalog:download");
        wakeLock.acquire(6 * 60 * 60 * 1000L);
        new Thread(this::work, "qcatalog-download").start(); return START_REDELIVER_INTENT;
    }
    private void notification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("downloads", "Загрузки QCatalog", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(1, new Notification.Builder(this, "downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("QCatalog").setContentText(text).setContentIntent(open).setOngoing(true).build());
    }
    private void work() {
        try {
            while (!stopping) {
                JSONObject job = null;
                for (JSONObject candidate : QueueStore.all(this)) {
                    if (candidate.optString("state").equals("queued") || candidate.optString("state").equals("needsData")) { job = candidate; break; }
                }
                if (job == null) break;
                activeId = job.getString("id");
                final String id = activeId;
                if (job.optString("state").equals("needsData")) {
                    notification("Устанавливаю файлы данных: " + job.getString("name"));
                    try { Installer.finishData(this, job); QueueStore.update(this, id, "installed", "APK и файлы данных установлены."); }
                    catch (Exception e) { QueueStore.update(this, id, "dataError", safeMessage(e)); }
                    continue;
                }
                QueueStore.update(this, id, "downloading", "Подключаюсь к серверу");
                notification(job.getString("name")); active = new Engine(this);
                try {
                    String release = job.getString("release"); if (!Catalog.safeRelease(release)) throw new IOException("Некорректное имя релиза.");
                    File dir = QueueStore.directory(this, job);
                    List<String> mirrors = active.mirrors();
                    String preferred = getSharedPreferences("state", 0).getString("mirror", "");
                    if (mirrors.remove(preferred)) mirrors.add(0, preferred);
                    Exception last = null; boolean downloaded = false;
                    for (String mirror : mirrors) {
                        if (QueueStore.get(this, id).optString("state").equals("paused")) throw new InterruptedIOException("Пауза.");
                        try {
                            String remote = mirror + ":Quest Games/" + release;
                            active.downloadRelease(remote,dir,
                                message -> { if (!active.cancelled.get()) QueueStore.progress(this, id, message); });
                            downloaded = true; break;
                        } catch (InterruptedIOException e) { throw e; }
                        catch (Exception e) { last = e; if (e.getMessage() != null && e.getMessage().startsWith("Нужно около")) throw e; }
                    }
                    if (!downloaded) throw last == null ? new IOException("Не удалось скачать релиз.") : last;
                    Installer.Bundle bundle = Installer.inspect(this, job);
                    QueueStore.update(this, id, "downloaded", bundle.needsApproval(job) ? "Скачано. Имя пакета отличается от каталога — при установке покажу подробности." : "Скачано. Нажми «Установить» и подтверди системный запрос.");
                } catch (InterruptedIOException e) {
                    JSONObject current=QueueStore.get(this,id); boolean manual=current != null && current.optString("state").equals("paused");
                    QueueStore.update(this,id,manual ? "paused" : "queued",manual ? "Загрузка приостановлена; позиция сохранена." : "Загрузка прервалась; продолжу с сохранённой позиции.");
                }
                catch (Exception e) { QueueStore.update(this, id, "error", safeMessage(e)); }
                finally { active = null; activeId = null; }
            }
        } catch (Exception ignored) {
            if (activeId != null) QueueStore.update(this, activeId, "error", "Не удалось завершить задачу. Можно повторить.");
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            new Handler(Looper.getMainLooper()).post(() -> {
                RUNNING.set(false);
                if (!stopping) for (JSONObject pending : QueueStore.all(this)) if (Arrays.asList("queued","needsData").contains(pending.optString("state"))) {
                    start(this); return;
                }
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            });
        }
    }
    static String safeMessage(Exception e) {
        String message = e.getMessage(); if (message == null) return "Ошибка: " + e.getClass().getSimpleName();
        return message.replaceAll("https?://\\S+", "[адрес скрыт]");
    }
    @Override public void onDestroy() { stopping=true; if (active != null) active.cancel(); super.onDestroy(); }
}
