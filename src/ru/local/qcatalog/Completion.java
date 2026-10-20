package ru.local.qcatalog;

import android.content.Context;
import org.json.JSONObject;
import java.io.IOException;

/** Only a verified install may authorize deletion of its own staging directory. */
final class Completion {
    static void cleanup(Context c,JSONObject job) throws Exception {
        JSONObject current=QueueStore.get(c,job.getString("id"));
        if(current==null || !current.optBoolean("dataVerified") || !java.util.Arrays.asList("cleanup","cleanupError").contains(current.optString("state")))
            throw new IOException("Сначала нужно завершить установку и проверку данных.");
        long installed=c.getPackageManager().getPackageInfo(QueueStore.installedPackage(current),0).getLongVersionCode();
        if(installed < current.getLong("version")) throw new IOException("Установленная версия не подтверждена. Скачанные файлы сохранены.");
        QueueStore.update(c,current.getString("id"),"cleanup","Удаляем установочные файлы…");
        DownloadFiles.delete(QueueStore.base(c),current.getString("id"));
        QueueStore.completed(c,current.getString("id"));
    }
}
