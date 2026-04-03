package com.example.englishflow.util;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.example.englishflow.data.AppSettingsStore;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Super Optimized VoiceFlowEngine — Fixed stuttering for Emulators.
 */
public class VoiceFlowEngine {

    private static final String TAG = "VoiceFlowEngine";
    private static final String UTTERANCE_ID_SUFFIX = "_flowflow";

    public enum State { IDLE, LISTENING, PROCESSING, SPEAKING }
    private State currentState = State.IDLE;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer speechRecognizer;
    
    private final Context context;
    private final VoiceCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoRelisten = false;
    private final AppSettingsStore settingsStore;
    
    private final Locale localeEn = Locale.US;
    // Fix Deprecated constructor: use Locale.forLanguageTag()
    private final Locale localeVi = Locale.forLanguageTag("vi-VN");

    private static final Pattern EN_WORD_PATTERN = 
            Pattern.compile("[a-zA-Z][a-zA-Z'\\-]{1,}|\\b[A-Z]{2,}\\b");

    public interface VoiceCallback {
        void onStateChanged(State state);
        void onTranscript(String text);
        void onPartialTranscript(String text);
        void onSpeakingDone();
        void onError(String message);
    }

    public VoiceFlowEngine(Context context, VoiceCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.settingsStore = new AppSettingsStore(this.context);
        initTts();
        initStt();
    }

    private void initTts() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(localeEn);
                tts.setSpeechRate(settingsStore.getVoiceSpeechRate());
                tts.setPitch(1.0f);
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.endsWith("_LAST")) {
                    mainHandler.post(() -> {
                        setState(State.IDLE);
                        callback.onSpeakingDone();
                        if (autoRelisten) startListening("vi-VN");
                    });
                }
            }
            @Override public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error: " + utteranceId);
            }
        });
    }

    public void speakResponse(String fullText) {
        if (fullText == null || fullText.isEmpty() || !ttsReady) return;
        
        tts.setSpeechRate(settingsStore.getVoiceSpeechRate());
        requestAudioFocus();
        stopSpeaking();
        setState(State.SPEAKING);

        // Strip characters and UI LABELS that interfere with natural speaking flow
        String clean = fullText
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("\\*(.*?)\\*", "$1")
                .replaceAll("\\[color:.*?\\](.*?)\\[/color\\]", "$1")
                .replaceAll("(?i)(Từ vựng|Phiên âm \\(IPA\\)|Loại từ|Nghĩa|Ví dụ & Dịch|Lưu ý|Ví dụ)[:：]", "") // Remove headers
                .replaceAll("[✿☁☂ϟ💡➠⇋⚡📝➡]", "") // Remove emojis/symbols
                .replace("/", "") // Remove forward slashes (often in IPA)
                .trim();

        // EMULATOR OPTIMIZATION: Only split by major sentence terminators (.!?) to avoid rapid engine flips
        String[] longChunks = clean.split("(?<=[.!?\\n])\\s+");
        
        for (int i = 0; i < longChunks.length; i++) {
            String chunk = longChunks[i].trim();
            if (chunk.isEmpty()) continue;
            
            boolean isLast = (i == longChunks.length - 1);
            String uId = i + UTTERANCE_ID_SUFFIX + (isLast ? "_LAST" : "");
            
            // Switch locale before adding to queue
            if (isEnglish(chunk)) {
                tts.setLanguage(localeEn);
            } else {
                tts.setLanguage(localeVi);
            }

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            // High priority audio stream for emulator stability
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, uId);
        }
    }

    private static final String VI_CHARS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";

    private boolean isEnglish(String text) {
        if (text == null || text.isEmpty()) return false;
        
        // 1. If it contains any Vietnamese characters, it's 100% Vietnamese
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            if (VI_CHARS.indexOf(lower.charAt(i)) != -1) return false;
        }

        // 2. Otherwise use the word pattern approach for ASCII-only text
        int enCount = 0;
        Matcher m = EN_WORD_PATTERN.matcher(text);
        while (m.find()) enCount++;
        
        String[] words = text.split("\\s+");
        if (words.length == 0) return false;
        
        // Higher threshold to avoid false positives for Vietnamese names or untoned sentences
        return (double) enCount / words.length > 0.6; 
    }

    private void requestAudioFocus() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .build();
            am.requestAudioFocus(req);
        }
    }

    private void initStt() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setState(State.LISTENING); }
            @Override public void onPartialResults(Bundle partial) {
                ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) callback.onPartialTranscript(matches.get(0));
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    setState(State.PROCESSING);
                    callback.onTranscript(matches.get(0));
                } else {
                    setState(State.IDLE);
                }
            }
            @Override public void onError(int error) { setState(State.IDLE); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void startListening(String lang) {
        if (currentState == State.LISTENING) return;
        stopSpeaking();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mainHandler.post(() -> speechRecognizer.startListening(intent));
    }

    public void stopSpeaking() { if (ttsReady) tts.stop(); if (currentState == State.SPEAKING) setState(State.IDLE); }
    public void stopListening() { if (speechRecognizer != null) speechRecognizer.stopListening(); }
    public void setAutoRelisten(boolean enabled) { this.autoRelisten = enabled; }
    public State getState() { return currentState; }
    public void shutdown() { stopListening(); stopSpeaking(); if (tts != null) tts.shutdown(); if (speechRecognizer != null) speechRecognizer.destroy(); }
    private void setState(State s) { if (currentState != s) { currentState = s; callback.onStateChanged(s); } }
}
