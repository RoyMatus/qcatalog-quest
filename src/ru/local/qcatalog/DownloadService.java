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
    private PowerManager.WakeLock wakeLock;
    static void start(Context c) { c.startForegroundService(new Intent(c, DownloadService.class)); }
    static void pause(Context c, String id) { c.startService(new Intent(c, DownloadService.class).setAction("pause").putExtra("id", id)); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "pause".equals(intent.getAction())) {
            String id = intent.getStringExtra("id");
            QueueStore.update(this, id, "paused", "Загрузка приостановлена.");
            if (id != null && id.equals(activeId) && active != null) active.cancel();
            if (!RUNNING.get()) stopSelf();
            return START_NOT_STICKY;
        }
        notification("Готовлю загрузку");
        if (!RUNNING.compareAndSet(false, true)) return START_NOT_STICKY;
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QCatalog:download");
        wakeLock.acquire(6 * 60 * 60 * 1000L);
        new Thread(this::work, "qcatalog-download").start(); return START_NOT_STICKY;
    }
    private void notification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("downloads", "Загрузки QCatalog", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(1, new Notification.Builder(this, "downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("QCatalog").setContentText(text).setContentIntent(open).setOngoing(true).build());
    }
    private void work() {
        Set<String> attempted = new HashSet<>();
        try {
            while (true) {
                JSONObject job = null;
                for (JSONObject candidate : QueueStore.all(this)) {
                    if ((candidate.optString("state").equals("queued") || candidate.optString("state").equals("needsData")) && !attempted.contains(candidate.optString("id"))) { job = candidate; break; }
                }
                if (job == null) break;
                activeId = job.getString("id"); attempted.add(activeId);
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
                            JSONObject size = new JSONObject(active.run(Arrays.asList("size", remote, "--json"), null));
                            long total = size.getLong("bytes");
                            if (total < 1) throw new IOException("Сервер вернул пустой релиз.");
                            long reserve = Math.addExact(Math.multiplyExact(total, 2), 512L * 1024 * 1024);
                            if (dir.getUsableSpace() < reserve) throw new IOException("Нужно около " + (reserve / 1024 / 1024) + " МБ свободного места для загрузки и установки.");
                            active.run(Arrays.asList("copy", remote, dir.getAbsolutePath(), "--stats", "1s", "--stats-log-level", "NOTICE", "--log-level", "NOTICE", "--transfers", "2"),
                                message -> { if (!active.cancelled.get()) QueueStore.update(this, id, "downloading", message); });
                            downloaded = true; break;
                        } catch (InterruptedIOException e) { throw e; }
                        catch (Exception e) { last = e; if (e.getMessage() != null && e.getMessage().startsWith("Нужно около")) throw e; }
                    }
                    if (!downloaded) throw last == null ? new IOException("Не удалось скачать релиз.") : last;
                    Installer.Bundle bundle = Installer.inspect(this, job);
                    QueueStore.update(this, id, "downloaded", bundle.needsApproval(job) ? "Скачано. Имя пакета отличается от каталога — при установке покажу подробности." : "Скачано. Нажми «Установить» и подтверди системный запрос.");
                } catch (InterruptedIOException e) { QueueStore.update(this, id, "paused", "Загрузка приостановлена; скачанные файлы сохранены."); }
                catch (Exception e) { QueueStore.update(this, id, "error", safeMessage(e)); }
                finally { active = null; activeId = null; }
            }
        } catch (Exception ignored) {
            if (activeId != null) QueueStore.update(this, activeId, "error", "Не удалось завершить задачу. Можно повторить.");
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            RUNNING.set(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
    static String safeMessage(Exception e) {
        String message = e.getMessage(); if (message == null) return "Ошибка: " + e.getClass().getSimpleName();
        return message.replaceAll("https?://\\S+", "[адрес скрыт]");
    }
    @Override public void onDestroy() { if (active != null) active.cancel(); super.onDestroy(); }
}
