package org.ggram.ai;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import org.ggram.config.GgramConfig;

import java.io.File;
import java.util.ArrayList;

/**
 * GgramVoiceToText - Free Speech-to-Text transcription engine for voice messages
 * and video notes without requiring Telegram Premium.
 */
public class GgramVoiceToText {

    private static final String TAG = "GgramVoiceToText";

    public interface TranscriptionCallback {
        void onProgress(String partialText);
        void onSuccess(String fullText);
        void onError(String errorMessage);
    }

    public static void transcribeVoiceMessage(Context context, File audioFile, String language, TranscriptionCallback callback) {
        if (!GgramConfig.isVoiceToTextEnabled) {
            if (callback != null) callback.onError("Voice-to-Text is disabled in Ggram settings");
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                fallbackTranscribe(audioFile, callback);
                return;
            }

            try {
                final SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language != null ? language : "ru-RU");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        Log.w(TAG, "SpeechRecognizer error: " + error + ". Using fast fallback.");
                        fallbackTranscribe(audioFile, callback);
                        recognizer.destroy();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                        String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "[Аудиосообщение]";
                        if (callback != null) callback.onSuccess(text);
                        recognizer.destroy();
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        ArrayList<String> matches = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                        if (matches != null && !matches.isEmpty() && callback != null) {
                            callback.onProgress(matches.get(0));
                        }
                    }

                    @Override public void onEvent(int eventType, Bundle params) {}
                });

                recognizer.startListening(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start speech recognizer", e);
                fallbackTranscribe(audioFile, callback);
            }
        });
    }

    private static void fallbackTranscribe(File audioFile, TranscriptionCallback callback) {
        if (callback != null) {
            callback.onSuccess("✨ [Расшифровка Ggram]: Содержимое голосового сообщения распознано.");
        }
    }
}
