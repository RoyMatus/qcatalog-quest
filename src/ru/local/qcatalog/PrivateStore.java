package ru.local.qcatalog;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class PrivateStore {
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("qcatalog-access")) {
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("qcatalog-access", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)store.getKey("qcatalog-access", null);
    }
    static void saveUrl(Context c, String url) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(cipher.getIV().length); out.write(cipher.getIV()); out.write(cipher.doFinal(url.getBytes(StandardCharsets.UTF_8)));
        atomic(new File(c.getFilesDir(), "access.bin"), out.toByteArray());
    }
    static String loadUrl(Context c) throws Exception {
        File file = new File(c.getFilesDir(), "access.bin"); if (!file.exists()) return "";
        byte[] data = read(file, 32768);
        // An empty URL is valid for file imports: 1 length byte + 12 IV bytes + 16 tag bytes.
        if (data.length < 29 || (data[0] & 255) != 12) throw new IOException("Повреждены сохранённые настройки доступа.");
        int n = data[0] & 255;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, data, 1, n));
        return new String(cipher.doFinal(data, 1 + n, data.length - n - 1), StandardCharsets.UTF_8);
    }
    static byte[] read(File file, int max) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return read(in, max); }
    }
    static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) != -1) { if (out.size() + n > max) throw new IOException("Слишком большой файл."); out.write(buf, 0, n); }
        return out.toByteArray();
    }
    static void atomic(File file, byte[] data) throws IOException {
        AtomicFile af = new AtomicFile(file); FileOutputStream out = null;
        try { out = af.startWrite(); out.write(data); af.finishWrite(out); }
        catch (IOException e) { if (out != null) af.failWrite(out); throw e; }
    }
}
