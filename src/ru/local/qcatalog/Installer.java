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
        String packageName, label, sha256;
        final LinkedHashMap<File,String> data = new LinkedHashMap<>();
        boolean needsApproval(JSONObject job) {
            return !packageName.equals(job.optString("pkg")) &&
                !(packageName.equals(job.optString("approvedPackage")) && sha256.equals(job.optString("approvedApkSha256")));
        }
    }
    private static List<File> files(File dir) throws IOException {
        List<File> result = new ArrayList<>(); File[] entries = dir.listFiles(); if (entries == null) throw new IOException("Не удалось прочитать папку релиза.");
        for (File f : entries) {
            if (Files.isSymbolicLink(f.toPath())) throw new IOException("Ссылки в релизах не поддерживаются.");
            if (f.isDirectory()) result.addAll(files(f)); else result.add(f);
        }
        return result;
    }
    static Bundle inspect(Context c, JSONObject job) throws Exception {
        String pkg = job.getString("pkg"); if (!Catalog.safePackage(pkg)) throw new IOException("Некорректное имя пакета.");
        File dir = QueueStore.directory(c, job); Bundle result = new Bundle(); Set<String> destinations = new HashSet<>();
        for (File f : files(dir)) {
            String relative = dir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            if (f.getName().equalsIgnoreCase("install.txt") && f.length() > 0) throw new IOException("Релиз содержит install.txt. Произвольные скрипты установки в этой версии не выполняются.");
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
            } else if (f.getName().endsWith(".obb")) throw new IOException("Не удалось определить место установки OBB-файла.");
        }
        if (result.apk == null) throw new IOException("В загруженном релизе нет APK.");
        PackageInfo apkInfo = c.getPackageManager().getPackageArchiveInfo(result.apk.getAbsolutePath(), 0);
        if (apkInfo == null) throw new IOException("Не удалось прочитать APK. Файл повреждён или загрузка не завершена.");
        if (!Catalog.safePackage(apkInfo.packageName)) throw new IOException("APK содержит некорректное имя пакета.");
        result.packageName = apkInfo.packageName;
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
    static void install(Context c, JSONObject job) throws Exception {
        Bundle bundle = inspect(c, job);
        if (bundle.needsApproval(job)) throw new IOException("Перед установкой подтверди расхождение имени пакета с каталогом.");
        if (!c.getPackageManager().canRequestPackageInstalls()) throw new IOException("Разреши QCatalog устанавливать приложения в системных настройках.");
        if (!bundle.data.isEmpty() && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager())
            throw new IOException("Для OBB требуется разрешение «Доступ ко всем файлам». Оно запрашивается в настройках приложения.");
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(bundle.packageName); params.setSize(bundle.apk.length());
        int sessionId = installer.createSession(params); boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(bundle.apk); OutputStream output = session.openWrite("base.apk", 0, bundle.apk.length())) {
                byte[] buffer = new byte[1024 * 1024]; int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                session.fsync(output);
            }
            Intent result = new Intent(c, InstallReceiver.class).setAction("ru.local.qcatalog.INSTALL_RESULT").putExtra("id", job.getString("id"));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent callback = PendingIntent.getBroadcast(c, sessionId, result, flags);
            QueueStore.update(c, job.getString("id"), "installing", "Ожидаю результат системного установщика.");
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
        File root = new File(Environment.getExternalStorageDirectory(), "Android/obb/" + pkg);
        long bytes = 0; for (File file : bundle.data.keySet()) bytes = Math.addExact(bytes, file.length());
        if (Environment.getExternalStorageDirectory().getUsableSpace() < bytes + 64L * 1024 * 1024) throw new IOException("Недостаточно места для файлов данных.");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Прошивка не разрешила запись в Android/obb. APK установлен, данные ещё не установлены.");
        for (Map.Entry<File,String> entry : bundle.data.entrySet()) {
            File target = new File(root, entry.getValue());
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) throw new IOException("Недопустимый путь OBB.");
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Не удалось создать папку OBB.");
            File pending = new File(target.getParentFile(), target.getName() + ".qcatalog-part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(entry.getKey()); FileOutputStream out = new FileOutputStream(pending)) {
                byte[] buffer = new byte[1024 * 1024]; int n;
                while ((n = in.read(buffer)) != -1) { digest.update(buffer, 0, n); out.write(buffer, 0, n); }
                out.getFD().sync();
            } catch (IOException e) { throw new IOException("Запись данных не удалась. Проверь место и доступ к Android/obb."); }
            byte[] expected = digest.digest(); digest.reset();
            try (InputStream in = new FileInputStream(pending)) { byte[] buffer = new byte[1024 * 1024]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n); }
            if (!MessageDigest.isEqual(expected, digest.digest())) throw new IOException("Проверка записанного OBB не прошла.");
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
