package org.ggram.media;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GgramDownloadManager - Multi-threaded download accelerator for Ggram.
 * Splits large files (up to 4 GB) into 4-8 parallel chunks for maximum throughput.
 */
public class GgramDownloadManager {

    private static final String TAG = "GgramDownloadManager";

    public enum DownloadStatus {
        PENDING,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }

    public static class DownloadTask {
        public String id;
        public String url;
        public File destination;
        public long totalBytes;
        public AtomicLong downloadedBytes = new AtomicLong(0);
        public long speedBytesPerSec = 0;
        public DownloadStatus status = DownloadStatus.PENDING;
    }

    public interface DownloadListener {
        void onProgress(int progressPercent, String speedFormatted);
        void onComplete(File destination);
        void onError(Throwable t);
    }

    private static final ConcurrentHashMap<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void downloadFile(String fileUrl, File targetFile, int threadsCount, DownloadListener listener) {
        final int threads = Math.max(1, Math.min(8, threadsCount));
        executor.execute(() -> {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                long contentLength = conn.getContentLengthLong();
                conn.disconnect();

                DownloadTask task = new DownloadTask();
                task.id = targetFile.getName();
                task.url = fileUrl;
                task.destination = targetFile;
                task.totalBytes = contentLength;
                task.status = DownloadStatus.DOWNLOADING;
                activeTasks.put(task.id, task);

                long chunkSize = contentLength / threads;
                CountDownLatch latch = new CountDownLatch(threads);

                for (int i = 0; i < threads; i++) {
                    final int threadIdx = i;
                    final long start = threadIdx * chunkSize;
                    final long end = (threadIdx == threads - 1) ? (contentLength - 1) : ((threadIdx + 1) * chunkSize - 1);

                    executor.execute(() -> {
                        try {
                            HttpURLConnection chunkConn = (HttpURLConnection) new URL(fileUrl).openConnection();
                            chunkConn.setRequestProperty("Range", "bytes=" + start + "-" + end);
                            chunkConn.connect();

                            byte[] buffer = new byte[16384];
                            int read;
                            long lastTime = System.currentTimeMillis();
                            long bytesSinceLastTime = 0;

                            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
                                 InputStream in = chunkConn.getInputStream()) {
                                raf.seek(start);
                                while ((read = in.read(buffer)) != -1) {
                                    raf.write(buffer, 0, read);
                                    long total = task.downloadedBytes.addAndGet(read);
                                    bytesSinceLastTime += read;

                                    long now = System.currentTimeMillis();
                                    long delta = now - lastTime;
                                    if (delta >= 1000) {
                                        long speed = (bytesSinceLastTime * 1000) / delta;
                                        task.speedBytesPerSec = speed;
                                        lastTime = now;
                                        bytesSinceLastTime = 0;

                                        int percent = task.totalBytes > 0 ? (int) ((total * 100) / task.totalBytes) : 0;
                                        String speedStr = formatSpeed(speed);
                                        if (listener != null) {
                                            mainHandler.post(() -> listener.onProgress(percent, speedStr));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Chunk error", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                task.status = DownloadStatus.COMPLETED;
                if (listener != null) {
                    mainHandler.post(() -> listener.onComplete(targetFile));
                }
                Log.i(TAG, "Parallel download complete: " + targetFile.getName() + " (" + contentLength + " bytes)");
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError(e));
                }
            }
        });
    }

    public static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec >= 1024 * 1024) {
            return String.format(Locale.US, "%.1f МБ/с", bytesPerSec / (1024.0 * 1024.0));
        } else if (bytesPerSec >= 1024) {
            return String.format(Locale.US, "%.0f КБ/с", bytesPerSec / 1024.0);
        } else {
            return bytesPerSec + " Б/с";
        }
    }
}
