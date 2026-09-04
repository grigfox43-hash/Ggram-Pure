package org.ggram.ai;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.ggram.config.GgramConfig;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GgramVoiceToText - High-performance offline Speech-to-Text engine.
 * Powered by Vosk embedded Russian acoustic language model.
 * Decodes voice messages and video notes completely offline on-device without Telegram Premium.
 */
public class GgramVoiceToText {

    private static final String TAG = "GgramVoiceToText";
    private static final float SAMPLE_RATE = 16000.0f;

    private static volatile Model sharedModel = null;
    private static volatile boolean isInitializing = false;
    private static final Object lock = new Object();

    public interface TranscriptionCallback {
        void onProgress(String partialText);
        void onSuccess(String fullText);
        void onError(String errorMessage);
    }

    private interface ModelCallback {
        void onReady(Model model);
        void onError(Exception e);
    }

    public static void ensureModelReady(Context context, ModelCallback callback) {
        if (sharedModel != null) {
            if (callback != null) callback.onReady(sharedModel);
            return;
        }

        synchronized (lock) {
            if (sharedModel != null) {
                if (callback != null) callback.onReady(sharedModel);
                return;
            }

            if (isInitializing) {
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        for (int i = 0; i < 200; i++) {
                            Thread.sleep(100);
                            if (sharedModel != null) {
                                if (callback != null) callback.onReady(sharedModel);
                                return;
                            }
                        }
                    } catch (InterruptedException ignored) {}
                    if (callback != null) callback.onError(new Exception("Таймаут инициализации языковой модели"));
                });
                return;
            }

            isInitializing = true;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                File targetDir = new File(context.getFilesDir(), "vosk-model-ru");
                File finalModelFile = new File(targetDir, "am/final.mdl");

                // 1. Check if already extracted and valid
                if (finalModelFile.exists() && finalModelFile.length() > 5000000) {
                    Log.i(TAG, "Loading existing Vosk model from: " + targetDir.getAbsolutePath());
                    Model model = new Model(targetDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = model;
                        isInitializing = false;
                    }
                    if (callback != null) callback.onReady(model);
                    return;
                }

                // 2. Try extracting from assets
                boolean extracted = extractModelFromAssets(context, targetDir);
                if (extracted && finalModelFile.exists() && finalModelFile.length() > 5000000) {
                    Log.i(TAG, "Successfully unpacked Vosk model from assets: " + targetDir.getAbsolutePath());
                    Model model = new Model(targetDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = model;
                        isInitializing = false;
                    }
                    if (callback != null) callback.onReady(model);
                    return;
                }

                // 3. Fallback: Check standard storage locations
                File legacyDir = new File(context.getFilesDir(), "model/model-ru");
                if (new File(legacyDir, "am/final.mdl").exists()) {
                    Model model = new Model(legacyDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = model;
                        isInitializing = false;
                    }
                    if (callback != null) callback.onReady(model);
                    return;
                }

                File extDir = new File(context.getExternalFilesDir(null), "model/model-ru");
                if (new File(extDir, "am/final.mdl").exists()) {
                    Model model = new Model(extDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = model;
                        isInitializing = false;
                    }
                    if (callback != null) callback.onReady(model);
                    return;
                }

                // 4. Autonomous download if model is missing
                Log.w(TAG, "Model not found locally, downloading Vosk small-ru model...");
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        android.widget.Toast.makeText(context, "Загрузка языковой модели Vosk...", android.widget.Toast.LENGTH_LONG).show();
                    } catch (Exception ignored) {}
                });

                downloadAndExtractModel(targetDir);
                if (finalModelFile.exists()) {
                    Model model = new Model(targetDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = model;
                        isInitializing = false;
                    }
                    if (callback != null) callback.onReady(model);
                    return;
                }

                throw new IOException("Не удалось загрузить или распаковать модель Vosk");

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Vosk model", e);
                synchronized (lock) {
                    isInitializing = false;
                }
                if (callback != null) callback.onError(e);
            }
        });
    }

    private static boolean extractModelFromAssets(Context context, File targetDir) {
        AssetManager am = context.getAssets();
        String[] prefixes = new String[]{"model-ru", "models/model-ru", "assets/model-ru"};
        String foundPrefix = null;
        for (String prefix : prefixes) {
            try {
                String[] list = am.list(prefix);
                if (list != null && list.length > 0) {
                    foundPrefix = prefix;
                    break;
                }
            } catch (IOException ignored) {}
        }
        if (foundPrefix == null) {
            Log.w(TAG, "No model-ru folder found in assets");
            return false;
        }

        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            copyAssetFolder(am, foundPrefix, targetDir);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error copying model assets", e);
            return false;
        }
    }

    private static void copyAssetFolder(AssetManager am, String srcPath, File destDir) throws IOException {
        String[] files = am.list(srcPath);
        if (files == null || files.length == 0) {
            copyAssetFile(am, srcPath, new File(destDir.getParentFile(), new File(srcPath).getName()));
            return;
        }

        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        for (String file : files) {
            String subSrc = srcPath.isEmpty() ? file : srcPath + "/" + file;
            String[] subFiles = am.list(subSrc);
            File subDest = new File(destDir, file);
            if (subFiles != null && subFiles.length > 0) {
                copyAssetFolder(am, subSrc, subDest);
            } else {
                copyAssetFile(am, subSrc, subDest);
            }
        }
    }

    private static void copyAssetFile(AssetManager am, String srcPath, File destFile) throws IOException {
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = am.open(srcPath);
            out = new FileOutputStream(destFile);
            byte[] buf = new byte[65536];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void downloadAndExtractModel(File targetDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File tempZip = new File(targetDir.getParentFile(), "vosk_temp.zip");
        URL url = new URL("https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Ggram/1.3.0");
        conn.connect();

        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = conn.getInputStream();
            fos = new FileOutputStream(tempZip);
            byte[] buffer = new byte[65536];
            int len;
            while ((len = in.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }

        // Unzip
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(tempZip));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("vosk-model-small-ru-0.22/")) {
                    name = name.substring("vosk-model-small-ru-0.22/".length());
                }
                if (name.isEmpty()) continue;

                File outFile = new File(targetDir, name);
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File p = outFile.getParentFile();
                    if (p != null && !p.exists()) p.mkdirs();
                    FileOutputStream entryFos = null;
                    try {
                        entryFos = new FileOutputStream(outFile);
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = zis.read(buf)) != -1) {
                            entryFos.write(buf, 0, r);
                        }
                        entryFos.flush();
                    } finally {
                        if (entryFos != null) {
                            try { entryFos.close(); } catch (Exception ignored) {}
                        }
                    }
                }
                zis.closeEntry();
            }
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Exception ignored) {}
            }
            if (tempZip.exists()) {
                tempZip.delete();
            }
        }
    }

    public static void transcribeVoiceMessage(Context context, File audioFile, TranscriptionCallback callback) {
        if (!GgramConfig.isVoiceToTextEnabled) {
            if (callback != null) callback.onError("Voice-to-Text отключен в настройках Ggram");
            return;
        }

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            if (callback != null) callback.onError("Аудиофайл не найден в кэше");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                Log.d(TAG, "Starting offline audio decoding for: " + audioFile.getName());
                byte[] pcmData = GgramAudioDecoder.decodeTo16kHzPcm(audioFile);
                if (pcmData == null || pcmData.length == 0) {
                    if (callback != null) callback.onError("Не удалось декодировать аудиопоток");
                    return;
                }

                ensureModelReady(context, new ModelCallback() {
                    @Override
                    public void onReady(Model model) {
                        Utilities.globalQueue.postRunnable(() -> {
                            Recognizer recognizer = null;
                            try {
                                recognizer = new Recognizer(model, SAMPLE_RATE);
                                int chunkSize = 4096;
                                for (int i = 0; i < pcmData.length; i += chunkSize) {
                                    int len = Math.min(chunkSize, pcmData.length - i);
                                    byte[] chunk = new byte[len];
                                    System.arraycopy(pcmData, i, chunk, 0, len);
                                    recognizer.acceptWaveForm(chunk, len);
                                }

                                String finalJson = recognizer.getFinalResult();
                                String text = "";
                                if (finalJson != null && finalJson.contains("\"text\"")) {
                                    JSONObject obj = new JSONObject(finalJson);
                                    text = obj.optString("text", "").trim();
                                }

                                if (text.isEmpty()) {
                                    text = "[Голос не распознан]";
                                } else {
                                    text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
                                }

                                final String resultText = text;
                                Log.i(TAG, "Offline transcription complete: " + resultText);
                                if (callback != null) {
                                    AndroidUtilities.runOnUIThread(() -> callback.onSuccess(resultText));
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Recognition failed", e);
                                if (callback != null) {
                                    AndroidUtilities.runOnUIThread(() -> callback.onError("Ошибка распознавания: " + e.getMessage()));
                                }
                            } finally {
                                if (recognizer != null) {
                                    recognizer.close();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Model error: " + e.getMessage());
                        if (callback != null) {
                            String msg = e != null && e.getMessage() != null ? e.getMessage() : "Языковая модель не найдена в приложении";
                            AndroidUtilities.runOnUIThread(() -> callback.onError(msg));
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Decoding exception", e);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError("Ошибка обработки звука: " + e.getMessage()));
                }
            }
        });
    }
}
