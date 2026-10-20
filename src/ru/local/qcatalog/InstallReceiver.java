package ru.local.qcatalog;

import android.content.*;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id"); if (id == null) return;
        org.json.JSONObject job=QueueStore.get(context,id); if(job==null) return;
        if (!java.util.Arrays.asList("installing","preparing","downloaded","installError").contains(job.optString("state"))) return;
        if(intent.hasExtra("sessionId") && job.has("sessionId") && intent.getIntExtra("sessionId",-1)!=job.optInt("sessionId")) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            QueueStore.stage(context,id,"installing","Установка APK",0,0,"Подтверди установку в системном окне");
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                try { confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(confirmation); }
                catch (Exception e) { QueueStore.update(context, id, "downloaded", "Открой QCatalog и повтори установку для подтверждения."); }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            QueueStore.update(context, id, "needsData", "APK установлен. Устанавливаю файлы данных.");
            try { DownloadService.start(context); } catch (Exception ignored) { }
        } else {
            QueueStore.update(context, id, "installError", InstallChecks.failure(status,intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)));
        }
    }
}
