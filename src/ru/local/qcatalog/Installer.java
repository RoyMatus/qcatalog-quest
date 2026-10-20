package ru.local.qcatalog;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class Installer {
    static final class Bundle {
        File apk;
        PackageInfo apkInfo;
        String packageName, label, sha256;
        boolean sharedData, preservesOldData;
        final LinkedHashMap<File,String> data = new LinkedHashMap<>();
        boolean needsApproval(JSONObject job) {
            return !packageName.equals(job.optString("pkg")) &&
                !(packageName.equals(job.optString("approvedPackage")) && sha256.equals(job.optString("approvedApkSha256")));
        }
    }
    private static List<File> files(File dir) throws IOException {
        List<File> result = new ArrayList<>(); File[] entries = dir.listFiles(); if (entries == null) throw new IOException("Не удалось прочитать папку релиза.");
        for (File f : entries) {
            if (f.getName().equals(".qcatalog-expanded")) continue;
            if (Files.isSymbolicLink(f.toPath())) throw new IOException("Ссылки в релизах не поддерживаются.");
            if (f.isDirectory()) result.addAll(files(f)); else result.add(f);
        }
        return result;
    }
    static Bundle inspect(Context c, JSONObject job) throws Exception {
        String pkg = job.getString("pkg"); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя пакета.");
        File dir = QueueStore.directory(c, job); Bundle result = new Bundle(); Set<String> destinations = new HashSet<>();
        File script=null;List<File> archives=new ArrayList<>();boolean unmappedObb=false;
        for (File f : files(dir)) {
            if (f.getName().endsWith(".qcatalog-part") || f.getName().endsWith(".qcatalog-resume") || f.getName().endsWith(".qcatalog-resume.tmp")) throw new IOException("В релизе остались незавершённые файлы. Продолжи загрузку перед установкой.");
            String relative = dir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            if (f.getName().equalsIgnoreCase("install.txt") && f.length() > 0) {if(script!=null)throw new IOException("В релизе несколько install.txt.");script=f;continue;}
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".7z")) archives.add(f);
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                if (result.apk != null) throw new IOException("В релизе несколько APK. Такие комплекты пока не поддерживаются.");
                result.apk = f; continue;
            }
            String mapped = null;
            for (String prefix : Arrays.asList(pkg + "/", "Android/obb/" + pkg + "/", "obb/" + pkg + "/")) if (relative.startsWith(prefix)) mapped = relative.substring(prefix.length());
            if (mapped == null && !relative.contains("/") && f.getName().matches("(main|patch)\\.\\d+\\." + java.util.regex.Pattern.quote(pkg) + "\\.obb")) mapped = f.getName();
            if (mapped != null) {
                if (mapped.isEmpty() || Arrays.asList(mapped.split("/")).contains("..") || !destinations.add(mapped)) throw new IOException("Некорректные пути дополнительных файлов.");
                result.data.put(f, mapped);
            } else if (f.getName().endsWith(".obb")) unmappedObb=true;
        }
        if(script!=null) {
            List<File> roots=new ArrayList<>();roots.add(script.getParentFile());
            for(File archive:archives)roots.add(ArchiveFiles.expand(archive,dir,(done,total)->{
                JSONObject current=QueueStore.get(c,job.optString("id"));
                if(current!=null&&current.optString("state").equals("paused"))throw new InterruptedIOException("Распаковка приостановлена. Скачанный архив сохранён.");
                if(current!=null)QueueStore.stage(c,job.optString("id"),current.optString("state").equals("downloading")?"downloading":"preparing","Распаковка 7z",done,total,"Распаковка: "+DownloadFiles.format(done)+" / "+DownloadFiles.format(total));
            }));
            InstallPlan plan=InstallPlan.read(script,roots,pkg);result.apk=plan.apk;result.data.clear();result.data.putAll(plan.files);result.sharedData=true;result.preservesOldData=plan.preservesOldData;
        }else if(unmappedObb)throw new IOException("Не удалось определить место установки OBB-файла.");
        if (result.apk == null) throw new IOException("В загруженном релизе нет APK.");
        PackageInfo apkInfo = c.getPackageManager().getPackageArchiveInfo(result.apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (apkInfo == null) throw new IOException("Не удалось прочитать APK. Файл повреждён или загрузка не завершена.");
        if (!Catalog.safePackage(apkInfo.packageName)) throw new IOException("APK содержит некорректное имя пакета.");
        result.packageName = apkInfo.packageName;
        result.apkInfo = apkInfo;
        apkInfo.applicationInfo.sourceDir = result.apk.getAbsolutePath();
        apkInfo.applicationInfo.publicSourceDir = result.apk.getAbsolutePath();
        result.label = apkInfo.applicationInfo.loadLabel(c.getPackageManager()).toString();
        if (apkInfo.getLongVersionCode() != job.getLong("version")) throw new IOException("Версия APK не совпадает с каталогом. Обнови каталог перед установкой.");
        if (!pkg.equals(result.packageName)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(result.apk)) {
                byte[] buffer = new byte[1024 * 1024]; int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            result.sha256 = hex.toString();
        }
        return result;
    }
    static void recoverLegacyScripts(Context c) {
        for(JSONObject job:QueueStore.all(c))if(job.optString("state").equals("error")&&job.optString("message").contains("install.txt")&&!job.optBoolean("filesRemoved"))try{
            File dir=QueueStore.directory(c,job);File script=InstallPlan.lookup(dir,"install.txt");
            if(script!=null&&script.isFile())QueueStore.update(c,job.optString("id"),"downloaded","Файлы сохранены. Нажми «Установить»: архив будет распакован на шлеме. Старые данные не удаляются.");
        }catch(Exception ignored){}
    }
    static void checkUpdateSignature(Context c, Bundle bundle) throws IOException {
        SigningPreflight.check(c,bundle.apkInfo);
    }
    static void install(Context c, JSONObject job) throws Exception {
        Bundle bundle = inspect(c, job);
        checkUpdateSignature(c,bundle);
        if (bundle.needsApproval(job)) throw new IOException("Перед установкой подтверди расхождение имени пакета с каталогом.");
        if (!c.getPackageManager().canRequestPackageInstalls()) throw new IOException("Разреши QCatalog устанавливать приложения в системных настройках.");
        if (!bundle.data.isEmpty() && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager())
            throw new IOException("Для данных игры требуется разрешение «Доступ ко всем файлам». Оно запрашивается в настройках приложения.");
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(bundle.packageName); params.setSize(bundle.apk.length());
        int sessionId = installer.createSession(params); boolean committed = false;
        QueueStore.session(c,job.getString("id"),sessionId);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            final long total=bundle.apk.length(); long done=0,last=0;
            QueueStore.stage(c,job.getString("id"),"preparing","Подготовка APK",0,total,"Передаю APK системному установщику");
            try (InputStream input = new FileInputStream(bundle.apk); OutputStream output = session.openWrite("base.apk", 0, bundle.apk.length())) {
                byte[] buffer = new byte[1024 * 1024]; int n;
                while ((n = input.read(buffer)) != -1) { output.write(buffer, 0, n); done+=n; long now=SystemClock.elapsedRealtime();
                    if(now-last>250 || done==total) { last=now; QueueStore.stage(c,job.getString("id"),"preparing","Подготовка APK",done,total,"Передаю APK системному установщику"); }
                }
                session.fsync(output);
            }
            Intent result = new Intent(c, InstallReceiver.class).setAction("ru.local.qcatalog.INSTALL_RESULT").putExtra("id", job.getString("id")).putExtra("sessionId",sessionId);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent callback = PendingIntent.getBroadcast(c, sessionId, result, flags);
            QueueStore.stage(c,job.getString("id"),"installing","Установка APK",0,0,"Ожидаем результат Android");
            session.commit(callback.getIntentSender()); committed = true;
        } finally { if (!committed) installer.abandonSession(sessionId); }
    }
    static void finishData(Context c, JSONObject job) throws Exception {
        Bundle bundle = inspect(c, job); String pkg = bundle.packageName;
        if (bundle.needsApproval(job)) throw new IOException("Скачанный APK изменился. Нужно повторно проверить и подтвердить установку.");
        PackageInfo installed = c.getPackageManager().getPackageInfo(pkg, 0);
        if (installed.getLongVersionCode() != job.getLong("version")) throw new IOException("Android не подтвердил установку нужной версии APK.");
        if (bundle.data.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) throw new IOException("Разреши доступ ко всем файлам и повтори установку данных.");
        File root = bundle.sharedData ? Environment.getExternalStorageDirectory() : new File(Environment.getExternalStorageDirectory(), "Android/obb/" + pkg);
        long bytes = 0; for (File file : bundle.data.keySet()) bytes = Math.addExact(bytes, file.length());
        if (Environment.getExternalStorageDirectory().getUsableSpace() < bytes + 64L * 1024 * 1024) throw new IOException("Недостаточно места для файлов данных.");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Прошивка не разрешила запись в Android/obb. APK установлен, данные ещё не установлены.");
        DataFiles.copy(bundle.data,root,(verifying,done,total) -> QueueStore.stage(c,job.optString("id"),verifying ? "verifying" : "copying",verifying ? "Проверка данных" : "Копирование данных",done,total,DownloadFiles.format(done)+" / "+DownloadFiles.format(total)));
    }
}
