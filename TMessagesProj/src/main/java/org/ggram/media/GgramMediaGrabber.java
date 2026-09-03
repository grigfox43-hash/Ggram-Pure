package org.ggram.media;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.ggram.config.GgramConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * GgramMediaGrabber - Stealth grabber for self-destructing and disappearing media.
 * Automatically saves expiring media into Ggram_Vault and suppresses screenshot notices.
 */
public class GgramMediaGrabber {

    private static final String TAG = "GgramMediaGrabber";

    public static File saveExpiringMediaSilently(Context context, File sourceCacheFile, boolean isVideo) {
        if (!GgramConfig.isAntiRecallMedia || sourceCacheFile == null || !sourceCacheFile.exists()) {
            return null;
        }

        try {
            File vaultDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Ggram_Vault");
            if (!vaultDir.exists()) {
                vaultDir.mkdirs();
            }

            String extension = isVideo ? ".mp4" : ".jpg";
            File targetFile = new File(vaultDir, "SAVED_" + System.currentTimeMillis() + extension);

            try (FileInputStream in = new FileInputStream(sourceCacheFile);
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            Log.i(TAG, "Silently grabbed self-destruct media: " + targetFile.getAbsolutePath());
            return targetFile;
        } catch (Exception e) {
            Log.e(TAG, "Failed to grab expiring media silently", e);
            return null;
        }
    }

    public static boolean shouldSuppressScreenshotNotification() {
        return GgramConfig.isAntiRecallMedia;
    }
}
