package com.nfsu2.androidevolved;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.os.Process;
import java.io.File;
import java.util.List;

/** Only disposable graphics caches in this application's private cache directory. */
final class GraphicsCache {
    // Keep in sync with configurePipelineCache() in main.cpp. Epoch 2 excludes
    // caches from the previous DXVK preparation; those files remain untouched.
    static final String DIRECTORY = "dxvk-v2";

    static void prompt(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("Setel ulang cache grafis")
                .setMessage("Sesi permainan saat ini akan ditutup. Shader akan dikompilasi ulang saat diputar; Permainan dan pengaturan dipertahankan.")
                .setNegativeButton("Membatalkan", null)
                .setPositiveButton("Mulai ulang", (dialog, which) -> reset(activity)).show();
    }

    private static void reset(Activity activity) {
        try {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
            if (manager == null) throw new IllegalStateException("Tidak dapat memeriksa proses permainan.");
            List<ActivityManager.RunningAppProcessInfo> running = manager.getRunningAppProcesses();
            if (running != null) for (ActivityManager.RunningAppProcessInfo process : running)
                if (process.uid == Process.myUid() && process.processName.equals(activity.getPackageName() + ":game"))
                    Process.killProcess(process.pid);
            File cache = new File(activity.getCacheDir(), DIRECTORY);
            if (cache.exists()) {
                File backup = new File(activity.getCacheDir(), DIRECTORY + "-reset-" + System.currentTimeMillis());
                if (!cache.isDirectory() || !cache.renameTo(backup))
                    throw new IllegalStateException("Tidak dapat menyetel ulang cache grafis.");
            }
            new AlertDialog.Builder(activity).setTitle("Cache grafis siap")
                    .setMessage("Boot berikutnya akan menghasilkan cache baru. Yang sebelumnya dicadangkan di cache pribadi aplikasi.")
                    .setPositiveButton("Menerima", null).show();
        } catch (RuntimeException error) {
            new AlertDialog.Builder(activity).setTitle("Cache grafis").setMessage(error.getMessage())
                    .setPositiveButton("Menerima", null).show();
        }
    }
}
