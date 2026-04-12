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
    private static final int SPEAK_READY_RETRY_DELAY_MS = 180;
    private static final int SPEAK_READY_RETRY_MAX = 12;
    private static final float VOICE_RATE_BASE = 0.85f; // Standard learning multiplier
    private static final int CHUNK_GAP_MS = 200; // Natural pause between sentences
    private static final String ERROR_TTS_UNAVAILABLE = "Thiết bị chưa hỗ trợ giọng đọc (TTS).";
    private static final String ERROR_STT_UNAVAILABLE = "Thiết bị chưa hỗ trợ nhận diện giọng nói.";


    public enum State { IDLE, LISTENING, PROCESSING, SPEAKING }
    private State currentState = State.IDLE;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer speechRecognizer;
    private boolean isShutdown = false;
    
    private final Context context;
    private final VoiceCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean autoRelisten = false;
    private final AppSettingsStore settingsStore;
    private float speechRateOverride = -1f;
    
    private final Locale localeEn = Locale.US;
    // Fix Deprecated constructor: use Locale.forLanguageTag()
    private final Locale localeVi = Locale.forLanguageTag("vi-VN");

    private static final Pattern EN_WORD_PATTERN = 
            Pattern.compile("[a-zA-Z][a-zA-Z'\\-]{1,}|\\b[A-Z]{2,}\\b");

    public interface VoiceCallback {
        void onStateChanged(State state);
        void onTranscript(String text);
        void onPartialTranscript(String text);
        void onRmsChanged(float rmsDb);
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
        try {
            tts = new TextToSpeech(context, status -> {
                if (isShutdown) {
                    return;
                }
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    ttsReady = true;
                    tts.setLanguage(localeEn);
                    tts.setSpeechRate(resolveSpeechRate());
                    tts.setPitch(1.0f);
                } else {
                    ttsReady = false;
                    notifyError(ERROR_TTS_UNAVAILABLE);
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize TTS", e);
            tts = null;
            ttsReady = false;
            notifyError(ERROR_TTS_UNAVAILABLE);
            return;
        }

        if (tts == null) {
            ttsReady = false;
            notifyError(ERROR_TTS_UNAVAILABLE);
            return;
        }

        try {
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
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to attach TTS progress listener", e);
            ttsReady = false;
            notifyError(ERROR_TTS_UNAVAILABLE);
        }
    }

    public void speakResponse(String fullText) {
        speakResponse(fullText, 0);
    }

    private void speakResponse(String fullText, int retryCount) {
        if (isShutdown) {
            return;
        }
        if (fullText == null || fullText.trim().isEmpty()) {
            return;
        }

        if (!ttsReady) {
            if (retryCount >= SPEAK_READY_RETRY_MAX) {
                Log.w(TAG, "TTS not ready after retries; skip utterance");
                return;
            }
            final String pending = fullText;
            mainHandler.postDelayed(
                    () -> speakResponse(pending, retryCount + 1),
                    SPEAK_READY_RETRY_DELAY_MS
            );
            return;
        }
        
        tts.setSpeechRate(resolveSpeechRate());
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
            
            // Re-apply rate and pitch to ensure engine doesn't reset on language switch
            float rate = resolveSpeechRate();
            tts.setSpeechRate(rate);
            tts.setPitch(1.0f);

            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            // High priority audio stream for emulator stability
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, uId);

            // Add a natural gap between chunks unless it's the last one
            if (!isLast) {
                tts.playSilentUtterance(CHUNK_GAP_MS, TextToSpeech.QUEUE_ADD, null);
            }
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
        if (am == null) {
            return;
        }
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = null;
            notifyError(ERROR_STT_UNAVAILABLE);
            return;
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to initialize SpeechRecognizer", e);
            speechRecognizer = null;
            notifyError(ERROR_STT_UNAVAILABLE);
            return;
        }

        if (speechRecognizer == null) {
            notifyError(ERROR_STT_UNAVAILABLE);
            return;
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setState(State.LISTENING); }
            @Override public void onPartialResults(Bundle partial) {
                ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0);
                    mainHandler.post(() -> callback.onPartialTranscript(partialText));
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String transcript = matches.get(0);
                    setState(State.PROCESSING);
                    mainHandler.post(() -> callback.onTranscript(transcript));
                } else {
                    setState(State.IDLE);
                }
            }
            @Override public void onError(int error) {
                setState(State.IDLE);
                notifyError(resolveSpeechError(error));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {
                mainHandler.post(() -> callback.onRmsChanged(rmsdB));
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void startListening(String lang) {
        if (isShutdown || speechRecognizer == null) {
            notifyError(ERROR_STT_UNAVAILABLE);
            return;
        }
        if (currentState == State.LISTENING) return;
        stopSpeaking();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        mainHandler.post(() -> {
            if (isShutdown || speechRecognizer == null) {
                return;
            }
            try {
                speechRecognizer.startListening(intent);
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to start listening", e);
                setState(State.IDLE);
                notifyError(ERROR_STT_UNAVAILABLE);
            }
        });
    }

    public void stopSpeaking() {
        if (ttsReady && tts != null) {
            try {
                tts.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to stop TTS", e);
            }
        }
        if (currentState == State.SPEAKING) setState(State.IDLE);
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to stop listening", e);
            }
        }
    }
    public void setAutoRelisten(boolean enabled) { this.autoRelisten = enabled; }
    public void setSpeechRateOverride(float speedRate) {
        if (speedRate >= 0.6f && speedRate <= 2.2f) {
            speechRateOverride = speedRate;
        } else {
            speechRateOverride = -1f;
        }

        if (ttsReady && tts != null) {
            tts.setSpeechRate(resolveSpeechRate());
        }
    }

    private float resolveSpeechRate() {
        float rawRate = 1.0f;
        if (speechRateOverride > 0f) {
            rawRate = speechRateOverride;
        } else if (settingsStore != null) {
            rawRate = settingsStore.getVoiceSpeechRate();
        }
        
        float finalRate = rawRate * VOICE_RATE_BASE;
        Log.d(TAG, "Speech rate resolved: raw=" + rawRate + ", final=" + finalRate);
        return finalRate;
    }

    public State getState() { return currentState; }
    public void shutdown() {
        isShutdown = true;
        autoRelisten = false;
        speechRateOverride = -1f;
        stopListening();
        stopSpeaking();
        ttsReady = false;
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to shutdown TTS", e);
            }
            tts = null;
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (RuntimeException e) {
                Log.w(TAG, "Failed to destroy SpeechRecognizer", e);
            }
            speechRecognizer = null;
        }
        setState(State.IDLE);
    }

    private void notifyError(String message) {
        if (isShutdown || message == null || message.trim().isEmpty()) {
            return;
        }
        mainHandler.post(() -> callback.onError(message));
    }

    private String resolveSpeechError(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Không thể ghi âm microphone lúc này.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Thiếu quyền microphone để nhận diện giọng nói.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Lỗi mạng khi nhận diện giọng nói.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Không nghe rõ nội dung, bạn thử nói lại nhé.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Bộ nhận diện giọng nói đang bận.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Máy chủ nhận diện giọng nói đang lỗi.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Không phát hiện giọng nói.";
            default:
                return ERROR_STT_UNAVAILABLE;
        }
    }

    private void setState(State s) {
        if (currentState != s) {
            currentState = s;
            mainHandler.post(() -> callback.onStateChanged(s));
        }
    }
}
