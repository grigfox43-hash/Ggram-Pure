package org.ggram.ai;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GgramAudioDecoder - Hardware-accelerated on-device audio decoder.
 * Converts Telegram voice messages (.ogg Opus) and video notes (.mp4) into
 * 16 kHz 16-bit Mono PCM suitable for offline speech recognition.
 */
public class GgramAudioDecoder {

    private static final String TAG = "GgramAudioDecoder";
    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final long TIMEOUT_US = 5000;

    public static byte[] decodeTo16kHzPcm(File audioFile) {
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            Log.e(TAG, "Audio file is invalid or does not exist: " + audioFile);
            return null;
        }

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        ByteArrayOutputStream pcmOutputStream = new ByteArrayOutputStream((int) (audioFile.length() * 4));

        try {
            extractor.setDataSource(audioFile.getAbsolutePath());
            int trackIndex = -1;
            MediaFormat trackFormat = null;
            String mimeType = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    trackFormat = format;
                    mimeType = mime;
                    break;
                }
            }

            if (trackIndex == -1 || mimeType == null) {
                Log.e(TAG, "No audio track found in file: " + audioFile.getName());
                return null;
            }

            extractor.selectTrack(trackIndex);
            int inputSampleRate = trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                    trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
            int channelCount = trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                    trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

            decoder = MediaCodec.createDecoderByType(mimeType);
            decoder.configure(trackFormat, null, null, 0);
            decoder.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean isInputEos = false;
            boolean isOutputEos = false;

            while (!isOutputEos) {
                if (!isInputEos) {
                    int inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer != null) {
                            inputBuffer.clear();
                            int sampleSize = extractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                isInputEos = true;
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);

                        byte[] pcmChunk = new byte[info.size];
                        outputBuffer.get(pcmChunk);

                        resampleAndAppend(pcmOutputStream, pcmChunk, inputSampleRate, channelCount);
                    }
                    decoder.releaseOutputBuffer(outputIndex, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEos = true;
                    }
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        inputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }

            return pcmOutputStream.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Error decoding audio file " + audioFile.getName(), e);
            return null;
        } finally {
            try {
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                }
            } catch (Exception ignored) {}
            try {
                extractor.release();
            } catch (Exception ignored) {}
        }
    }

    private static void resampleAndAppend(ByteArrayOutputStream out, byte[] pcmData, int srcSampleRate, int channels) {
        if (pcmData == null || pcmData.length == 0) return;

        int numShorts = pcmData.length / 2;
        short[] srcShorts = new short[numShorts];
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(srcShorts);

        short[] monoShorts;
        if (channels > 1) {
            int frames = numShorts / channels;
            monoShorts = new short[frames];
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) {
                    sum += srcShorts[i * channels + c];
                }
                monoShorts[i] = (short) (sum / channels);
            }
        } else {
            monoShorts = srcShorts;
        }

        short[] resampledShorts;
        if (srcSampleRate == TARGET_SAMPLE_RATE) {
            resampledShorts = monoShorts;
        } else {
            int targetFrames = (int) Math.round(((double) monoShorts.length * TARGET_SAMPLE_RATE) / srcSampleRate);
            resampledShorts = new short[targetFrames];
            for (int i = 0; i < targetFrames; i++) {
                double srcIdx = ((double) i * srcSampleRate) / TARGET_SAMPLE_RATE;
                int idx = (int) srcIdx;
                double frac = srcIdx - idx;
                if (idx + 1 < monoShorts.length) {
                    resampledShorts[i] = (short) (monoShorts[idx] * (1.0 - frac) + monoShorts[idx + 1] * frac);
                } else if (idx < monoShorts.length) {
                    resampledShorts[i] = monoShorts[idx];
                }
            }
        }

        byte[] outBytes = new byte[resampledShorts.length * 2];
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledShorts);
        out.write(outBytes, 0, outBytes.length);
    }
}
