package com.dailyreading.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

final class ReaderSpeech implements AutoCloseable {
    private final TextToSpeech engine;
    private final Map<String, BiConsumer<String, String>> listeners = new ConcurrentHashMap<>();
    private boolean ready;
    private boolean closed;

    ReaderSpeech(Context context) {
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        engine = new TextToSpeech(context, status -> main.post(() -> onInit(status)));
    }

    private void onInit(int status) {
        if (closed || status != TextToSpeech.SUCCESS) return;
        ready = engine.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE;
        engine.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { dispatch(id, "start", null, false); }
            @Override public void onDone(String id) { dispatch(id, "end", null, true); }
            @Override public void onError(String id) { dispatch(id, "error", "系统语音引擎出错", true); }
            @Override public void onStop(String id, boolean interrupted) { dispatch(id, "end", null, true); }
        });
    }

    void speak(String text, boolean queue, double rate, BiConsumer<String, String> callback) {
        if (!ready) {
            callback.accept("error", "请在安卓系统的文字转语音设置里启用英文语音");
            return;
        }
        if (!queue) stop();

        String id = UUID.randomUUID().toString();
        listeners.put(id, callback);
        engine.setSpeechRate((float) Math.max(0.5, Math.min(2, rate)));
        int status = engine.speak(text, TextToSpeech.QUEUE_ADD, null, id);
        if (status != TextToSpeech.SUCCESS) dispatch(id, "error", "系统语音引擎出错", true);
    }

    private void dispatch(String id, String event, String error, boolean finished) {
        BiConsumer<String, String> callback = finished ? listeners.remove(id) : listeners.get(id);
        if (callback != null) callback.accept(event, error);
    }

    void stop() {
        engine.stop();
        Map<String, BiConsumer<String, String>> pending = new HashMap<>(listeners);
        for (String id : pending.keySet()) dispatch(id, "end", null, true);
    }

    @Override public void close() {
        closed = true;
        stop();
        engine.shutdown();
    }
}
