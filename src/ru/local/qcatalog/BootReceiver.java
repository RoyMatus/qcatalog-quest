package ru.local.qcatalog;

import android.content.*;
import org.json.JSONObject;
import java.util.Arrays;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            if (DownloadService.RUNNING.get()) return;
            QueueStore.recover(context);
            for (JSONObject job : QueueStore.all(context)) if (Arrays.asList("queued","needsData").contains(job.optString("state"))) {
                DownloadService.start(context); break;
            }
        } catch (Exception e) { context.getSharedPreferences("state",0).edit().putString("lastError",DownloadService.safeMessage(e)).apply(); }
    }
}
