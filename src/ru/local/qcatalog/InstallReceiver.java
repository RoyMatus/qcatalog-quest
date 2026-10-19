package ru.local.qcatalog;

import android.content.*;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id"); if (id == null || QueueStore.get(context, id) == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
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
