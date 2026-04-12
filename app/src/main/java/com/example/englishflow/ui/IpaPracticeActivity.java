package com.example.englishflow.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.CMUDictionary;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IpaPracticeActivity extends AppCompatActivity {

    private static final String TAG = "IpaPracticeActivity";
    private static final String EXTRA_SYMBOL = "extra_symbol";
    private static final String EXTRA_SEED_WORD = "extra_seed_word";

    private static final float PRACTICE_SPEECH_RATE_BASE = 0.80f;
    private static final float PRACTICE_SPEECH_RATE_MIN = 0.62f;
    private static final float PRACTICE_SPEECH_RATE_MAX = 0.90f;
    private static final long SAMPLE_WORD_GAP_MS = 420L;

    private static final int WORD_LADDER_SIZE = 10;
    private static final int PASS_SCORE_OVERALL = 80;
    private static final int PASS_SCORE_SOUND = 75;
    private static final int MIN_PASS_OVERALL = 75;
    private static final int MIN_PASS_SOUND = 70;
    private static final int REQUIRED_CORRECT_READS = 3;
    private static final float MIN_ASR_CONFIDENCE_FOR_HARD_BLOCK = 0.18f;
    private static final float MIN_ASR_CONFIDENCE_FOR_SCORING = 0.45f;
    private static final float CONFIDENCE_WEIGHT_FLOOR = 0.78f;
    private static final float ADAPTIVE_PASS_FACTOR = 0.90f;
    private static final int BASELINE_ATTEMPTS_FOR_ADAPT = 8;
    private static final int MIN_PASS_SCORE_OVERALL = 72;
    private static final int MAX_PASS_SCORE_OVERALL = 88;
    private static final int MIN_PASS_SCORE_SOUND = 68;
    private static final int MAX_PASS_SCORE_SOUND = 86;
    private static final long INLINE_AUDIO_TAP_DEBOUNCE_MS = 360L;
    private static final long AUTO_ADVANCE_BLINK_TOTAL_MS = 300L;
    private static final long AUTO_ADVANCE_SCROLL_DELAY_MS = 180L;
    private static final int ATTEMPT_HISTORY_LIMIT = 5;
    private static final float AUDIO_GATE_LOW_RMS_PEAK_DB = 4.2f;
    private static final float AUDIO_GATE_MEDIUM_RMS_PEAK_DB = 6.4f;
    private static final float AUDIO_GATE_NOISY_RMS_AVG_DB = 9.0f;
    private static final float AUDIO_GATE_SPEECH_TRIGGER_RMS_DB = 1.8f;
    private static final String IPA_PROGRESS_PREFS = "ipa_practice_progress";
    private static final String IPA_PROGRESS_UNLOCK_PREFIX = "ipa_unlock_";
    private static final String IPA_PROGRESS_CORRECT_PREFIX = "ipa_correct_";
    private static final String IPA_BASELINE_COUNT_PREFIX = "ipa_baseline_count_";
    private static final String IPA_BASELINE_SUM_OVERALL_PREFIX = "ipa_baseline_sum_overall_";
    private static final String IPA_BASELINE_SUM_SOUND_PREFIX = "ipa_baseline_sum_sound_";
    private static final String IPA_CONFUSION_PREFIX = "ipa_confusion_";
    private static final int AUDIO_QUALITY_GATE_PASS = 0;
    private static final int AUDIO_QUALITY_GATE_LOW_INPUT = 1;
    private static final int AUDIO_QUALITY_GATE_NOISY = 2;
    private static final int ASR_ERRORS_BEFORE_SYSTEM_FALLBACK = 2;

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListeningForAssessment();
                } else {
                    Toast.makeText(this, R.string.ipa_practice_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> systemRecognizerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> handleSystemRecognizerActivityResult(result.getResultCode(), result.getData()));

    private AppSettingsStore settingsStore;
    private CMUDictionary cmuDictionary;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;

    private IpaProfile profile;
    private final List<String> practiceWords = new ArrayList<>();
    private final List<PracticeWordItem> practiceWordItems = new ArrayList<>();
    private final Map<String, String> practiceIpaLexicon = createPracticeIpaLexicon();
    private final Map<String, Integer> correctReadCountByWord = new HashMap<>();
    private final List<AttemptHistoryEntry> attemptHistory = new ArrayList<>();
    private final Map<String, Integer> confusionCountBySymbol = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentWord = "";
    private int adaptivePassOverall = PASS_SCORE_OVERALL;
    private int adaptivePassSound = PASS_SCORE_SOUND;
    private float listeningRmsPeakDb;
    private float listeningRmsSumDb;
    private int listeningRmsSamples;
    private boolean listeningSpeechDetected;
    private int consecutiveAsrErrors;
    private boolean dictionaryLoading = true;
    private boolean dictionaryFallbackMode;
    private int ipaSourceCmuHits;
    private int ipaSourceFallbackHits;

    private TextView tvPracticeTitle;
    private TextView tvFocusLine;
    private TextView tvCurrentWord;
    private TextView tvCurrentWordProgress;
    private TextView tvWordLadderStatus;
    private TextView tvMouthHint;
    private TextView tvExerciseHint;
    private TextView tvRecognized;
    private TextView tvOverallScore;
    private TextView tvSoundScore;
    private TextView tvOnsetScore;
    private TextView tvCodaScore;
    private TextView tvDiagnosis;
    private TextView tvExercisePlan;
    private TextView tvAttemptHistory;
    private ImageButton btnPlaySymbol;
    private MaterialButton btnListenSample;
    private MaterialButton btnStartPronounce;
    private RecyclerView rvPracticeWords;
    private WordAdapter wordAdapter;
    private int unlockedWordCount = 1;
    private long lastInlineAudioTapUptimeMs;

    public static Intent createIntent(@NonNull Context context,
                                      @Nullable String symbol,
                                      @Nullable String seedWord) {
        Intent intent = new Intent(context, IpaPracticeActivity.class);
        intent.putExtra(EXTRA_SYMBOL, symbol);
        intent.putExtra(EXTRA_SEED_WORD, seedWord);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ipa_practice);

        settingsStore = new AppSettingsStore(this);
        cmuDictionary = CMUDictionary.getInstance(getApplicationContext());

        String symbol = sanitizeSymbol(getIntent().getStringExtra(EXTRA_SYMBOL));
        String seedWord = sanitizeWord(getIntent().getStringExtra(EXTRA_SEED_WORD));
        profile = buildProfile(symbol, seedWord);

        practiceWords.clear();
        practiceWords.addAll(buildWordLadder(profile.symbol, profile.practiceWords, seedWord, profile.expectedPatterns));
        practiceWordItems.clear();
        practiceWordItems.addAll(buildPracticeWordItems(practiceWords, profile.symbol));
        unlockedWordCount = loadUnlockedWordCount(profile.symbol, practiceWords.size());
        initializeCorrectReadProgress();
        loadAdaptivePassThresholds(profile.symbol);
        loadConfusionStats(profile.symbol);
        int currentWordIndex = Math.max(0, Math.min(unlockedWordCount - 1, practiceWords.size() - 1));
        currentWord = practiceWords.isEmpty() ? seedWord : practiceWords.get(currentWordIndex);

        bindViews();
        setupHeader();
        setupWordList();
        setupActions();
        setupTts();
        setupSpeechRecognizer();
        renderProfileInfo();
        renderEmptyAssessmentState();
        setupDictionaryLoadingState();
    }

    private void bindViews() {
        tvPracticeTitle = findViewById(R.id.tvPracticeTitle);
        tvFocusLine = findViewById(R.id.tvPracticeFocus);
        tvCurrentWord = findViewById(R.id.tvCurrentWord);
        tvCurrentWordProgress = findViewById(R.id.tvCurrentWordProgress);
        tvWordLadderStatus = findViewById(R.id.tvWordLadderStatus);
        tvMouthHint = findViewById(R.id.tvPracticeMouthHint);
        tvExerciseHint = findViewById(R.id.tvPracticeExerciseHint);
        tvRecognized = findViewById(R.id.tvRecognizedText);
        tvOverallScore = findViewById(R.id.tvOverallScore);
        tvSoundScore = findViewById(R.id.tvSoundScore);
        tvOnsetScore = findViewById(R.id.tvOnsetScore);
        tvCodaScore = findViewById(R.id.tvCodaScore);
        tvDiagnosis = findViewById(R.id.tvDiagnosis);
        tvExercisePlan = findViewById(R.id.tvExercisePlan);
        tvAttemptHistory = findViewById(R.id.tvAttemptHistory);
        btnPlaySymbol = findViewById(R.id.btnPlaySymbol);
        btnListenSample = findViewById(R.id.btnListenSample);
        btnStartPronounce = findViewById(R.id.btnStartPronounce);
    }

    private void setupHeader() {
        View back = findViewById(R.id.btnBackPractice);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
    }

    private void setupWordList() {
        rvPracticeWords = findViewById(R.id.rvPracticeWords);
        if (rvPracticeWords == null) {
            return;
        }
        rvPracticeWords.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        rvPracticeWords.setNestedScrollingEnabled(false);

        wordAdapter = new WordAdapter(
                practiceWordItems,
                correctReadCountByWord,
                currentWord,
                unlockedWordCount,
                adaptivePassOverall,
                adaptivePassSound,
                new OnWordActionListener() {
            @Override
            public void onWordSelected(@NonNull String word) {
                selectPracticeWord(word, false, false);
            }

            @Override
            public void onPlayWord(@NonNull String word) {
                playWordSample(word);
            }
            }
        );
        rvPracticeWords.setAdapter(wordAdapter);
        renderWordLadderProgress();
    }

    private void selectPracticeWord(@NonNull String word, boolean smoothScroll, boolean animateBlink) {
        currentWord = word;
        if (tvCurrentWord != null) {
            tvCurrentWord.setText(getString(R.string.ipa_practice_current_word_format, currentWord));
        }
        renderCurrentWordProgress();
        renderEmptyAssessmentState();
        if (wordAdapter != null) {
            wordAdapter.setSelectedWord(word);
        }

        int index = practiceWords.indexOf(word);
        if (rvPracticeWords != null && index >= 0) {
            if (smoothScroll) {
                rvPracticeWords.smoothScrollToPosition(index);
            }
            if (animateBlink) {
                blinkPracticeWordItem(index, smoothScroll);
            }
        }
    }

    private void blinkPracticeWordItem(int adapterPosition, boolean waitForScroll) {
        if (rvPracticeWords == null || adapterPosition < 0) {
            return;
        }

        long initialDelayMs = waitForScroll ? AUTO_ADVANCE_SCROLL_DELAY_MS : 0L;
        rvPracticeWords.postDelayed(() -> {
            RecyclerView.ViewHolder viewHolder = rvPracticeWords.findViewHolderForAdapterPosition(adapterPosition);
            if (viewHolder == null) {
                if (waitForScroll) {
                    rvPracticeWords.postDelayed(() -> {
                        RecyclerView.ViewHolder retryHolder = rvPracticeWords.findViewHolderForAdapterPosition(adapterPosition);
                        if (retryHolder == null) {
                            return;
                        }
                        View retryTarget = retryHolder.itemView.findViewById(R.id.cardPracticeWord);
                        runAutoAdvanceBlink(retryTarget == null ? retryHolder.itemView : retryTarget);
                    }, AUTO_ADVANCE_SCROLL_DELAY_MS);
                }
                return;
            }

            View target = viewHolder.itemView.findViewById(R.id.cardPracticeWord);
            runAutoAdvanceBlink(target == null ? viewHolder.itemView : target);
        }, initialDelayMs);
    }

    private void runAutoAdvanceBlink(@NonNull View targetView) {
        long halfDuration = AUTO_ADVANCE_BLINK_TOTAL_MS / 2L;
        targetView.animate().cancel();
        targetView.setAlpha(1f);
        targetView.animate()
                .alpha(0.35f)
                .setDuration(halfDuration)
                .withEndAction(() -> targetView.animate().alpha(1f).setDuration(halfDuration).start())
                .start();
    }

    private void setupActions() {
        if (btnPlaySymbol != null) {
            btnPlaySymbol.setOnClickListener(v -> playSymbolSample());
        }

        if (btnListenSample != null) {
            btnListenSample.setOnClickListener(v -> playSample());
        }

        if (btnStartPronounce != null) {
            btnStartPronounce.setOnClickListener(v -> {
                if (isListening) {
                    stopListeningIfNeeded();
                } else {
                    requestSpeechAssessment();
                }
            });
        }
    }

    private void setupDictionaryLoadingState() {
        setDictionaryLoadingState(true, false);
        if (cmuDictionary == null) {
            setDictionaryLoadingState(false, true);
            return;
        }

        cmuDictionary.preloadAsync(
                () -> runOnUiThread(() -> setDictionaryLoadingState(false, false)),
                () -> runOnUiThread(() -> setDictionaryLoadingState(false, true))
        );
    }

    private void setDictionaryLoadingState(boolean loading, boolean fallbackMode) {
        dictionaryLoading = loading;
        dictionaryFallbackMode = !loading && fallbackMode;
        updateStartPronounceAvailability();

        if (tvRecognized == null || isListening) {
            return;
        }

        if (dictionaryLoading) {
            tvRecognized.setText(R.string.ipa_practice_dictionary_loading);
            return;
        }

        if (dictionaryFallbackMode) {
            tvRecognized.setText(R.string.ipa_practice_dictionary_fallback_basic);
            return;
        }

        tvRecognized.setText(R.string.ipa_practice_recognized_placeholder);
    }

    private void updateStartPronounceAvailability() {
        if (btnStartPronounce == null) {
            return;
        }
        boolean enabled = !dictionaryLoading;
        btnStartPronounce.setEnabled(enabled);
        btnStartPronounce.setAlpha(enabled ? 1f : 0.60f);
    }

    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int languageStatus = tts.setLanguage(Locale.US);
                ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                        && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
            } else {
                ttsReady = false;
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
            }

            @Override
            public void onError(String utteranceId) {
            }
        });
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                resetListeningAudioStats();
                setListeningState(true);
                if (tvRecognized != null) {
                    tvRecognized.setText(R.string.ipa_practice_listening);
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                listeningSpeechDetected = true;
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                recordListeningRms(rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                setListeningState(false);
                consecutiveAsrErrors += 1;
                if (shouldLaunchSystemRecognizerFallback(error) && launchSystemRecognizerFallback()) {
                    Toast.makeText(IpaPracticeActivity.this, R.string.ipa_practice_switching_system_recognizer, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (tvRecognized != null) {
                    tvRecognized.setText(R.string.ipa_practice_empty_result);
                }
                pushAttemptHistory(currentWord, "", 0, 0, R.string.ipa_practice_history_status_no_match);
                Toast.makeText(IpaPracticeActivity.this, mapSpeechError(error), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                setListeningState(false);
                handleAssessmentResults(results, false);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                RecognitionCandidate partialCandidate = chooseBestCandidate(partialResults, currentWord);
                String partial = partialCandidate.word;
                if (!TextUtils.isEmpty(partial) && tvRecognized != null) {
                    tvRecognized.setText(getString(R.string.ipa_practice_recognized_format, partial));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
    }

    private void renderProfileInfo() {
        if (tvPracticeTitle != null) {
            tvPracticeTitle.setText(getString(R.string.ipa_practice_title_format, profile.symbol));
        }
        if (tvFocusLine != null) {
            tvFocusLine.setText(getString(R.string.ipa_practice_focus_format, profile.symbol));
        }
        if (tvCurrentWord != null) {
            tvCurrentWord.setText(getString(R.string.ipa_practice_current_word_format, currentWord));
        }
        renderCurrentWordProgress();
        if (tvMouthHint != null) {
            tvMouthHint.setText(getString(R.string.ipa_practice_mouth_hint_prefix, profile.mouthHint));
        }
        if (tvExerciseHint != null) {
            tvExerciseHint.setText(getString(R.string.ipa_practice_exercise_prefix, profile.exerciseHint));
        }
        renderWordLadderProgress();
    }

    private void renderWordLadderProgress() {
        if (tvWordLadderStatus == null) {
            return;
        }
        tvWordLadderStatus.setText(getString(
                R.string.ipa_practice_word_ladder_status_format,
                unlockedWordCount,
                practiceWords.size(),
            adaptivePassOverall,
            adaptivePassSound,
                REQUIRED_CORRECT_READS
        ));
    }

    private void renderCurrentWordProgress() {
        if (tvCurrentWordProgress == null) {
            return;
        }

        int correctReads = getCorrectReadCount(currentWord);
        tvCurrentWordProgress.setText(getString(
                R.string.ipa_practice_current_word_correct_reads_format,
                Math.min(correctReads, REQUIRED_CORRECT_READS),
                REQUIRED_CORRECT_READS
        ));

        int colorRes = correctReads >= REQUIRED_CORRECT_READS
                ? R.color.ef_success
                : (correctReads > 0 ? R.color.ef_orange_primary : R.color.ef_text_secondary);
        tvCurrentWordProgress.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private void renderEmptyAssessmentState() {
        if (tvRecognized != null) {
            if (dictionaryLoading) {
                tvRecognized.setText(R.string.ipa_practice_dictionary_loading);
            } else if (dictionaryFallbackMode) {
                tvRecognized.setText(R.string.ipa_practice_dictionary_fallback_basic);
            } else {
                tvRecognized.setText(R.string.ipa_practice_recognized_placeholder);
            }
        }
        if (tvOverallScore != null) {
            tvOverallScore.setText(getString(R.string.ipa_practice_score_overall_format, 0));
            tvOverallScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvSoundScore != null) {
            tvSoundScore.setText(getString(R.string.ipa_practice_score_sound_format, 0));
            tvSoundScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvOnsetScore != null) {
            tvOnsetScore.setText(getString(R.string.ipa_practice_score_onset_format, 0));
            tvOnsetScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvCodaScore != null) {
            tvCodaScore.setText(getString(R.string.ipa_practice_score_coda_format, 0));
            tvCodaScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvDiagnosis != null) {
            tvDiagnosis.setText(R.string.ipa_practice_diag_placeholder);
        }
        if (tvExercisePlan != null) {
            tvExercisePlan.setText(R.string.ipa_practice_exercise_placeholder);
        }
        renderAttemptHistory();
    }

    private void renderConfidenceBlockedState(@NonNull String recognizedWord) {
        if (tvRecognized != null) {
            tvRecognized.setText(getString(R.string.ipa_practice_recognized_format, recognizedWord));
        }
        if (tvOverallScore != null) {
            tvOverallScore.setText(getString(R.string.ipa_practice_score_overall_format, 0));
            tvOverallScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvSoundScore != null) {
            tvSoundScore.setText(getString(R.string.ipa_practice_score_sound_format, 0));
            tvSoundScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvOnsetScore != null) {
            tvOnsetScore.setText(getString(R.string.ipa_practice_score_onset_format, 0));
            tvOnsetScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvCodaScore != null) {
            tvCodaScore.setText(getString(R.string.ipa_practice_score_coda_format, 0));
            tvCodaScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvDiagnosis != null) {
            tvDiagnosis.setText(R.string.ipa_practice_confidence_low);
        }
        if (tvExercisePlan != null) {
            tvExercisePlan.setText(R.string.ipa_practice_confidence_retry);
        }
    }

    private void renderAudioQualityBlockedState(@Nullable String recognizedWord, int gateReason) {
        if (tvRecognized != null) {
            if (TextUtils.isEmpty(recognizedWord)) {
                tvRecognized.setText(R.string.ipa_practice_empty_result);
            } else {
                tvRecognized.setText(getString(R.string.ipa_practice_recognized_format, recognizedWord));
            }
        }
        if (tvOverallScore != null) {
            tvOverallScore.setText(getString(R.string.ipa_practice_score_overall_format, 0));
            tvOverallScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvSoundScore != null) {
            tvSoundScore.setText(getString(R.string.ipa_practice_score_sound_format, 0));
            tvSoundScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvOnsetScore != null) {
            tvOnsetScore.setText(getString(R.string.ipa_practice_score_onset_format, 0));
            tvOnsetScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvCodaScore != null) {
            tvCodaScore.setText(getString(R.string.ipa_practice_score_coda_format, 0));
            tvCodaScore.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
        }
        if (tvDiagnosis != null) {
            tvDiagnosis.setText(gateReason == AUDIO_QUALITY_GATE_NOISY
                    ? R.string.ipa_practice_audio_quality_noisy
                    : R.string.ipa_practice_audio_quality_low_input);
        }
        if (tvExercisePlan != null) {
            tvExercisePlan.setText(gateReason == AUDIO_QUALITY_GATE_NOISY
                    ? R.string.ipa_practice_audio_quality_retry_noise
                    : R.string.ipa_practice_audio_quality_retry_low_input);
        }
    }

    private void resetListeningAudioStats() {
        listeningRmsPeakDb = 0f;
        listeningRmsSumDb = 0f;
        listeningRmsSamples = 0;
        listeningSpeechDetected = false;
    }

    private void recordListeningRms(float rmsdB) {
        if (rmsdB < 0f) {
            return;
        }
        if (rmsdB >= AUDIO_GATE_SPEECH_TRIGGER_RMS_DB) {
            listeningSpeechDetected = true;
        }
        listeningRmsPeakDb = Math.max(listeningRmsPeakDb, rmsdB);
        listeningRmsSumDb += rmsdB;
        listeningRmsSamples += 1;
    }

    private int evaluateAudioQualityGate(float recognitionConfidence,
                                         @Nullable String recognizedWord) {
        boolean hasRecognizedToken = !normalizeWord(recognizedWord).isEmpty();
        if (!listeningSpeechDetected && !hasRecognizedToken && listeningRmsSamples == 0) {
            return AUDIO_QUALITY_GATE_LOW_INPUT;
        }

        if (listeningRmsSamples > 0) {
            float averageRms = listeningRmsSumDb / listeningRmsSamples;
            if (listeningRmsPeakDb < AUDIO_GATE_LOW_RMS_PEAK_DB && !hasRecognizedToken) {
                return AUDIO_QUALITY_GATE_LOW_INPUT;
            }
            if (averageRms > AUDIO_GATE_NOISY_RMS_AVG_DB
                    && recognitionConfidence >= 0f
                    && recognitionConfidence < MIN_ASR_CONFIDENCE_FOR_SCORING) {
                return AUDIO_QUALITY_GATE_NOISY;
            }
        }

        return AUDIO_QUALITY_GATE_PASS;
    }

    private int audioQualityGateToast(int gateReason) {
        if (gateReason == AUDIO_QUALITY_GATE_NOISY) {
            return R.string.ipa_practice_audio_quality_noisy;
        }
        return R.string.ipa_practice_audio_quality_low_input;
    }

    private void renderAssessment(@NonNull PronunciationAssessment assessment) {
        if (tvOverallScore != null) {
            tvOverallScore.setText(getString(R.string.ipa_practice_score_overall_format, assessment.overallScore));
            tvOverallScore.setTextColor(ContextCompat.getColor(this, resolveScoreColor(assessment.overallScore)));
        }
        if (tvSoundScore != null) {
            tvSoundScore.setText(getString(R.string.ipa_practice_score_sound_format, assessment.soundScore));
            tvSoundScore.setTextColor(ContextCompat.getColor(this, resolveScoreColor(assessment.soundScore)));
        }
        if (tvOnsetScore != null) {
            tvOnsetScore.setText(getString(R.string.ipa_practice_score_onset_format, assessment.onsetScore));
            tvOnsetScore.setTextColor(ContextCompat.getColor(this, resolveScoreColor(assessment.onsetScore)));
        }
        if (tvCodaScore != null) {
            tvCodaScore.setText(getString(R.string.ipa_practice_score_coda_format, assessment.codaScore));
            tvCodaScore.setTextColor(ContextCompat.getColor(this, resolveScoreColor(assessment.codaScore)));
        }
        if (tvDiagnosis != null) {
            tvDiagnosis.setText(assessment.diagnosis);
        }
        if (tvExercisePlan != null) {
            tvExercisePlan.setText(assessment.exercisePlan);
        }
    }

    private int resolveScoreColor(int score) {
        if (score >= 85) {
            return R.color.ef_success;
        }
        if (score >= 70) {
            return R.color.ef_orange_primary;
        }
        return R.color.ef_error;
    }

    private void playSample() {
        if (!ensureTtsReady()) {
            return;
        }

        String symbolSpeak = toSpeakableSymbol(profile.symbol);
        String wordSpeak = currentWord;
        Bundle params = createTtsAudioParams();
        configurePracticeTts();
        tts.stop();
        tts.speak(symbolSpeak, TextToSpeech.QUEUE_FLUSH, params, "practice_symbol");
        tts.playSilentUtterance(SAMPLE_WORD_GAP_MS, TextToSpeech.QUEUE_ADD, "practice_gap");
        tts.speak(wordSpeak, TextToSpeech.QUEUE_ADD, params, "practice_word");
    }

    private void playSymbolSample() {
        if (!canPlayInlineAudio() || !ensureTtsReady()) {
            return;
        }

        configurePracticeTts();
        tts.stop();
        tts.speak(
                toSpeakableSymbol(profile.symbol),
                TextToSpeech.QUEUE_FLUSH,
                createTtsAudioParams(),
                "practice_symbol_inline"
        );
    }

    private void playWordSample(@NonNull String word) {
        if (!canPlayInlineAudio() || !ensureTtsReady()) {
            return;
        }

        configurePracticeTts();
        tts.stop();
        tts.speak(
                word,
                TextToSpeech.QUEUE_FLUSH,
                createTtsAudioParams(),
                "practice_word_inline"
        );
    }

    private boolean ensureTtsReady() {
        if (tts == null || !ttsReady) {
            Toast.makeText(this, R.string.ipa_practice_tts_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void configurePracticeTts() {
        tts.setSpeechRate(resolvePracticeRate());
        tts.setPitch(1.0f);
    }

    @NonNull
    private Bundle createTtsAudioParams() {
        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
        return params;
    }

    private boolean canPlayInlineAudio() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastInlineAudioTapUptimeMs < INLINE_AUDIO_TAP_DEBOUNCE_MS) {
            return false;
        }
        lastInlineAudioTapUptimeMs = now;
        return true;
    }

    private float resolvePracticeRate() {
        float userRate = settingsStore != null ? settingsStore.getVoiceSpeechRate() : 1.0f;
        float adjustedRate = userRate * PRACTICE_SPEECH_RATE_BASE;
        if (adjustedRate < PRACTICE_SPEECH_RATE_MIN) {
            return PRACTICE_SPEECH_RATE_MIN;
        }
        if (adjustedRate > PRACTICE_SPEECH_RATE_MAX) {
            return PRACTICE_SPEECH_RATE_MAX;
        }
        return adjustedRate;
    }

    private void requestSpeechAssessment() {
        int currentIndex = practiceWords.indexOf(currentWord);
        if (currentIndex >= unlockedWordCount) {
            Toast.makeText(
                    this,
                getString(
                    R.string.ipa_practice_word_locked_hint,
                    currentIndex + 1,
                    adaptivePassOverall,
                    adaptivePassSound,
                    REQUIRED_CORRECT_READS
                ),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (dictionaryLoading) {
            Toast.makeText(this, R.string.ipa_practice_dictionary_loading, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this) || speechRecognizer == null || speechIntent == null) {
            if (!launchSystemRecognizerFallback()) {
                Toast.makeText(this, R.string.ipa_practice_recognition_unavailable, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        startListeningForAssessment();
    }

    private void startListeningForAssessment() {
        if (speechRecognizer == null || speechIntent == null) {
            if (!launchSystemRecognizerFallback()) {
                Toast.makeText(this, R.string.ipa_practice_recognition_unavailable, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        resetListeningAudioStats();
        setListeningState(true);
        if (tvRecognized != null) {
            tvRecognized.setText(R.string.ipa_practice_listening);
        }
        try {
            speechRecognizer.startListening(speechIntent);
        } catch (IllegalStateException ex) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.startListening(speechIntent);
            } catch (RuntimeException retryEx) {
                if (!launchSystemRecognizerFallback()) {
                    setListeningState(false);
                    Toast.makeText(this, R.string.ipa_practice_error_generic, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (RuntimeException ex) {
            if (!launchSystemRecognizerFallback()) {
                setListeningState(false);
                Toast.makeText(this, R.string.ipa_practice_error_generic, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopListeningIfNeeded() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        setListeningState(false);
    }

    private void loadAdaptivePassThresholds(@NonNull String symbol) {
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        int count = Math.max(0, preferences.getInt(baselineCountKey(symbol), 0));
        int sumOverall = Math.max(0, preferences.getInt(baselineOverallSumKey(symbol), 0));
        int sumSound = Math.max(0, preferences.getInt(baselineSoundSumKey(symbol), 0));

        adaptivePassOverall = computeAdaptivePassThreshold(
                count,
                sumOverall,
                PASS_SCORE_OVERALL,
                MIN_PASS_SCORE_OVERALL,
            MAX_PASS_SCORE_OVERALL,
            MIN_PASS_OVERALL
        );
        adaptivePassSound = computeAdaptivePassThreshold(
                count,
                sumSound,
                PASS_SCORE_SOUND,
                MIN_PASS_SCORE_SOUND,
            MAX_PASS_SCORE_SOUND,
            MIN_PASS_SOUND
        );

        if (wordAdapter != null) {
            wordAdapter.setPassThresholds(adaptivePassOverall, adaptivePassSound);
        }
    }

    private void recordAssessmentForAdaptivePass(@NonNull String symbol,
                                                 @NonNull PronunciationAssessment assessment) {
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        int count = Math.max(0, preferences.getInt(baselineCountKey(symbol), 0));
        int sumOverall = Math.max(0, preferences.getInt(baselineOverallSumKey(symbol), 0));
        int sumSound = Math.max(0, preferences.getInt(baselineSoundSumKey(symbol), 0));

        count += 1;
        sumOverall += assessment.overallScore;
        sumSound += assessment.soundScore;

        preferences.edit()
                .putInt(baselineCountKey(symbol), count)
                .putInt(baselineOverallSumKey(symbol), sumOverall)
                .putInt(baselineSoundSumKey(symbol), sumSound)
                .apply();

        loadAdaptivePassThresholds(symbol);
        renderWordLadderProgress();
    }

    private int computeAdaptivePassThreshold(int count,
                                             int scoreSum,
                                             int fallbackThreshold,
                                             int minThreshold,
                                             int maxThreshold,
                                             int minFloor) {
        int safeFloor = Math.max(0, minFloor);
        if (count < BASELINE_ATTEMPTS_FOR_ADAPT || scoreSum <= 0) {
            int fallback = Math.max(fallbackThreshold, safeFloor);
            if (fallback < minThreshold) {
                return minThreshold;
            }
            if (fallback > maxThreshold) {
                return maxThreshold;
            }
            return fallback;
        }

        float averageScore = (float) scoreSum / (float) count;
        int threshold = Math.round(averageScore * ADAPTIVE_PASS_FACTOR);
        threshold = Math.max(threshold, safeFloor);
        if (threshold < minThreshold) {
            return minThreshold;
        }
        if (threshold > maxThreshold) {
            return maxThreshold;
        }
        return threshold;
    }

    @NonNull
    private String baselineCountKey(@NonNull String symbol) {
        return IPA_BASELINE_COUNT_PREFIX + keyPartForSymbol(symbol);
    }

    @NonNull
    private String baselineOverallSumKey(@NonNull String symbol) {
        return IPA_BASELINE_SUM_OVERALL_PREFIX + keyPartForSymbol(symbol);
    }

    @NonNull
    private String baselineSoundSumKey(@NonNull String symbol) {
        return IPA_BASELINE_SUM_SOUND_PREFIX + keyPartForSymbol(symbol);
    }

    private void loadConfusionStats(@NonNull String symbol) {
        confusionCountBySymbol.clear();
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        for (String nearSymbol : symbolNearMatches(symbol)) {
            int count = Math.max(0, preferences.getInt(confusionKey(symbol, nearSymbol), 0));
            if (count > 0) {
                confusionCountBySymbol.put(nearSymbol, count);
            }
        }
    }

    private void recordConfusionObservation(@NonNull String targetSymbol,
                                            @Nullable String confusedSymbol) {
        String normalizedConfusion = confusedSymbol == null ? "" : confusedSymbol.trim();
        if (normalizedConfusion.isEmpty()) {
            return;
        }

        int nextCount = confusionCountBySymbol.getOrDefault(normalizedConfusion, 0) + 1;
        confusionCountBySymbol.put(normalizedConfusion, nextCount);

        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        preferences.edit().putInt(confusionKey(targetSymbol, normalizedConfusion), nextCount).apply();
    }

    @NonNull
    private String topConfusionSymbol() {
        String topSymbol = "";
        int topCount = 0;
        for (Map.Entry<String, Integer> entry : confusionCountBySymbol.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > topCount) {
                topCount = entry.getValue();
                topSymbol = entry.getKey();
            }
        }
        return topSymbol;
    }

    @NonNull
    private String confusionKey(@NonNull String targetSymbol, @NonNull String confusedSymbol) {
        return IPA_CONFUSION_PREFIX + keyPartForSymbol(targetSymbol) + "_" + keyPartForSymbol(confusedSymbol);
    }

    private void initializeCorrectReadProgress() {
        correctReadCountByWord.clear();
        for (int i = 0; i < practiceWords.size(); i++) {
            String word = practiceWords.get(i);
            String normalized = normalizeWord(word);
            if (normalized.isEmpty()) {
                continue;
            }

            int stored = loadCorrectReadCount(profile.symbol, word);
            if (i < unlockedWordCount - 1) {
                stored = Math.max(stored, REQUIRED_CORRECT_READS);
                persistCorrectReadCount(profile.symbol, word, stored);
            } else {
                stored = Math.max(0, Math.min(REQUIRED_CORRECT_READS, stored));
            }

            correctReadCountByWord.put(normalized, stored);
        }
    }

    private void registerSuccessfulRead(@NonNull String word) {
        String normalized = normalizeWord(word);
        if (normalized.isEmpty()) {
            return;
        }

        int currentCount = getCorrectReadCount(word);
        if (currentCount >= REQUIRED_CORRECT_READS) {
            return;
        }

        int nextCount = currentCount + 1;
        correctReadCountByWord.put(normalized, nextCount);
        persistCorrectReadCount(profile.symbol, word, nextCount);

        if (wordAdapter != null) {
            wordAdapter.setCorrectReadCount(word, nextCount);
        }
        if (word.equalsIgnoreCase(currentWord)) {
            renderCurrentWordProgress();
        }
    }

    private int getCorrectReadCount(@Nullable String word) {
        String normalized = normalizeWord(word);
        if (normalized.isEmpty()) {
            return 0;
        }
        Integer count = correctReadCountByWord.get(normalized);
        return count == null ? 0 : count;
    }

    private boolean hasReachedRequiredCorrectReads(@NonNull String word) {
        return getCorrectReadCount(word) >= REQUIRED_CORRECT_READS;
    }

    private void maybeUnlockNextWord(@NonNull String assessedWord) {
        if (practiceWords.isEmpty() || !hasReachedRequiredCorrectReads(assessedWord)) {
            return;
        }

        int currentIndex = practiceWords.indexOf(assessedWord);
        if (currentIndex < 0) {
            return;
        }

        if (currentIndex == practiceWords.size() - 1) {
            Toast.makeText(this, R.string.ipa_practice_ladder_completed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentIndex != unlockedWordCount - 1) {
            return;
        }

        unlockedWordCount = Math.min(practiceWords.size(), unlockedWordCount + 1);
        persistUnlockedWordCount(profile.symbol, unlockedWordCount);
        renderWordLadderProgress();

        if (wordAdapter != null) {
            wordAdapter.setUnlockedWordCount(unlockedWordCount);
        }

        String unlockedWord = practiceWords.get(unlockedWordCount - 1);
        Toast.makeText(this, getString(R.string.ipa_practice_word_unlocked_toast, unlockedWord), Toast.LENGTH_SHORT).show();
        selectPracticeWord(unlockedWord, true, true);
    }

    private int loadUnlockedWordCount(@NonNull String symbol, int wordCount) {
        if (wordCount <= 0) {
            return 1;
        }
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        int stored = preferences.getInt(progressKeyForSymbol(symbol), 1);
        return Math.max(1, Math.min(wordCount, stored));
    }

    private int loadCorrectReadCount(@NonNull String symbol, @NonNull String word) {
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        int stored = preferences.getInt(progressKeyForWord(symbol, word), 0);
        return Math.max(0, Math.min(REQUIRED_CORRECT_READS, stored));
    }

    private void persistUnlockedWordCount(@NonNull String symbol, int unlockedCount) {
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        preferences.edit().putInt(progressKeyForSymbol(symbol), unlockedCount).apply();
    }

    private void persistCorrectReadCount(@NonNull String symbol,
                                         @NonNull String word,
                                         int correctCount) {
        SharedPreferences preferences = getSharedPreferences(IPA_PROGRESS_PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putInt(progressKeyForWord(symbol, word), Math.max(0, Math.min(REQUIRED_CORRECT_READS, correctCount)))
                .apply();
    }

    @NonNull
    private String progressKeyForWord(@NonNull String symbol, @NonNull String word) {
        String wordPart = normalizeWord(word);
        if (wordPart.isEmpty()) {
            wordPart = Integer.toHexString(word.hashCode());
        }
        return IPA_PROGRESS_CORRECT_PREFIX + keyPartForSymbol(symbol) + "_" + wordPart;
    }

    @NonNull
    private String progressKeyForSymbol(@NonNull String symbol) {
        return IPA_PROGRESS_UNLOCK_PREFIX + keyPartForSymbol(symbol);
    }

    @NonNull
    private String keyPartForSymbol(@NonNull String symbol) {
        String keyPart = toSpeakableSymbol(symbol).replaceAll("[^a-z0-9]+", "_");
        if (keyPart.trim().isEmpty()) {
            keyPart = Integer.toHexString(symbol.hashCode());
        }
        return keyPart;
    }

    private void setListeningState(boolean listening) {
        isListening = listening;
        if (btnStartPronounce != null) {
            btnStartPronounce.setText(listening
                    ? R.string.ipa_practice_stop_record
                    : R.string.ipa_practice_start_record);
        }
        updateStartPronounceAvailability();
    }

    private void pushAttemptHistory(@NonNull String targetWord,
                                    @Nullable String spokenWord,
                                    int overallScore,
                                    int soundScore,
                                    int statusLabelRes) {
        String safeTarget = normalizeWord(targetWord);
        if (safeTarget.isEmpty()) {
            safeTarget = normalizeWord(currentWord);
        }
        String safeSpoken = normalizeWord(spokenWord);

        attemptHistory.add(0, new AttemptHistoryEntry(
            safeTarget,
            safeSpoken,
                clampScore(overallScore),
                clampScore(soundScore),
                statusLabelRes
        ));

        if (attemptHistory.size() > ATTEMPT_HISTORY_LIMIT) {
            attemptHistory.remove(attemptHistory.size() - 1);
        }

        renderAttemptHistory();
    }

    private void renderAttemptHistory() {
        if (tvAttemptHistory == null) {
            return;
        }

        if (attemptHistory.isEmpty()) {
            tvAttemptHistory.setText(R.string.ipa_practice_history_placeholder);
            tvAttemptHistory.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
            return;
        }

        List<String> lines = new ArrayList<>();
        int index = 1;
        for (AttemptHistoryEntry entry : attemptHistory) {
            String target = entry.targetWord.isEmpty() ? "-" : entry.targetWord;
            String spoken = entry.spokenWord.isEmpty() ? "-" : entry.spokenWord;
            lines.add(getString(
                    R.string.ipa_practice_history_line_format,
                    index,
                target,
                    spoken,
                    entry.overallScore,
                    entry.soundScore,
                    getString(entry.statusLabelRes)
            ));
            index++;
        }

        tvAttemptHistory.setText(TextUtils.join("\n", lines));
        tvAttemptHistory.setTextColor(ContextCompat.getColor(this, R.color.ef_text_secondary));
    }

    @NonNull
    private String mapSpeechError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            case SpeechRecognizer.ERROR_NO_MATCH:
                return getString(R.string.ipa_practice_empty_result);
            case SpeechRecognizer.ERROR_AUDIO:
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return getString(R.string.ipa_practice_error_generic);
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return getString(R.string.ipa_practice_error_language_unavailable);
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return getString(R.string.ipa_practice_permission_denied);
            default:
                return getString(R.string.ipa_practice_error_generic);
        }
    }

    private void handleAssessmentResults(@Nullable Bundle results, boolean skipAudioQualityGate) {
        consecutiveAsrErrors = 0;
        RecognitionCandidate candidate = chooseBestCandidate(results, currentWord);
        String recognized = candidate.word;

        if (!skipAudioQualityGate) {
            int audioGate = evaluateAudioQualityGate(
                    candidate.hasConfidence() ? candidate.confidence : -1f,
                    recognized
            );
            if (audioGate != AUDIO_QUALITY_GATE_PASS) {
                renderAudioQualityBlockedState(recognized, audioGate);
                pushAttemptHistory(
                        currentWord,
                        recognized,
                        0,
                        0,
                        R.string.ipa_practice_history_status_audio_quality
                );
                Toast.makeText(this, audioQualityGateToast(audioGate), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (TextUtils.isEmpty(recognized)) {
            if (tvRecognized != null) {
                tvRecognized.setText(R.string.ipa_practice_empty_result);
            }
            pushAttemptHistory(currentWord, "", 0, 0, R.string.ipa_practice_history_status_no_match);
            Toast.makeText(this, R.string.ipa_practice_empty_result, Toast.LENGTH_SHORT).show();
            return;
        }

        if (candidate.hasConfidence() && candidate.confidence < MIN_ASR_CONFIDENCE_FOR_HARD_BLOCK) {
            renderConfidenceBlockedState(recognized);
            pushAttemptHistory(currentWord, recognized, 0, 0, R.string.ipa_practice_history_status_blocked);
            Toast.makeText(this, R.string.ipa_practice_confidence_low, Toast.LENGTH_SHORT).show();
            return;
        }

        if (candidate.hasConfidence()
                && candidate.confidence < MIN_ASR_CONFIDENCE_FOR_SCORING
                && listeningRmsPeakDb < AUDIO_GATE_MEDIUM_RMS_PEAK_DB) {
            renderConfidenceBlockedState(recognized);
            pushAttemptHistory(currentWord, recognized, 0, 0, R.string.ipa_practice_history_status_blocked);
            Toast.makeText(this, R.string.ipa_practice_confidence_low, Toast.LENGTH_SHORT).show();
            return;
        }

        if (tvRecognized != null) {
            tvRecognized.setText(getString(R.string.ipa_practice_recognized_format, recognized));
        }

        PronunciationAssessment assessment = assessPronunciation(
                recognized,
                currentWord,
                profile,
                candidate.hasConfidence() ? candidate.confidence : -1f
        );
        int passOverallAtAttemptTime = adaptivePassOverall;
        int passSoundAtAttemptTime = adaptivePassSound;
        renderAssessment(assessment);
        recordAssessmentForAdaptivePass(profile.symbol, assessment);
        if (!assessment.detectedConfusionSymbol.isEmpty()
                && assessment.soundScore < passSoundAtAttemptTime) {
            recordConfusionObservation(profile.symbol, assessment.detectedConfusionSymbol);
        }
        boolean passed = assessment.overallScore >= passOverallAtAttemptTime
                && assessment.soundScore >= passSoundAtAttemptTime;
        pushAttemptHistory(
                currentWord,
                recognized,
                assessment.overallScore,
                assessment.soundScore,
                passed
                        ? R.string.ipa_practice_history_status_pass
                        : R.string.ipa_practice_history_status_retry
        );
        if (passed) {
            registerSuccessfulRead(currentWord);
        }
        maybeUnlockNextWord(currentWord);
    }

    private boolean shouldLaunchSystemRecognizerFallback(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return false;
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return consecutiveAsrErrors >= ASR_ERRORS_BEFORE_SYSTEM_FALLBACK;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
            case SpeechRecognizer.ERROR_AUDIO:
            case SpeechRecognizer.ERROR_SERVER:
            case SpeechRecognizer.ERROR_CLIENT:
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                return true;
            default:
                return false;
        }
    }

    private boolean launchSystemRecognizerFallback() {
        Intent fallbackIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        fallbackIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        fallbackIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag());
        fallbackIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        fallbackIntent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.ipa_practice_system_recognizer_prompt, currentWord)
        );

        try {
            resetListeningAudioStats();
            setListeningState(true);
            if (tvRecognized != null) {
                tvRecognized.setText(R.string.ipa_practice_listening);
            }
            systemRecognizerLauncher.launch(fallbackIntent);
            return true;
        } catch (ActivityNotFoundException ex) {
            setListeningState(false);
            Toast.makeText(this, R.string.ipa_practice_system_recognizer_unavailable, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void handleSystemRecognizerActivityResult(int resultCode, @Nullable Intent data) {
        setListeningState(false);
        if (resultCode != RESULT_OK) {
            if (tvRecognized != null) {
                tvRecognized.setText(R.string.ipa_practice_empty_result);
            }
            Toast.makeText(this, R.string.ipa_practice_empty_result, Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle bundle = new Bundle();
        if (data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null) {
                bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, matches);
            }
            float[] confidence = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
            if (confidence != null) {
                bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, confidence);
            }
        }
        handleAssessmentResults(bundle, true);
    }

    @NonNull
    private RecognitionCandidate chooseBestCandidate(@Nullable Bundle bundle, @NonNull String targetWord) {
        if (bundle == null) {
            return RecognitionCandidate.empty();
        }

        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return RecognitionCandidate.empty();
        }

        float[] confidenceScores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);

        String target = normalizeWord(targetWord);
        String best = "";
        int bestScore = -1;
        float bestConfidence = -1f;

        for (int i = 0; i < matches.size(); i++) {
            String raw = matches.get(i);
            if (raw == null) {
                continue;
            }

            float confidence = confidenceAt(confidenceScores, i);
            List<String> tokens = tokenizeWords(raw);
            if (tokens.isEmpty()) {
                String normalized = normalizeWord(raw);
                int score = similarityScore(target, normalized);
                if (isBetterCandidate(score, confidence, bestScore, bestConfidence)) {
                    bestScore = score;
                    best = normalized;
                    bestConfidence = confidence;
                }
                continue;
            }

            for (String token : tokens) {
                int score = similarityScore(target, token);
                if (isBetterCandidate(score, confidence, bestScore, bestConfidence)) {
                    bestScore = score;
                    best = token;
                    bestConfidence = confidence;
                }
            }
        }

        if (best.isEmpty()) {
            return RecognitionCandidate.empty();
        }
        return new RecognitionCandidate(best, bestConfidence);
    }

    private float confidenceAt(@Nullable float[] scores, int index) {
        if (scores == null || index < 0 || index >= scores.length) {
            return -1f;
        }
        float confidence = scores[index];
        if (confidence < 0f || confidence > 1f) {
            return -1f;
        }
        return confidence;
    }

    private boolean isBetterCandidate(int score, float confidence, int bestScore, float bestConfidence) {
        if (score != bestScore) {
            return score > bestScore;
        }
        return confidence > bestConfidence;
    }

    @NonNull
    private List<String> tokenizeWords(@Nullable String raw) {
        List<String> tokens = new ArrayList<>();
        if (raw == null) {
            return tokens;
        }

        String[] parts = raw.toLowerCase(Locale.US).split("[^a-z']+");
        for (String part : parts) {
            String normalized = normalizeWord(part);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    @NonNull
    private PronunciationAssessment assessPronunciation(@NonNull String spokenWord,
                                                        @NonNull String targetWord,
                                                        @NonNull IpaProfile currentProfile,
                                                        float recognitionConfidence) {
        String spoken = normalizeWord(spokenWord);
        String target = normalizeWord(targetWord);
        String targetIpa = resolveWordIpa(targetWord, currentProfile.symbol, true);
        String spokenIpa = resolveWordIpa(spokenWord, currentProfile.symbol, false);

        if (spoken.isEmpty()) {
            return new PronunciationAssessment(
                    0,
                    0,
                    0,
                    0,
                    getString(R.string.ipa_practice_empty_result),
                    getString(R.string.ipa_practice_exercise_prefix, currentProfile.exerciseHint),
                    ""
            );
        }

        int wordScore = similarityScore(target, spoken);
        int soundScore = computeSoundScore(
                currentProfile.symbol,
                targetIpa,
                spokenIpa,
                spoken,
                currentProfile.expectedPatterns
        );

        String targetOnset = extractOnsetIpaOrFallback(targetIpa, targetWord);
        String spokenOnset = extractOnsetIpaOrFallback(spokenIpa, spokenWord);
        String targetCoda = extractCodaIpaOrFallback(targetIpa, targetWord);
        String spokenCoda = extractCodaIpaOrFallback(spokenIpa, spokenWord);

        int onsetScore = similarityScore(targetOnset, spokenOnset);
        int codaScore = similarityScore(targetCoda, spokenCoda);

        int baseOverall = Math.round(
                wordScore * 0.45f
                        + soundScore * 0.35f
                        + onsetScore * 0.10f
                        + codaScore * 0.10f
        );

        int adjustedSoundScore = applyConfidenceWeight(soundScore, recognitionConfidence);
        int adjustedOverall = applyConfidenceWeight(baseOverall, recognitionConfidence);
        int soundPassThreshold = adaptivePassSound;
        String detectedConfusionSymbol = detectLikelyConfusionSymbol(currentProfile.symbol, spokenIpa);
        int confusionCount = 0;
        String confusionSymbolForFeedback = !detectedConfusionSymbol.isEmpty()
            ? detectedConfusionSymbol
            : topConfusionSymbol();
        if (!confusionSymbolForFeedback.isEmpty()) {
            confusionCount = confusionCountBySymbol.getOrDefault(confusionSymbolForFeedback, 0);
        }

        List<String> issues = new ArrayList<>();
        if (adjustedSoundScore < 70) {
            issues.add(getString(R.string.ipa_practice_issue_sound, currentProfile.symbol));
        }
        if (onsetScore < 65) {
            issues.add(getString(R.string.ipa_practice_issue_onset, targetOnset));
        }
        if (codaScore < 65) {
            issues.add(getString(R.string.ipa_practice_issue_coda, targetCoda));
        }
        if (!confusionSymbolForFeedback.isEmpty() && adjustedSoundScore < soundPassThreshold) {
            issues.add(getString(
                    R.string.ipa_practice_issue_confusion_pair,
                    currentProfile.symbol,
                confusionSymbolForFeedback
            ));
            if (confusionCount > 0) {
                issues.add(getString(
                        R.string.ipa_practice_issue_confusion_pair_progress,
                        confusionCount
                ));
            }
        }

        if (issues.isEmpty()) {
            issues.add(getString(R.string.ipa_practice_issue_good));
        }

        String diagnosis = TextUtils.join("\n", toBulletLines(issues));

        List<String> exerciseLines = new ArrayList<>();
        exerciseLines.add(getString(R.string.ipa_practice_exercise_base, currentProfile.exerciseHint));
        if (adjustedSoundScore < 70) {
            exerciseLines.add(getString(R.string.ipa_practice_exercise_sound, currentProfile.symbol, targetWord));
        }
        if (onsetScore < 65) {
            exerciseLines.add(getString(R.string.ipa_practice_exercise_onset, targetOnset, targetWord));
        }
        if (codaScore < 65) {
            exerciseLines.add(getString(R.string.ipa_practice_exercise_coda, targetCoda, targetWord));
        }
        if (!confusionSymbolForFeedback.isEmpty() && adjustedSoundScore < soundPassThreshold) {
            exerciseLines.add(getString(
                    R.string.ipa_practice_exercise_confusion_drill,
                    currentProfile.symbol,
                confusionSymbolForFeedback,
                    targetWord
            ));
            if (confusionCount > 0) {
                exerciseLines.add(getString(
                        R.string.ipa_practice_exercise_confusion_route,
                        currentProfile.symbol,
                        confusionSymbolForFeedback,
                        confusionCount
                ));
            }
        }

        String exercisePlan = TextUtils.join("\n", toBulletLines(exerciseLines));

        return new PronunciationAssessment(
                clampScore(adjustedOverall),
                clampScore(adjustedSoundScore),
                clampScore(onsetScore),
                clampScore(codaScore),
                diagnosis,
            exercisePlan,
            detectedConfusionSymbol
        );
    }

    private int applyConfidenceWeight(int rawScore, float confidence) {
        return clampScore(Math.round(rawScore * confidenceWeight(confidence)));
    }

    private float confidenceWeight(float confidence) {
        if (confidence < 0f) {
            return 1f;
        }
        if (confidence <= MIN_ASR_CONFIDENCE_FOR_SCORING) {
            return CONFIDENCE_WEIGHT_FLOOR;
        }

        float normalized = (confidence - MIN_ASR_CONFIDENCE_FOR_SCORING)
                / (1f - MIN_ASR_CONFIDENCE_FOR_SCORING);
        normalized = Math.max(0f, Math.min(1f, normalized));
        return CONFIDENCE_WEIGHT_FLOOR + ((1f - CONFIDENCE_WEIGHT_FLOOR) * normalized);
    }

    @NonNull
    private List<String> toBulletLines(@NonNull List<String> lines) {
        List<String> output = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            output.add("• " + line.trim());
        }
        return output;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private int computeSoundScore(@NonNull String targetSymbol,
                                  @NonNull String targetIpa,
                                  @NonNull String spokenIpa,
                                  @NonNull String spokenWord,
                                  @NonNull List<String> expectedPatterns) {
        String normalizedTargetIpa = normalizeIpaForComparison(targetIpa);
        String normalizedSpokenIpa = normalizeIpaForComparison(spokenIpa);

        if (!normalizedTargetIpa.isEmpty() && !normalizedSpokenIpa.isEmpty()) {
            int ipaSimilarity = similarityScore(normalizedTargetIpa, normalizedSpokenIpa);
            if (containsAnyIpaSignature(normalizedSpokenIpa, symbolStrongMatches(targetSymbol))) {
                return clampScore(Math.round(86f + (ipaSimilarity * 0.14f)));
            }
            if (containsAnyIpaSignature(normalizedSpokenIpa, symbolNearMatches(targetSymbol))) {
                return clampScore(Math.round(58f + (ipaSimilarity * 0.22f)));
            }
            return clampScore(Math.round(ipaSimilarity * 0.60f));
        }

        return computeSoundScoreFromTextPatterns(spokenWord, expectedPatterns);
    }

    private int computeSoundScoreFromTextPatterns(@NonNull String spoken,
                                                  @NonNull List<String> expectedPatterns) {
        if (spoken.isEmpty()) {
            return 0;
        }
        if (expectedPatterns.isEmpty()) {
            return 50;
        }

        for (String pattern : expectedPatterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (spoken.contains(pattern)) {
                return 92;
            }
        }

        for (String pattern : expectedPatterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            char marker = pattern.charAt(0);
            if (spoken.indexOf(marker) >= 0) {
                return 62;
            }
        }

        return 35;
    }

    @NonNull
    private String extractOnsetIpaOrFallback(@Nullable String ipa, @Nullable String word) {
        List<String> tokens = tokenizeIpa(ipa);
        if (!tokens.isEmpty()) {
            return tokens.get(0);
        }
        return extractOnset(word);
    }

    @NonNull
    private String extractCodaIpaOrFallback(@Nullable String ipa, @Nullable String word) {
        List<String> tokens = tokenizeIpa(ipa);
        if (!tokens.isEmpty()) {
            return tokens.get(tokens.size() - 1);
        }
        return extractCoda(word);
    }

    @NonNull
    private List<String> tokenizeIpa(@Nullable String ipa) {
        List<String> tokens = new ArrayList<>();
        String normalized = normalizeIpaForComparison(ipa);
        if (normalized.isEmpty()) {
            return tokens;
        }

        List<String> multiCharUnits = Arrays.asList(
                "tʃ", "dʒ", "eɪ", "oʊ", "aʊ", "aɪ", "ɔɪ",
                "ɑ:", "ɔ:", "ɜ:", "ɝ:", "i:", "u:", "ər"
        );

        int index = 0;
        while (index < normalized.length()) {
            String matched = null;
            for (String unit : multiCharUnits) {
                if (normalized.startsWith(unit, index)) {
                    matched = unit;
                    break;
                }
            }

            if (matched != null) {
                tokens.add(matched);
                index += matched.length();
                continue;
            }

            char c = normalized.charAt(index);
            if (c != ':') {
                tokens.add(String.valueOf(c));
            } else if (!tokens.isEmpty()) {
                int lastIndex = tokens.size() - 1;
                tokens.set(lastIndex, tokens.get(lastIndex) + ":");
            }
            index++;
        }

        return tokens;
    }

    @NonNull
    private String normalizeIpaForComparison(@Nullable String ipa) {
        if (ipa == null) {
            return "";
        }
        return ipa.toLowerCase(Locale.US)
                .replaceAll("[/\\[\\]\\sˈˌ]", "")
                .replace('ː', ':')
                .trim();
    }

    private boolean containsAnyIpaSignature(@NonNull String ipa,
                                            @NonNull List<String> signatures) {
        for (String signature : signatures) {
            String normalizedSignature = normalizeIpaForComparison(signature);
            if (!normalizedSignature.isEmpty() && ipa.contains(normalizedSignature)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> symbolStrongMatches(@NonNull String symbol) {
        switch (symbol) {
            case "ɑ:":
                return Arrays.asList("ɑ:", "ɑ");
            case "ɔ:":
                return Arrays.asList("ɔ:", "ɔ");
            case "ɜ:":
                return Arrays.asList("ɜ:", "ɝ:", "ər");
            case "i:":
                return Arrays.asList("i:", "i");
            case "u:":
                return Arrays.asList("u:", "u");
            default:
                return Arrays.asList(symbol);
        }
    }

    @NonNull
    private List<String> symbolNearMatches(@NonNull String symbol) {
        switch (symbol) {
            case "i:":
                return Arrays.asList("ɪ");
            case "ɪ":
                return Arrays.asList("i:", "i");
            case "u:":
                return Arrays.asList("ʊ");
            case "ʊ":
                return Arrays.asList("u:", "u");
            case "eɪ":
                return Arrays.asList("ɛ", "e");
            case "aɪ":
                return Arrays.asList("eɪ", "aʊ");
            case "ɔɪ":
                return Arrays.asList("oʊ");
            case "ʃ":
                return Arrays.asList("s");
            case "ʒ":
                return Arrays.asList("z", "dʒ");
            case "tʃ":
                return Arrays.asList("ʃ", "dʒ");
            case "dʒ":
                return Arrays.asList("ʒ", "tʃ");
            case "θ":
                return Arrays.asList("t", "s", "ð");
            case "ð":
                return Arrays.asList("d", "z", "θ");
            case "ŋ":
                return Arrays.asList("n");
            default:
                return new ArrayList<>();
        }
    }

    @NonNull
    private String detectLikelyConfusionSymbol(@NonNull String targetSymbol, @Nullable String spokenIpa) {
        String normalizedSpokenIpa = normalizeIpaForComparison(spokenIpa);
        if (normalizedSpokenIpa.isEmpty()) {
            return "";
        }

        List<String> nearMatches = symbolNearMatches(targetSymbol);
        for (String nearSymbol : nearMatches) {
            String normalizedNear = normalizeIpaForComparison(nearSymbol);
            if (!normalizedNear.isEmpty() && normalizedSpokenIpa.contains(normalizedNear)) {
                return nearSymbol;
            }
        }
        return "";
    }

    private int similarityScore(@Nullable String a, @Nullable String b) {
        String left = a == null ? "" : a;
        String right = b == null ? "" : b;

        if (left.isEmpty() && right.isEmpty()) {
            return 100;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }

        int distance = levenshtein(left, right);
        int maxLen = Math.max(left.length(), right.length());
        float similarity = 1f - ((float) distance / (float) maxLen);
        return clampScore(Math.round(similarity * 100f));
    }

    private int levenshtein(@NonNull String a, @NonNull String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    @NonNull
    private String extractOnset(@Nullable String word) {
        String normalized = normalizeWord(word);
        if (normalized.isEmpty()) {
            return "";
        }

        List<String> twoLetters = Arrays.asList("th", "sh", "ch", "ph", "wh", "ng", "qu");
        if (normalized.length() >= 2) {
            String firstTwo = normalized.substring(0, 2);
            if (twoLetters.contains(firstTwo)) {
                return firstTwo;
            }
        }
        return normalized.substring(0, 1);
    }

    @NonNull
    private String extractCoda(@Nullable String word) {
        String normalized = normalizeWord(word);
        if (normalized.isEmpty()) {
            return "";
        }

        List<String> twoLetters = Arrays.asList("sh", "ch", "ng", "th", "ck", "st", "ld", "rd");
        if (normalized.length() >= 2) {
            String lastTwo = normalized.substring(normalized.length() - 2);
            if (twoLetters.contains(lastTwo)) {
                return lastTwo;
            }
        }
        return normalized.substring(normalized.length() - 1);
    }

    @NonNull
    private String normalizeWord(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.US).replaceAll("[^a-z]", "").trim();
    }

    @NonNull
    private String sanitizeSymbol(@Nullable String symbol) {
        if (symbol == null) {
            return "ə";
        }
        String trimmed = symbol.trim();
        return trimmed.isEmpty() ? "ə" : trimmed;
    }

    @NonNull
    private String sanitizeWord(@Nullable String word) {
        if (word == null) {
            return "about";
        }
        String trimmed = word.trim();
        return trimmed.isEmpty() ? "about" : trimmed.toLowerCase(Locale.US);
    }

    @NonNull
    private String toSpeakableSymbol(@NonNull String symbol) {
        String clean = symbol.trim().toLowerCase(Locale.US);
        if (clean.equals("ɑ:")) return "ah";
        if (clean.equals("æ")) return "aeh";
        if (clean.equals("ʌ")) return "uh";
        if (clean.equals("ɛ")) return "eh";
        if (clean.equals("eɪ")) return "ay";
        if (clean.equals("ɜ:")) return "er";
        if (clean.equals("ɪ")) return "ih";
        if (clean.equals("i:")) return "ee";
        if (clean.equals("ə")) return "uh";
        if (clean.equals("oʊ")) return "oh";
        if (clean.equals("ʊ")) return "uu";
        if (clean.equals("u:")) return "oo";
        if (clean.equals("aʊ")) return "ow";
        if (clean.equals("aɪ")) return "eye";
        if (clean.equals("ɔɪ")) return "oy";
        if (clean.equals("ɔ:")) return "aw";
        if (clean.equals("tʃ")) return "ch";
        if (clean.equals("dʒ")) return "j";
        if (clean.equals("ʒ")) return "zh";
        if (clean.equals("ʃ")) return "sh";
        if (clean.equals("ð") || clean.equals("θ")) return "th";
        return clean;
    }

    @NonNull
    private List<String> buildWordLadder(@NonNull String symbol,
                                         @NonNull List<String> baseWords,
                                         @NonNull String seedWord,
                                         @NonNull List<String> expectedPatterns) {
        List<String> ladder = new ArrayList<>();
        List<String> bonusWords = ladderBonusWords(symbol);
        List<String> seedWords = words(seedWord);
        List<String> lexiconWords = new ArrayList<>(practiceIpaLexicon.keySet());
        Collections.sort(lexiconWords);

        mergeUniqueWordsForTargetSound(ladder, baseWords, symbol, expectedPatterns);
        mergeUniqueWordsForTargetSound(ladder, bonusWords, symbol, expectedPatterns);
        mergeUniqueWordsForTargetSound(ladder, seedWords, symbol, expectedPatterns);

        if (ladder.size() < WORD_LADDER_SIZE) {
            mergeUniqueWordsForTargetSound(ladder, lexiconWords, symbol, expectedPatterns);
        }

        if (ladder.size() < WORD_LADDER_SIZE) {
            mergeUniqueWords(ladder, baseWords);
            mergeUniqueWords(ladder, bonusWords);
            mergeUniqueWords(ladder, seedWords);
        }

        if (ladder.size() < WORD_LADDER_SIZE) {
            mergeUniqueWords(ladder, lexiconWords);
        }

        if (ladder.size() > WORD_LADDER_SIZE) {
            return new ArrayList<>(ladder.subList(0, WORD_LADDER_SIZE));
        }
        return ladder;
    }

    @NonNull
    private List<PracticeWordItem> buildPracticeWordItems(@NonNull List<String> words,
                                                           @NonNull String symbol) {
        List<PracticeWordItem> items = new ArrayList<>();
        for (String word : words) {
            String safeWord = sanitizeWord(word);
            if (safeWord.isEmpty()) {
                continue;
            }
            items.add(new PracticeWordItem(safeWord, resolveWordIpa(safeWord, symbol)));
        }
        return items;
    }

    @NonNull
    private String resolveWordIpa(@NonNull String word, @NonNull String symbol) {
        return resolveWordIpa(word, symbol, true);
    }

    @NonNull
    private String resolveWordIpa(@NonNull String word,
                                  @NonNull String symbol,
                                  boolean allowSymbolFallback) {
        String normalized = normalizeWord(word);
        String ipa = practiceIpaLexicon.get(normalized);
        if (!TextUtils.isEmpty(ipa)) {
            ipaSourceFallbackHits += 1;
            return ipa;
        }

        String ipaFromCmu = cmuDictionary != null ? cmuDictionary.getIPA(normalized) : null;
        if (!TextUtils.isEmpty(ipaFromCmu)) {
            ipaSourceCmuHits += 1;
            return ipaFromCmu;
        }

        if (!allowSymbolFallback) {
            return "";
        }
        ipaSourceFallbackHits += 1;
        return "/" + symbol + "/";
    }

    private void logIpaSourceStats() {
        int total = ipaSourceCmuHits + ipaSourceFallbackHits;
        float coverage = total > 0 ? ((ipaSourceCmuHits * 100f) / total) : 0f;
        Log.i(
                TAG,
                String.format(
                        Locale.US,
                        "IPA source stats: CMU=%d Fallback=%d Coverage=%.1f%%",
                        ipaSourceCmuHits,
                        ipaSourceFallbackHits,
                        coverage
                )
        );
    }

    @NonNull
    private Map<String, String> createPracticeIpaLexicon() {
        Map<String, String> map = new HashMap<>();

        putIpa(map, "father", "/ˈfɑːðər/");
        putIpa(map, "car", "/kɑːr/");
        putIpa(map, "start", "/stɑːrt/");
        putIpa(map, "calm", "/kɑːm/");
        putIpa(map, "cat", "/kæt/");
        putIpa(map, "bag", "/bæɡ/");
        putIpa(map, "man", "/mæn/");
        putIpa(map, "back", "/bæk/");
        putIpa(map, "cup", "/kʌp/");
        putIpa(map, "luck", "/lʌk/");
        putIpa(map, "bus", "/bʌs/");
        putIpa(map, "much", "/mʌtʃ/");
        putIpa(map, "bed", "/bed/");
        putIpa(map, "pen", "/pen/");
        putIpa(map, "left", "/left/");
        putIpa(map, "said", "/sed/");
        putIpa(map, "say", "/seɪ/");
        putIpa(map, "late", "/leɪt/");
        putIpa(map, "cake", "/keɪk/");
        putIpa(map, "train", "/treɪn/");
        putIpa(map, "her", "/hɜːr/");
        putIpa(map, "bird", "/bɝːd/");
        putIpa(map, "word", "/wɝːd/");
        putIpa(map, "learn", "/lɝːn/");
        putIpa(map, "sit", "/sɪt/");
        putIpa(map, "ship", "/ʃɪp/");
        putIpa(map, "milk", "/mɪlk/");
        putIpa(map, "big", "/bɪɡ/");
        putIpa(map, "sheep", "/ʃiːp/");
        putIpa(map, "green", "/ɡriːn/");
        putIpa(map, "see", "/siː/");
        putIpa(map, "need", "/niːd/");
        putIpa(map, "about", "/əˈbaʊt/");
        putIpa(map, "ago", "/əˈɡoʊ/");
        putIpa(map, "teacher", "/ˈtiːtʃər/");
        putIpa(map, "support", "/səˈpɔːrt/");
        putIpa(map, "go", "/ɡoʊ/");
        putIpa(map, "home", "/hoʊm/");
        putIpa(map, "phone", "/foʊn/");
        putIpa(map, "open", "/ˈoʊpən/");
        putIpa(map, "book", "/bʊk/");
        putIpa(map, "good", "/ɡʊd/");
        putIpa(map, "look", "/lʊk/");
        putIpa(map, "full", "/fʊl/");
        putIpa(map, "blue", "/bluː/");
        putIpa(map, "food", "/fuːd/");
        putIpa(map, "school", "/skuːl/");
        putIpa(map, "true", "/truː/");
        putIpa(map, "now", "/naʊ/");
        putIpa(map, "house", "/haʊs/");
        putIpa(map, "sound", "/saʊnd/");
        putIpa(map, "around", "/əˈraʊnd/");
        putIpa(map, "my", "/maɪ/");
        putIpa(map, "time", "/taɪm/");
        putIpa(map, "light", "/laɪt/");
        putIpa(map, "drive", "/draɪv/");
        putIpa(map, "boy", "/bɔɪ/");
        putIpa(map, "toy", "/tɔɪ/");
        putIpa(map, "voice", "/vɔɪs/");
        putIpa(map, "choice", "/tʃɔɪs/");
        putIpa(map, "law", "/lɔː/");
        putIpa(map, "talk", "/tɔːk/");
        putIpa(map, "call", "/kɔːl/");
        putIpa(map, "small", "/smɔːl/");
        putIpa(map, "check", "/tʃek/");
        putIpa(map, "chair", "/tʃer/");
        putIpa(map, "watch", "/wɑːtʃ/");
        putIpa(map, "just", "/dʒʌst/");
        putIpa(map, "job", "/dʒɑːb/");
        putIpa(map, "bridge", "/brɪdʒ/");
        putIpa(map, "orange", "/ˈɔːrɪndʒ/");
        putIpa(map, "sing", "/sɪŋ/");
        putIpa(map, "long", "/lɔːŋ/");
        putIpa(map, "bring", "/brɪŋ/");
        putIpa(map, "finger", "/ˈfɪŋɡər/");
        putIpa(map, "measure", "/ˈmeʒər/");
        putIpa(map, "vision", "/ˈvɪʒən/");
        putIpa(map, "usual", "/ˈjuːʒuəl/");
        putIpa(map, "beige", "/beɪʒ/");
        putIpa(map, "she", "/ʃiː/");
        putIpa(map, "push", "/pʊʃ/");
        putIpa(map, "nation", "/ˈneɪʃən/");
        putIpa(map, "this", "/ðɪs/");
        putIpa(map, "that", "/ðæt/");
        putIpa(map, "think", "/θɪŋk/");
        putIpa(map, "three", "/θriː/");
        putIpa(map, "yes", "/jes/");
        putIpa(map, "yellow", "/ˈjeloʊ/");
        putIpa(map, "music", "/ˈmjuːzɪk/");
        putIpa(map, "use", "/juːz/");
        putIpa(map, "hard", "/hɑːrd/");
        putIpa(map, "garden", "/ˈɡɑːrdən/");
        putIpa(map, "market", "/ˈmɑːrkɪt/");
        putIpa(map, "drama", "/ˈdrɑːmə/");
        putIpa(map, "target", "/ˈtɑːrɡɪt/");
        putIpa(map, "charge", "/tʃɑːrdʒ/");
        putIpa(map, "apple", "/ˈæpəl/");
        putIpa(map, "happy", "/ˈhæpi/");
        putIpa(map, "family", "/ˈfæməli/");
        putIpa(map, "camera", "/ˈkæmərə/");
        putIpa(map, "planet", "/ˈplænɪt/");
        putIpa(map, "answer", "/ˈænsər/");
        putIpa(map, "love", "/lʌv/");
        putIpa(map, "money", "/ˈmʌni/");
        putIpa(map, "country", "/ˈkʌntri/");
        putIpa(map, "wonder", "/ˈwʌndər/");
        putIpa(map, "mother", "/ˈmʌðər/");
        putIpa(map, "young", "/jʌŋ/");
        putIpa(map, "head", "/hed/");
        putIpa(map, "friend", "/frend/");
        putIpa(map, "ready", "/ˈredi/");
        putIpa(map, "many", "/ˈmeni/");
        putIpa(map, "welcome", "/ˈwelkəm/");
        putIpa(map, "message", "/ˈmesɪdʒ/");
        putIpa(map, "day", "/deɪ/");
        putIpa(map, "make", "/meɪk/");
        putIpa(map, "name", "/neɪm/");
        putIpa(map, "great", "/ɡreɪt/");
        putIpa(map, "baby", "/ˈbeɪbi/");
        putIpa(map, "station", "/ˈsteɪʃən/");
        putIpa(map, "girl", "/ɡɝːl/");
        putIpa(map, "nurse", "/nɝːs/");
        putIpa(map, "first", "/fɝːst/");
        putIpa(map, "early", "/ˈɝːli/");
        putIpa(map, "service", "/ˈsɝːvɪs/");
        putIpa(map, "thirty", "/ˈθɝːti/");
        putIpa(map, "live", "/lɪv/");
        putIpa(map, "quick", "/kwɪk/");
        putIpa(map, "little", "/ˈlɪtəl/");
        putIpa(map, "river", "/ˈrɪvər/");
        putIpa(map, "window", "/ˈwɪndoʊ/");
        putIpa(map, "village", "/ˈvɪlɪdʒ/");
        putIpa(map, "team", "/tiːm/");
        putIpa(map, "people", "/ˈpiːpəl/");
        putIpa(map, "leave", "/liːv/");
        putIpa(map, "season", "/ˈsiːzən/");
        putIpa(map, "reason", "/ˈriːzən/");
        putIpa(map, "away", "/əˈweɪ/");
        putIpa(map, "banana", "/bəˈnænə/");
        putIpa(map, "sofa", "/ˈsoʊfə/");
        putIpa(map, "problem", "/ˈprɑːbləm/");
        putIpa(map, "animal", "/ˈænɪməl/");
        putIpa(map, "close", "/kloʊz/");
        putIpa(map, "road", "/roʊd/");
        putIpa(map, "moment", "/ˈmoʊmənt/");
        putIpa(map, "hotel", "/hoʊˈtel/");
        putIpa(map, "gold", "/ɡoʊld/");
        putIpa(map, "could", "/kʊd/");
        putIpa(map, "would", "/wʊd/");
        putIpa(map, "sugar", "/ˈʃʊɡər/");
        putIpa(map, "woman", "/ˈwʊmən/");
        putIpa(map, "put", "/pʊt/");
        putIpa(map, "pull", "/pʊl/");
        putIpa(map, "move", "/muːv/");
        putIpa(map, "group", "/ɡruːp/");
        putIpa(map, "truth", "/truːθ/");
        putIpa(map, "juice", "/dʒuːs/");
        putIpa(map, "through", "/θruː/");
        putIpa(map, "town", "/taʊn/");
        putIpa(map, "cloud", "/klaʊd/");
        putIpa(map, "found", "/faʊnd/");
        putIpa(map, "mountain", "/ˈmaʊntən/");
        putIpa(map, "flower", "/ˈflaʊər/");
        putIpa(map, "outside", "/ˌaʊtˈsaɪd/");
        putIpa(map, "write", "/raɪt/");
        putIpa(map, "smile", "/smaɪl/");
        putIpa(map, "night", "/naɪt/");
        putIpa(map, "inside", "/ɪnˈsaɪd/");
        putIpa(map, "bright", "/braɪt/");
        putIpa(map, "join", "/dʒɔɪn/");
        putIpa(map, "noise", "/nɔɪz/");
        putIpa(map, "point", "/pɔɪnt/");
        putIpa(map, "enjoy", "/ɪnˈdʒɔɪ/");
        putIpa(map, "more", "/mɔːr/");
        putIpa(map, "short", "/ʃɔːrt/");
        putIpa(map, "story", "/ˈstɔːri/");
        putIpa(map, "board", "/bɔːrd/");
        putIpa(map, "morning", "/ˈmɔːrnɪŋ/");
        putIpa(map, "order", "/ˈɔːrdər/");
        putIpa(map, "change", "/tʃeɪndʒ/");
        putIpa(map, "child", "/tʃaɪld/");
        putIpa(map, "nature", "/ˈneɪtʃər/");
        putIpa(map, "future", "/ˈfjuːtʃər/");
        putIpa(map, "lunch", "/lʌntʃ/");
        putIpa(map, "jump", "/dʒʌmp/");
        putIpa(map, "giant", "/ˈdʒaɪənt/");
        putIpa(map, "energy", "/ˈenərdʒi/");
        putIpa(map, "general", "/ˈdʒenərəl/");
        putIpa(map, "region", "/ˈriːdʒən/");
        putIpa(map, "adjust", "/əˈdʒʌst/");
        putIpa(map, "song", "/sɔːŋ/");
        putIpa(map, "strong", "/strɔːŋ/");
        putIpa(map, "longer", "/ˈlɔːŋɡər/");
        putIpa(map, "singing", "/ˈsɪŋɪŋ/");
        putIpa(map, "television", "/ˈtelɪvɪʒən/");
        putIpa(map, "decision", "/dɪˈsɪʒən/");
        putIpa(map, "usually", "/ˈjuːʒuəli/");
        putIpa(map, "pleasure", "/ˈpleʒər/");
        putIpa(map, "shop", "/ʃɑːp/");
        putIpa(map, "special", "/ˈspeʃəl/");
        putIpa(map, "machine", "/məˈʃiːn/");
        putIpa(map, "ocean", "/ˈoʊʃən/");
        putIpa(map, "delicious", "/dɪˈlɪʃəs/");
        putIpa(map, "those", "/ðoʊz/");
        putIpa(map, "them", "/ðem/");
        putIpa(map, "thank", "/θæŋk/");
        putIpa(map, "weather", "/ˈweðər/");
        putIpa(map, "year", "/jɪr/");
        putIpa(map, "student", "/ˈstuːdənt/");
        putIpa(map, "university", "/ˌjuːnɪˈvɝːsəti/");
        putIpa(map, "few", "/fjuː/");
        putIpa(map, "practice", "/ˈpræktɪs/");
        putIpa(map, "repeat", "/rɪˈpiːt/");
        putIpa(map, "speak", "/spiːk/");
        putIpa(map, "basic", "/ˈbeɪsɪk/");
        putIpa(map, "simple", "/ˈsɪmpəl/");
        putIpa(map, "better", "/ˈbetər/");
        putIpa(map, "listen", "/ˈlɪsən/");
        putIpa(map, "clear", "/klɪr/");

        return map;
    }

    private void putIpa(@NonNull Map<String, String> map,
                        @NonNull String word,
                        @NonNull String ipa) {
        map.put(normalizeWord(word), ipa);
    }

    private void mergeUniqueWordsForTargetSound(@NonNull List<String> target,
                                                 @NonNull List<String> source,
                                                 @NonNull String symbol,
                                                 @NonNull List<String> expectedPatterns) {
        for (String item : source) {
            String safe = sanitizeWord(item);
            if (safe.isEmpty() || target.contains(safe)) {
                continue;
            }
            if (!containsTargetSound(safe, symbol, expectedPatterns)) {
                continue;
            }
            target.add(safe);
            if (target.size() >= WORD_LADDER_SIZE) {
                return;
            }
        }
    }

    private boolean containsTargetSound(@NonNull String word,
                                        @NonNull String symbol,
                                        @NonNull List<String> expectedPatterns) {
        String normalizedWord = normalizeWord(word);
        if (normalizedWord.isEmpty()) {
            return false;
        }

        List<String> normalizedPatterns = new ArrayList<>();
        for (String pattern : expectedPatterns) {
            String normalizedPattern = normalizeWord(pattern);
            if (!normalizedPattern.isEmpty() && !normalizedPatterns.contains(normalizedPattern)) {
                normalizedPatterns.add(normalizedPattern);
            }
        }

        List<String> filterPatterns = new ArrayList<>();
        for (String pattern : normalizedPatterns) {
            if (pattern.length() >= 2) {
                filterPatterns.add(pattern);
            }
        }

        if (filterPatterns.size() < 2) {
            for (String pattern : normalizedPatterns) {
                if (pattern.length() == 1 && !filterPatterns.contains(pattern)) {
                    filterPatterns.add(pattern);
                }
            }
        }

        if (filterPatterns.isEmpty()) {
            String fallbackPattern = normalizeWord(toSpeakableSymbol(symbol));
            if (!fallbackPattern.isEmpty()) {
                filterPatterns.add(fallbackPattern);
            }
        }

        for (String pattern : filterPatterns) {
            if (normalizedWord.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void mergeUniqueWords(@NonNull List<String> target, @NonNull List<String> source) {
        for (String item : source) {
            String safe = sanitizeWord(item);
            if (safe.isEmpty() || target.contains(safe)) {
                continue;
            }
            target.add(safe);
            if (target.size() >= WORD_LADDER_SIZE) {
                return;
            }
        }
    }

    @NonNull
    private List<String> ladderBonusWords(@NonNull String symbol) {
        switch (symbol) {
            case "ɑ:":
                return words("hard", "garden", "market", "drama", "target", "charge");
            case "æ":
                return words("apple", "happy", "family", "camera", "planet", "answer");
            case "ʌ":
                return words("love", "money", "country", "wonder", "mother", "young");
            case "ɛ":
                return words("head", "friend", "ready", "many", "welcome", "message");
            case "eɪ":
                return words("day", "make", "name", "great", "baby", "station");
            case "ɜ:":
                return words("girl", "nurse", "first", "early", "service", "thirty");
            case "ɪ":
                return words("live", "quick", "little", "river", "window", "village");
            case "i:":
                return words("team", "people", "leave", "teacher", "season", "reason");
            case "ə":
                return words("away", "banana", "sofa", "problem", "animal", "around");
            case "oʊ":
                return words("close", "road", "moment", "hotel", "window", "gold");
            case "ʊ":
                return words("could", "would", "sugar", "woman", "put", "pull");
            case "u:":
                return words("move", "group", "truth", "juice", "through", "school");
            case "aʊ":
                return words("town", "cloud", "found", "mountain", "flower", "outside");
            case "aɪ":
                return words("light", "write", "smile", "night", "inside", "bright");
            case "ɔɪ":
                return words("join", "noise", "point", "voice", "choice", "enjoy");
            case "ɔ:":
                return words("more", "short", "story", "board", "morning", "order");
            case "tʃ":
                return words("change", "child", "nature", "future", "lunch", "teacher");
            case "dʒ":
                return words("jump", "giant", "energy", "general", "region", "adjust");
            case "ŋ":
                return words("song", "strong", "morning", "longer", "young", "singing");
            case "ʒ":
                return words("television", "decision", "usually", "pleasure", "vision", "measure");
            case "ʃ":
                return words("shop", "special", "machine", "ocean", "station", "delicious");
            case "ð":
            case "θ":
                return words("those", "them", "mother", "thank", "thirty", "weather");
            case "j":
                return words("year", "young", "student", "university", "few", "music");
            default:
                return words("basic", "simple", "strong", "better", "listen", "clear");
        }
    }

    @NonNull
    private IpaProfile buildProfile(@NonNull String symbol, @NonNull String seedWord) {
        switch (symbol) {
            case "ɑ:":
                return new IpaProfile(symbol,
                        words("father", "car", "start", "calm"),
                        patterns("ar", "a"),
                        "Mở miệng rộng, hạ lưỡi thấp và giữ hơi dài.",
                        "Đọc kéo dài /ɑ:/ trước khi ghép vào từ hoàn chỉnh.");
            case "æ":
                return new IpaProfile(symbol,
                        words("cat", "bag", "man", "back"),
                        patterns("a"),
                        "Mở miệng ngang, hàm hạ vừa phải, môi không tròn.",
                        "Luyện chuỗi /æ-æ-æ/ rồi đọc cat, bag, man chậm rãi.");
            case "ʌ":
                return new IpaProfile(symbol,
                        words("cup", "luck", "bus", "much"),
                        patterns("u", "o"),
                        "Giữ miệng hơi mở, lưỡi ở giữa, âm ngắn và dứt.",
                        "Đọc nhanh cặp cup-cap để phân biệt /ʌ/ và /æ/.");
            case "ɛ":
                return new IpaProfile(symbol,
                        words("bed", "pen", "left", "said"),
                        patterns("e", "ea"),
                        "Miệng mở nhẹ theo chiều ngang, âm ngắn, không kéo.",
                        "Luyện cặp bed-bad để tránh nhầm /ɛ/ và /æ/.");
            case "eɪ":
                return new IpaProfile(symbol,
                        words("say", "late", "cake", "train"),
                        patterns("ay", "a", "ai"),
                        "Bắt đầu ở âm /e/ rồi lướt nhanh lên /ɪ/.",
                        "Đọc theo nhịp: e -> eɪ -> say, late, train.");
            case "ɜ:":
                return new IpaProfile(symbol,
                        words("her", "bird", "word", "learn"),
                        patterns("ir", "er", "ur", "ear"),
                        "Môi hơi tròn nhẹ, lưỡi giữa, giữ âm dài ổn định.",
                        "Luyện giữ hơi đều ở /ɜ:/ trong 2 giây mỗi lần.");
            case "ɪ":
                return new IpaProfile(symbol,
                        words("sit", "ship", "milk", "big"),
                        patterns("i", "y"),
                        "Âm ngắn, lưỡi nâng nhẹ phía trước, không kéo dài.",
                        "Luyện cặp ship-sheep để tách /ɪ/ và /i:/.");
            case "i:":
                return new IpaProfile(symbol,
                        words("sheep", "green", "see", "need"),
                        patterns("ee", "ea", "e"),
                        "Kéo âm dài, môi hơi kéo ngang, lưỡi cao phía trước.",
                        "Giữ /i:/ 1 giây rồi ghép vào sheep, green.");
            case "ə":
                return new IpaProfile(symbol,
                        words("about", "ago", "teacher", "support"),
                        patterns("a", "o", "e", "u"),
                        "Giữ miệng thả lỏng tự nhiên, âm trung tính rất ngắn.",
                        "Luyện đọc schwa ở âm tiết không nhấn: a-bout, tea-cher.");
            case "oʊ":
                return new IpaProfile(symbol,
                        words("go", "home", "phone", "open"),
                        patterns("o", "oa", "ow"),
                        "Bắt đầu âm /o/ rồi lướt sang /ʊ/, môi tròn dần.",
                        "Luyện chuỗi: o -> oʊ -> go, home, phone.");
            case "ʊ":
                return new IpaProfile(symbol,
                        words("book", "good", "look", "full"),
                        patterns("oo", "u"),
                        "Môi tròn nhẹ, âm ngắn, lưỡi cao vừa phải phía sau.",
                        "Luyện cặp look-luke để tách /ʊ/ và /u:/.");
            case "u:":
                return new IpaProfile(symbol,
                        words("blue", "food", "school", "true"),
                        patterns("oo", "ue", "u"),
                        "Môi tròn rõ, giữ âm dài, lưỡi cao phía sau.",
                        "Kéo dài /u:/ rồi ghép blue, food, true.");
            case "aʊ":
                return new IpaProfile(symbol,
                        words("now", "house", "sound", "around"),
                        patterns("ow", "ou"),
                        "Bắt đầu /a/ mở rộng rồi tròn môi sang /ʊ/.",
                        "Luyện nhịp chậm now-house-around để ổn định khẩu hình.");
            case "aɪ":
                return new IpaProfile(symbol,
                        words("my", "time", "light", "drive"),
                        patterns("y", "i", "igh"),
                        "Từ /a/ mở rộng lướt nhanh lên /ɪ/ ở cuối.",
                        "Đọc cặp my-may để tránh nhầm /aɪ/ và /eɪ/.");
            case "ɔɪ":
                return new IpaProfile(symbol,
                        words("boy", "toy", "voice", "choice"),
                        patterns("oy", "oi"),
                        "Môi hơi tròn ở đầu âm, sau đó lướt về /ɪ/.",
                        "Luyện nối âm: /ɔ/ -> /ɔɪ/ -> boy, toy.");
            case "ɔ:":
                return new IpaProfile(symbol,
                        words("law", "talk", "call", "small"),
                        patterns("aw", "al", "or"),
                        "Môi tròn vừa, hàm hạ nhẹ và giữ âm dài.",
                        "Giữ /ɔ:/ trước gương để kiểm soát độ tròn môi.");
            case "tʃ":
                return new IpaProfile(symbol,
                        words("check", "chair", "teacher", "watch"),
                        patterns("ch", "tch"),
                        "Đầu lưỡi chạm lợi rồi bật nhanh thành luồng hơi /ch/.",
                        "Luyện chuỗi: ch-ch-check, chair, watch.");
            case "dʒ":
                return new IpaProfile(symbol,
                        words("just", "job", "bridge", "orange"),
                        patterns("j", "g", "dge"),
                        "Giống /tʃ/ nhưng có rung dây thanh ngay khi bật âm.",
                        "Đọc cặp jeep-cheap để cảm nhận rung dây thanh.");
            case "ŋ":
                return new IpaProfile(symbol,
                        words("sing", "long", "bring", "finger"),
                        patterns("ng"),
                        "Đặt phần sau lưỡi chạm ngạc mềm, hơi ra qua mũi.",
                        "Luyện kết thúc từ bằng -ng: sing, long, bring.");
            case "ʒ":
                return new IpaProfile(symbol,
                        words("measure", "vision", "usual", "beige"),
                        patterns("s", "si", "ge"),
                        "Môi hơi tròn, lưỡi gần lợi, có rung dây thanh nhẹ.",
                        "Luyện kéo âm /ʒ/ 1 giây trước khi ghép vào từ.");
            case "ʃ":
                return new IpaProfile(symbol,
                        words("she", "ship", "push", "nation"),
                        patterns("sh", "ti", "si"),
                        "Đẩy môi hơi tròn, tạo khe hẹp để hơi đi ra liên tục.",
                        "Luyện cặp sip-ship để rõ khác biệt /s/ và /ʃ/.");
            case "ð":
            case "θ":
                return new IpaProfile(symbol,
                        words("this", "that", "think", "three"),
                        patterns("th"),
                        "Đặt đầu lưỡi nhẹ giữa hai răng, đẩy hơi qua khe lưỡi.",
                        "Luyện trước gương: th-th-this, think, three.");
            case "j":
                return new IpaProfile(symbol,
                        words("yes", "yellow", "music", "use"),
                        patterns("y", "u"),
                        "Lưỡi nâng cao gần ngạc cứng, âm lướt rất nhanh.",
                        "Luyện nối âm /j/ với nguyên âm: ya, ye, yo.");
            default:
                return new IpaProfile(symbol,
                        words(seedWord, "practice", "repeat", "speak"),
                        patterns(toSpeakableSymbol(symbol), seedWord),
                        "Giữ khẩu hình ổn định và đọc chậm rõ từng âm.",
                        "Đọc theo nhịp chậm 5 lần rồi tăng dần tốc độ.");
        }
    }

    @NonNull
    private List<String> words(@NonNull String... items) {
        List<String> output = new ArrayList<>();
        for (String item : items) {
            String safe = sanitizeWord(item);
            if (!safe.isEmpty() && !output.contains(safe)) {
                output.add(safe);
            }
        }
        return output;
    }

    @NonNull
    private List<String> patterns(@NonNull String... items) {
        List<String> output = new ArrayList<>();
        for (String item : items) {
            String safe = normalizeWord(item);
            if (!safe.isEmpty() && !output.contains(safe)) {
                output.add(safe);
            }
        }
        return output;
    }

    @Override
    protected void onDestroy() {
        logIpaSourceStats();
        stopListeningIfNeeded();
        mainHandler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        super.onDestroy();
    }

    private static class AttemptHistoryEntry {
        final String targetWord;
        final String spokenWord;
        final int overallScore;
        final int soundScore;
        final int statusLabelRes;

        AttemptHistoryEntry(@NonNull String targetWord,
                            @NonNull String spokenWord,
                            int overallScore,
                            int soundScore,
                            int statusLabelRes) {
            this.targetWord = targetWord;
            this.spokenWord = spokenWord;
            this.overallScore = overallScore;
            this.soundScore = soundScore;
            this.statusLabelRes = statusLabelRes;
        }
    }

    private static class IpaProfile {
        final String symbol;
        final List<String> practiceWords;
        final List<String> expectedPatterns;
        final String mouthHint;
        final String exerciseHint;

        IpaProfile(String symbol,
                   List<String> practiceWords,
                   List<String> expectedPatterns,
                   String mouthHint,
                   String exerciseHint) {
            this.symbol = symbol;
            this.practiceWords = practiceWords;
            this.expectedPatterns = expectedPatterns;
            this.mouthHint = mouthHint;
            this.exerciseHint = exerciseHint;
        }
    }

    private static class PronunciationAssessment {
        final int overallScore;
        final int soundScore;
        final int onsetScore;
        final int codaScore;
        final String diagnosis;
        final String exercisePlan;
        final String detectedConfusionSymbol;

        PronunciationAssessment(int overallScore,
                                int soundScore,
                                int onsetScore,
                                int codaScore,
                                String diagnosis,
                                String exercisePlan,
                                @NonNull String detectedConfusionSymbol) {
            this.overallScore = overallScore;
            this.soundScore = soundScore;
            this.onsetScore = onsetScore;
            this.codaScore = codaScore;
            this.diagnosis = diagnosis;
            this.exercisePlan = exercisePlan;
            this.detectedConfusionSymbol = detectedConfusionSymbol;
        }
    }

    private static class RecognitionCandidate {
        final String word;
        final float confidence;

        RecognitionCandidate(@NonNull String word, float confidence) {
            this.word = word;
            this.confidence = confidence;
        }

        boolean hasConfidence() {
            return confidence >= 0f;
        }

        @NonNull
        static RecognitionCandidate empty() {
            return new RecognitionCandidate("", -1f);
        }
    }

    private static class PracticeWordItem {
        final String word;
        final String ipa;

        PracticeWordItem(@NonNull String word, @NonNull String ipa) {
            this.word = word;
            this.ipa = ipa;
        }
    }

    private static class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {
        private final List<PracticeWordItem> words;
        private final Map<String, Integer> correctReadCountByWord;
        private String selectedWord;
        private int unlockedWordCount;
        private int passOverallThreshold;
        private int passSoundThreshold;
        private final OnWordActionListener listener;

        WordAdapter(@NonNull List<PracticeWordItem> words,
                @NonNull Map<String, Integer> correctReadCountByWord,
                    @NonNull String selectedWord,
                    int unlockedWordCount,
                int passOverallThreshold,
                int passSoundThreshold,
                    @NonNull OnWordActionListener listener) {
            this.words = words;
            this.correctReadCountByWord = correctReadCountByWord;
            this.selectedWord = selectedWord;
            this.unlockedWordCount = unlockedWordCount;
            this.passOverallThreshold = passOverallThreshold;
            this.passSoundThreshold = passSoundThreshold;
            this.listener = listener;
        }

        void setSelectedWord(@NonNull String selectedWord) {
            this.selectedWord = selectedWord;
            notifyDataSetChanged();
        }

        void setUnlockedWordCount(int unlockedWordCount) {
            this.unlockedWordCount = Math.max(1, unlockedWordCount);
            notifyDataSetChanged();
        }

        void setPassThresholds(int passOverallThreshold, int passSoundThreshold) {
            this.passOverallThreshold = passOverallThreshold;
            this.passSoundThreshold = passSoundThreshold;
            notifyDataSetChanged();
        }

        void setCorrectReadCount(@NonNull String word, int correctCount) {
            correctReadCountByWord.put(normalizeWordKey(word), Math.max(0, Math.min(REQUIRED_CORRECT_READS, correctCount)));
            notifyDataSetChanged();
        }

        private int getCorrectReadCount(@NonNull String word) {
            Integer count = correctReadCountByWord.get(normalizeWordKey(word));
            return count == null ? 0 : count;
        }

        @NonNull
        private String normalizeWordKey(@Nullable String word) {
            if (word == null) {
                return "";
            }
            return word.toLowerCase(Locale.US).replaceAll("[^a-z]", "").trim();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ipa_practice_word, parent, false);
            return new WordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            PracticeWordItem item = words.get(position);
            holder.wordText.setText(item.word);
            holder.ipaText.setText(item.ipa);
            holder.playButton.setContentDescription(
                    holder.itemView.getContext().getString(R.string.ipa_practice_play_word_format, item.word)
            );

            boolean locked = position >= unlockedWordCount;
            boolean passed = position < unlockedWordCount - 1;
            boolean selected = !locked && item.word.equalsIgnoreCase(selectedWord);
                int correctReadCount = getCorrectReadCount(item.word);
                int displayCorrectReadCount = passed
                    ? REQUIRED_CORRECT_READS
                    : Math.min(correctReadCount, REQUIRED_CORRECT_READS);

            int bgColor;
            int strokeColor;
            int wordColor;
            int ipaColor;
                int progressColor;
            int playTint;

            if (locked) {
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_surface_variant);
                strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_outline);
                wordColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary);
                ipaColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary);
                progressColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary);
                playTint = R.color.ef_text_tertiary;
            } else if (selected) {
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary_container);
                strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary);
                wordColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary_dark);
                ipaColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary);
                progressColor = displayCorrectReadCount >= REQUIRED_CORRECT_READS
                        ? ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success)
                        : ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary);
                playTint = R.color.ef_primary;
            } else if (passed) {
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_surface_variant);
                strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success);
                wordColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary);
                ipaColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_secondary);
                progressColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success);
                playTint = R.color.ef_secondary;
            } else {
                bgColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_surface);
                strokeColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_outline_light);
                wordColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary);
                ipaColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_secondary);
                progressColor = correctReadCount > 0
                        ? ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_orange_primary)
                        : ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary);
                playTint = R.color.ef_secondary;
            }

            holder.cardView.setCardBackgroundColor(bgColor);
            holder.cardView.setStrokeColor(strokeColor);
            holder.wordText.setTextColor(wordColor);
            holder.ipaText.setTextColor(ipaColor);
            holder.progressText.setText(
                    holder.itemView.getContext().getString(
                            R.string.ipa_practice_word_correct_progress_format,
                            displayCorrectReadCount,
                            REQUIRED_CORRECT_READS
                    )
            );
            holder.progressText.setTextColor(progressColor);
            holder.playButton.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), playTint));
            holder.playButton.setEnabled(!locked);
            holder.playButton.setAlpha(locked ? 0.45f : 1f);
            holder.itemView.setAlpha(locked ? 0.82f : 1f);

            if (locked) {
                holder.stateIcon.setVisibility(View.VISIBLE);
                holder.stateIcon.setImageResource(R.drawable.ic_lock_closed);
                holder.stateIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary));
            } else if (passed) {
                holder.stateIcon.setVisibility(View.VISIBLE);
                holder.stateIcon.setImageResource(R.drawable.ic_check_circle);
                holder.stateIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success));
            } else {
                holder.stateIcon.setVisibility(View.GONE);
                holder.stateIcon.clearColorFilter();
            }

            holder.itemView.setOnClickListener(v -> {
                if (locked) {
                    Toast.makeText(
                            v.getContext(),
                            v.getContext().getString(
                                    R.string.ipa_practice_word_locked_hint,
                                    position + 1,
                                    passOverallThreshold,
                                    passSoundThreshold,
                                        REQUIRED_CORRECT_READS
                            ),
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                listener.onWordSelected(item.word);
            });

            holder.playButton.setOnClickListener(v -> {
                if (locked) {
                    Toast.makeText(
                            v.getContext(),
                            v.getContext().getString(
                                    R.string.ipa_practice_word_locked_hint,
                                    position + 1,
                                    passOverallThreshold,
                                    passSoundThreshold,
                                        REQUIRED_CORRECT_READS
                            ),
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                listener.onPlayWord(item.word);
            });
        }

        @Override
        public int getItemCount() {
            return words.size();
        }

        static class WordViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView cardView;
            final TextView wordText;
            final TextView ipaText;
            final TextView progressText;
            final ImageButton playButton;
            final ImageView stateIcon;

            WordViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardPracticeWord);
                wordText = itemView.findViewById(R.id.tvPracticeWord);
                ipaText = itemView.findViewById(R.id.tvPracticeWordIpa);
                progressText = itemView.findViewById(R.id.tvPracticeWordProgress);
                playButton = itemView.findViewById(R.id.btnPlayPracticeWord);
                stateIcon = itemView.findViewById(R.id.ivWordState);
            }
        }
    }

    private interface OnWordActionListener {
        void onWordSelected(@NonNull String word);

        void onPlayWord(@NonNull String word);
    }
}
