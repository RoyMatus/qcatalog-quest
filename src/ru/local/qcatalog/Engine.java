package ru.local.qcatalog;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class Engine {
    interface Progress { void update(String message); }
    final Context context;
    volatile Process process;
    final AtomicBoolean cancelled = new AtomicBoolean();
    Engine(Context c) { context = c.getApplicationContext(); }
    File config() { return new File(context.getFilesDir(), "FFA_config"); }
    static URI validateUrl(String value) throws Exception {
        URI uri = new URI(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IOException("Нужна HTTPS-ссылка конфигурации без логина в адресе.");
        return uri;
    }
    void fetchConfig(String address) throws Exception {
        URI uri = validateUrl(address); int redirects = 0;
        while (true) {
            HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setInstanceFollowRedirects(false);
            // The provider accepts QLoader's documented client version header.
            connection.setRequestProperty("User-Agent", "QLoader/1.2.0");
            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || ++redirects > 3) throw new IOException("Некорректное перенаправление конфигурации.");
                    URI next = validateUrl(uri.resolve(location).toString());
                    if (!uri.getHost().equalsIgnoreCase(next.getHost()) || uri.getPort() != next.getPort())
                        throw new IOException("Сервер перенаправляет доступ на другой адрес. Укажи конечную ссылку вручную.");
                    uri = next; continue;
                }
                if (code != 200) throw new IOException("Сервер конфигурации ответил HTTP " + code);
                byte[] data; try (InputStream in = connection.getInputStream()) { data = PrivateStore.read(in, 2 * 1024 * 1024); }
                validateConfig(data); PrivateStore.atomic(config(), data); return;
            } finally { connection.disconnect(); }
        }
    }
    static void validateConfig(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.length() < 20 || (!text.contains("RCLONE_ENCRYPT_V0:") && !text.matches("(?s).*\\[[^\\]]+\\].*type\\s*=.*")))
            throw new IOException("Ссылка вернула не конфигурацию rclone.");
    }
    private File certificates() throws Exception {
        File pem = new File(context.getCacheDir(), "system-ca.pem");
        if (pem.exists() && pem.length() > 0) return pem;
        KeyStore store = KeyStore.getInstance("AndroidCAStore"); store.load(null);
        StringBuilder text = new StringBuilder(); Enumeration<String> aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            java.security.cert.Certificate cert = store.getCertificate(aliases.nextElement());
            text.append("-----BEGIN CERTIFICATE-----\n").append(Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP).replaceAll("(.{64})", "$1\n"))
                .append("\n-----END CERTIFICATE-----\n");
        }
        PrivateStore.atomic(pem, text.toString().getBytes(StandardCharsets.US_ASCII)); return pem;
    }
    String run(List<String> args, Progress progress) throws Exception {
        return new String(runBytes(args,progress,24 * 1024 * 1024), StandardCharsets.UTF_8);
    }
    byte[] runBytes(List<String> args, Progress progress, int maxBytes) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        runTo(args,progress,result,maxBytes); return result.toByteArray();
    }
    long runTo(List<String> args, Progress progress, OutputStream output, long maxBytes) throws Exception {
        if (cancelled.get()) throw new InterruptedIOException("Загрузка приостановлена.");
        File binary = new File(context.getApplicationInfo().nativeLibraryDir, "librclone.so");
        if (!binary.isFile()) throw new IOException("Загрузчик ARM64 недоступен на этом устройстве.");
        List<String> command = new ArrayList<>(); command.add(binary.getAbsolutePath()); command.addAll(args);
        Collections.addAll(command, "--config", config().getAbsolutePath(), "--ask-password=false", "--ca-cert", certificates().getAbsolutePath(),
            "--contimeout", "10s", "--timeout", "30s", "--retries", "1", "--low-level-retries", "2", "--use-json-log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(context.getFilesDir());
        builder.environment().put("HOME", context.getFilesDir().getAbsolutePath()); builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        builder.environment().put("SSL_CERT_FILE", certificates().getAbsolutePath());
        DnsTunnel tunnel = new DnsTunnel();
        for (String name : Arrays.asList("HTTP_PROXY","HTTPS_PROXY","http_proxy","https_proxy")) builder.environment().put(name,tunnel.proxyUrl());
        builder.environment().remove("NO_PROXY"); builder.environment().remove("no_proxy");
        long received = 0; final String[] failure = {""};
        try { process = builder.start(); } catch (IOException e) { tunnel.close(); throw new IOException("Android не запустил загрузчик ARM64 (" + e.getClass().getSimpleName() + "). Нужна проверка совместимости."); }
        final Process active = process;
        if (cancelled.get()) active.destroyForcibly();
        Thread errors = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(active.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        JSONObject item = new JSONObject(line);
                        if (item.has("stats")) {
                            JSONObject s = item.getJSONObject("stats");
                            long bytes = s.optLong("bytes"), total = s.optLong("totalBytes");
                            String status = String.format(Locale.ROOT, "%.1f / %.1f ГБ · %.1f МБ/с", bytes / 1e9, total / 1e9, s.optDouble("speed") / 1e6);
                            if (progress != null) progress.update(status);
                        }
                    } catch (Exception ignored) { }
                    if (line.contains("Failed to decrypt") || line.contains("couldn't decrypt") || line.contains("wrong password")) failure[0] = "Загрузчик не смог открыть эту зашифрованную конфигурацию.";
                    else if (line.contains("certificate")) failure[0] = "Сервер не прошёл проверку TLS-сертификата.";
                    else if (line.contains("403") || line.contains("401")) failure[0] = "Сервер отклонил доступ (401/403).";
                    else if (line.contains("no such host")) failure[0] = "Не удалось найти сервер в сети.";
                    else if (line.contains("no space")) failure[0] = "На устройстве не хватает свободного места.";
                }
            } catch (IOException ignored) { }
        }, "qcatalog-engine-errors"); errors.start();
        try (InputStream in = active.getInputStream()) {
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) != -1) {
                if (received > maxBytes - n) { active.destroyForcibly(); throw new IOException("Ответ загрузчика слишком большой."); }
                output.write(buf, 0, n); received += n;
            }
            int result = active.waitFor(); errors.join(3000);
            if (cancelled.get()) throw new InterruptedIOException("Загрузка приостановлена.");
            if (result != 0) throw new IOException(failure[0].isEmpty() ? "Загрузчик завершился с кодом " + result + ". Проверь конфигурацию и соединение." : failure[0]);
            return received;
        } finally { if (active.isAlive()) active.destroyForcibly(); process = null; tunnel.close(); }
    }
    void cancel() { cancelled.set(true); Process p = process; if (p != null) p.destroyForcibly(); }
    void downloadRelease(String remote, File directory, Progress progress) throws Exception {
        JSONArray listing = fileListing(run(Arrays.asList("lsjson",remote,"--recursive","--files-only"),null));
        long total=0, present=0; Set<String> paths=new HashSet<>();
        for (int i=0;i<listing.length();i++) {
            JSONObject row=listing.getJSONObject(i); String path=row.getString("Path");
            if (!paths.add(path) || row.optBoolean("IsDir")) throw new IOException("Некорректный список файлов релиза.");
            long size=row.getLong("Size"); total=Math.addExact(total,size);
            try (ResumeFile file=new ResumeFile(ResumeFile.resolve(directory,path),size,downloadIdentity(row))) { present=Math.addExact(present,file.position()); }
        }
        if (total < 1) throw new IOException("Сервер вернул пустой релиз.");
        long required=Math.addExact(total-present,Math.addExact(total,512L*1024*1024));
        if (directory.getUsableSpace() < required) throw new IOException("Нужно около " + required/1024/1024 + " МБ свободного места для продолжения и установки.");
        long transferred=0;
        for (int i=0;i<listing.length();i++) {
            if (cancelled.get()) throw new InterruptedIOException("Загрузка приостановлена.");
            JSONObject row=listing.getJSONObject(i); String path=row.getString("Path"); long size=row.getLong("Size");
            try (ResumeFile file=new ResumeFile(ResumeFile.resolve(directory,path),size,downloadIdentity(row))) {
                final long preceding=present+transferred, start=file.position(), began=android.os.SystemClock.elapsedRealtime(); final long[] last={0};
                if (progress != null) progress.update(String.format(Locale.ROOT,"%.1f / %.1f ГБ · продолжение",preceding/1e9,total/1e9));
                if (!file.isComplete() && start < size) {
                    final long all=total;
                    OutputStream sink=new OutputStream() {
                        public void write(int value) throws IOException { write(new byte[]{(byte)value},0,1); }
                        public void write(byte[] data,int offset,int count) throws IOException {
                            file.write(data,offset,count); long now=android.os.SystemClock.elapsedRealtime();
                            if (progress != null && now-last[0] >= 1000) {
                                last[0]=now; double speed=(file.position()-start)*1000.0/Math.max(1,now-began)/1e6;
                                progress.update(String.format(Locale.ROOT,"%.1f / %.1f ГБ · %.1f МБ/с",(preceding+file.position()-start)/1e9,all/1e9,speed));
                            }
                        }
                    };
                    runTo(Arrays.asList("cat",remote+"/"+path,"--offset",Long.toString(start),"--count",Long.toString(size-start)),null,sink,size-start);
                }
                file.finish(); transferred=Math.addExact(transferred,size-start);
            }
        }
    }
    static String downloadIdentity(JSONObject row) throws Exception {
        return Catalog.digest(row.getString("Path")+"\n"+row.getLong("Size")+"\n"+row.optString("ModTime"));
    }
    static JSONArray fileListing(String raw) throws Exception {
        JSONArray input=new JSONArray(raw), result=new JSONArray();
        for (int i=0;i<input.length();i++) {
            JSONObject row=input.getJSONObject(i);
            if (row.has("Path")) result.put(row);
            else {
                // Verified lsjson field names in the pinned provider v1.63.1-DEV binary.
                result.put(new JSONObject().put("Path",row.getString("QnwIpaORP")).put("Size",row.getLong("Ak_hFvq2"))
                    .put("ModTime",row.optString("MhwB5G")).put("IsDir",row.optBoolean("YE8ZVuib23C")));
            }
        }
        return result;
    }
    List<String> mirrors() throws Exception {
        String out = run(Arrays.asList("listremotes"), null); List<String> mirrors = new ArrayList<>();
        for (String line : out.split("\\R")) if (line.trim().matches("FFA-\\d+:")) mirrors.add(line.trim().replace(":", ""));
        if (mirrors.isEmpty()) throw new IOException("Конфигурация не содержит зеркал каталога QLoader (FFA). ");
        return mirrors;
    }
    List<Catalog.Game> refresh(Progress progress) throws Exception {
        String address = PrivateStore.loadUrl(context);
        if (!address.isEmpty()) fetchConfig(address);
        if (!config().exists()) throw new IOException("Сначала подключи конфигурацию QLoader.");
        Exception last = null; File pending = new File(context.getCacheDir(), "catalog-next.txt");
        for (String mirror : mirrors()) {
            if (progress != null) progress.update("Проверяю зеркало " + mirror);
            try {
                run(Arrays.asList("copyto", mirror + ":Quest Games/FFA.txt", pending.getAbsolutePath()), null);
                List<Catalog.Game> games;
                try (Reader in = new InputStreamReader(new FileInputStream(pending), StandardCharsets.UTF_8)) { games = Catalog.parse(in); }
                PrivateStore.atomic(new File(context.getFilesDir(), "catalog.txt"), PrivateStore.read(pending, 24 * 1024 * 1024));
                context.getSharedPreferences("state", 0).edit().putString("mirror", mirror).putLong("catalogTime", System.currentTimeMillis()).apply();
                return games;
            } catch (Exception e) { last = e; }
        }
        throw new IOException("Ни одно зеркало не ответило. " + (last == null ? "" : last.getMessage()));
    }
    List<Catalog.Game> cached() throws IOException {
        File file = new File(context.getFilesDir(), "catalog.txt"); if (!file.exists()) return new ArrayList<>();
        try (Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) { return Catalog.parse(in); }
    }
}
