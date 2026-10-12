package com.ethanreilly.liftlog;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/** Runs about once a week, even when the app is closed, and copies the latest snapshot to the backup folder. */
public class BackupWorker extends Worker {
    private static final String NAME = "weekly-backup";

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BackupWriter.write(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            if (m.equals("no_folder") || m.equals("no_data")) return Result.success();
            return Result.retry();
        }
    }

    static void schedule(Context ctx, boolean on) {
        WorkManager wm = WorkManager.getInstance(ctx);
        if (!on) {
            wm.cancelUniqueWork(NAME);
            return;
        }
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(BackupWorker.class, 7, TimeUnit.DAYS)
            .setInitialDelay(7, TimeUnit.DAYS)
            .build();
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }
}
