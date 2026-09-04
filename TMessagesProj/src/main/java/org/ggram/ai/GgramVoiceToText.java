package org.ggram.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.ggram.config.GgramConfig;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.io.File;

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
                        for (int i = 0; i < 50; i++) {
                            Thread.sleep(100);
                            if (sharedModel != null) {
                                if (callback != null) callback.onReady(sharedModel);
                                return;
                            }
                        }
                    } catch (InterruptedException ignored) {}
                    if (callback != null) callback.onError(new Exception("Model initialization timed out"));
                });
                return;
            }

            isInitializing = true;
        }

        StorageService.unpack(context, "model-ru", "model", model -> {
            synchronized (lock) {
                sharedModel = model;
                isInitializing = false;
            }
            Log.i(TAG, "Vosk offline Russian model loaded successfully from assets");
            if (callback != null) callback.onReady(model);
        }, exception -> {
            synchronized (lock) {
                isInitializing = false;
            }
            Log.e(TAG, "Failed to unpack Vosk model from assets", exception);

            File fallbackDir = new File(context.getFilesDir(), "model");
            if (fallbackDir.exists() && fallbackDir.isDirectory()) {
                try {
                    Model localModel = new Model(fallbackDir.getAbsolutePath());
                    synchronized (lock) {
                        sharedModel = localModel;
                    }
                    if (callback != null) callback.onReady(localModel);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load model from fallback directory", e);
                }
            }

            if (callback != null) callback.onError(exception);
        });
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
                            AndroidUtilities.runOnUIThread(() -> callback.onError("Языковая модель не найдена в приложении"));
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
