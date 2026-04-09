package com.example.englishflow.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.GroqChatService;
import com.example.englishflow.data.LessonVocabulary;
import com.example.englishflow.data.MapLessonFlowStep;
import com.example.englishflow.util.VoiceFlowEngine;
import com.example.englishflow.ui.views.AiAvatar3dController;
import com.example.englishflow.ui.views.VoiceWaveformView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class MapConversationActivity extends AppCompatActivity {

    private static final double FREE_INPUT_VOCAB_MATCH_THRESHOLD = 0.88d;
    private static final float[] VOICE_SPEED_OPTIONS = new float[]{0.75f, 0.9f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final AtomicLong MESSAGE_ID_GENERATOR = new AtomicLong(1L);

    private enum LessonStage {
        LECTURE,
        PRACTICE
    }

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_PROMPT_KEY = "extra_prompt_key";
    public static final String EXTRA_ROLE_CONTEXT = "extra_role_context";
    public static final String EXTRA_MIN_EXCHANGES = "extra_min_exchanges";
    public static final String EXTRA_VOCAB_LIST = "extra_vocab_list";
    public static final String EXTRA_FLOW_STEPS = "extra_flow_steps";
    public static final String EXTRA_LESSON_KEYWORDS = "extra_lesson_keywords";
    public static final String EXTRA_MIN_LEVEL = "extra_min_level";

    private VoiceFlowEngine voiceFlowEngine;

    private TextView titleView;
    private TextView progressView;
    private ProgressBar mapProgressBar;
    private View ivFinishFlag;
    private TextView listeningStatus;
    private TextView timerText;
    private TextView speedPanel;
    private ImageButton micButton;
    private View btnType;
    private View btnInspiration;
    private View stageLectureDot;
    private View stagePracticeDot;
    private View stageConnector;
    private View stageLectureRow;
    private View stagePracticeRow;
    private TextView stageLectureText;
    private TextView stagePracticeText;
    private VoiceWaveformView heroWaveform;
    private AiAvatar3dController avatar3dController;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private GroqChatService groqChatService;

    private int minExchanges = 4;
    private int exchangeCount = 0;
    private String nodeId;
    private String currentTopic = "English Practice";
    private String customPrompt = "";
    private String roleContextFromIntent = "";

    private List<LessonVocabulary> lessonVocab = new ArrayList<>();
    private LessonVocabulary currentPracticingWord;
    private List<MapLessonFlowStep> flowSteps = new ArrayList<>();
    private final List<MapLessonFlowStep> quizSteps = new ArrayList<>();
    private final Map<String, MapLessonFlowStep> vocabStepByWord = new HashMap<>();
    private MapLessonFlowStep pendingQuizStep;
    private int quizStepIndex = 0;

    private List<String> lessonKeywords = new ArrayList<>();
    private String lessonMinLevel = "A1";
    private GroqChatService.RagContext ragContext;

    private boolean isRoleplayActive = false;
    private boolean isDoneShown = false;
    private boolean pendingPronunciationListen = false;
    private boolean isLessonClosed = false;
    private boolean vocabCompletionPromptShown = false;
    private boolean mapNodeCompletionSaved = false;
    private int voiceSpeedIndex = 0;
    private LessonStage currentLessonStage = null;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private final List<String> guidedIntroMessages = new ArrayList<>();
    private int guidedIntroCursor = 0;
    private Runnable guidedIntroOnDone;
    private boolean guidedIntroRunning = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startListening();
                } else {
                    Toast.makeText(this, "Cần quyền micro để luyện nói", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_conversation);

        titleView = findViewById(R.id.mapConversationTitle);
        progressView = findViewById(R.id.mapConversationProgress);
        mapProgressBar = findViewById(R.id.mapProgressBar);
        ivFinishFlag = findViewById(R.id.ivFinishFlag);
        listeningStatus = findViewById(R.id.mapListeningStatus);
        micButton = findViewById(R.id.mapMicButton);
        timerText = findViewById(R.id.mapTimerText);
        speedPanel = findViewById(R.id.speedPanel);
        btnType = findViewById(R.id.btnType);
        btnInspiration = findViewById(R.id.btnInspiration);
        stageLectureDot = findViewById(R.id.stageLectureDot);
        stagePracticeDot = findViewById(R.id.stagePracticeDot);
        stageConnector = findViewById(R.id.stageConnector);
        stageLectureRow = findViewById(R.id.stageLectureRow);
        stagePracticeRow = findViewById(R.id.stagePracticeRow);
        stageLectureText = findViewById(R.id.stageLectureText);
        stagePracticeText = findViewById(R.id.stagePracticeText);
        heroWaveform = findViewById(R.id.heroWaveform);
        chatRecyclerView = findViewById(R.id.mapChatHistory);

        WebView robotMascotScene = findViewById(R.id.robotMascotScene);
        View robotMascotFallback = findViewById(R.id.robotMascotFallback);
        if (robotMascotScene != null) {
            avatar3dController = new AiAvatar3dController(
                robotMascotScene,
                robotMascotFallback,
                1.45f,
                0.35f,
                -0.03f,
                0f
            );
            avatar3dController.loadModel();
        }

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setItemAnimator(null);
        chatRecyclerView.setItemViewCacheSize(20);
        chatRecyclerView.setAdapter(chatAdapter);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        currentTopic = title == null ? "English Practice" : title;
        nodeId = getIntent().getStringExtra(EXTRA_NODE_ID);
        minExchanges = getIntent().getIntExtra(EXTRA_MIN_EXCHANGES, 5);

        if (titleView != null) {
            titleView.setText(currentTopic);
        }

        roleContextFromIntent = nonEmpty(getIntent().getStringExtra(EXTRA_ROLE_CONTEXT));
        customPrompt = roleContextFromIntent;

        lessonVocab = castSerializableList(getIntent().getSerializableExtra(EXTRA_VOCAB_LIST));
        flowSteps = castSerializableList(getIntent().getSerializableExtra(EXTRA_FLOW_STEPS));

        ArrayList<String> passedKeywords = getIntent().getStringArrayListExtra(EXTRA_LESSON_KEYWORDS);
        lessonKeywords = passedKeywords == null ? new ArrayList<>() : new ArrayList<>(passedKeywords);

        lessonMinLevel = nonEmpty(getIntent().getStringExtra(EXTRA_MIN_LEVEL), "A1");

        groqChatService = new GroqChatService(this);
        ragContext = buildRagContext();

        voiceFlowEngine = new VoiceFlowEngine(this, new VoiceFlowEngine.VoiceCallback() {
            @Override
            public void onStateChanged(VoiceFlowEngine.State state) {
                runOnUiThread(() -> renderState(state));
            }

            @Override
            public void onTranscript(String text) {
                runOnUiThread(() -> handleLearnerInput(text));
            }

            @Override
            public void onPartialTranscript(String text) {
                runOnUiThread(() -> listeningStatus.setText(text));
            }

            @Override
            public void onRmsChanged(float rmsDb) {
                runOnUiThread(() -> {
                    if (heroWaveform != null) {
                        heroWaveform.setMicLevel(rmsDb);
                    }
                });
            }

            @Override
            public void onSpeakingDone() {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    if (guidedIntroRunning) {
                        playNextGuidedIntroStep();
                        return;
                    }
                    if (pendingPronunciationListen && currentPracticingWord != null) {
                        pendingPronunciationListen = false;
                        if (hasMicPermission()) {
                            voiceFlowEngine.startListening("en-US");
                            listeningStatus.setText("Đang lắng nghe: " + currentPracticingWord.getWord());
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                        }
                        return;
                    }
                    listeningStatus.setText(getString(R.string.map_conversation_hint));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    Toast.makeText(MapConversationActivity.this, "Voice Error: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });

        initializeHeroControls();

        findViewById(R.id.mapConversationBack).setOnClickListener(v -> exitLesson());

        startLesson();

        micButton.setOnClickListener(v -> {
            if (isLessonClosed) {
                return;
            }
            if (hasMicPermission()) {
                if (voiceFlowEngine.getState() == VoiceFlowEngine.State.LISTENING) {
                    voiceFlowEngine.stopListening();
                } else {
                    startListening();
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        btnType.setOnClickListener(v -> showTypeDialog());
        btnInspiration.setOnClickListener(v -> showInspirationDialog());

        startTimer();
    }

    @Override
    public void onBackPressed() {
        exitLesson();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (avatar3dController != null) {
            avatar3dController.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (avatar3dController != null) {
            avatar3dController.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopVoiceImmediately();
    }

    @Override
    protected void onDestroy() {
        isLessonClosed = true;
        stopVoiceImmediately();
        uiHandler.removeCallbacksAndMessages(null);
        if (avatar3dController != null) {
            avatar3dController.onDestroy();
            avatar3dController = null;
        }
        if (voiceFlowEngine != null) {
            voiceFlowEngine.shutdown();
            voiceFlowEngine = null;
        }
        super.onDestroy();
    }

    private void exitLesson() {
        if (isLessonClosed) {
            return;
        }
        isLessonClosed = true;
        stopVoiceImmediately();
        finish();
    }

    private void stopVoiceImmediately() {
        pendingPronunciationListen = false;
        guidedIntroRunning = false;
        guidedIntroMessages.clear();
        guidedIntroCursor = 0;
        guidedIntroOnDone = null;
        if (voiceFlowEngine != null) {
            voiceFlowEngine.setAutoRelisten(false);
            voiceFlowEngine.stopListening();
            voiceFlowEngine.stopSpeaking();
        }
        if (heroWaveform != null) {
            heroWaveform.resetMicLevel();
            heroWaveform.setMode(VoiceWaveformView.Mode.IDLE);
        }
    }

    private boolean canUseVoice() {
        return !isLessonClosed && !isFinishing() && voiceFlowEngine != null;
    }

    private void safeSpeak(String text) {
        if (!canUseVoice()) {
            return;
        }
        voiceFlowEngine.speakResponse(text);
    }

    private void initializeHeroControls() {
        if (heroWaveform != null) {
            heroWaveform.resetMicLevel();
            heroWaveform.setMode(VoiceWaveformView.Mode.IDLE);
        }

        if (speedPanel != null) {
            speedPanel.setOnClickListener(v -> cycleVoiceSpeed());
        }

        voiceSpeedIndex = 2; // 1.0x is now at index 2
        applySelectedVoiceSpeed(false);
        setLessonStage(LessonStage.LECTURE);
    }

    private void cycleVoiceSpeed() {
        voiceSpeedIndex = (voiceSpeedIndex + 1) % VOICE_SPEED_OPTIONS.length;
        applySelectedVoiceSpeed(true);
    }

    private void applySelectedVoiceSpeed(boolean showToast) {
        float speed = VOICE_SPEED_OPTIONS[voiceSpeedIndex];

        if (voiceFlowEngine != null) {
            voiceFlowEngine.setSpeechRateOverride(speed);
        }

        String speedLabel = formatSpeedLabel(speed);
        if (speedPanel != null) {
            speedPanel.setText(speedLabel);
        }

        if (showToast) {
            Toast.makeText(this, getString(R.string.map_conversation_speed_toast_format, speedLabel), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatSpeedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.001f) {
            return String.format(Locale.US, "%dx", (int) Math.round(speed));
        }
        if (Math.abs(speed * 2f - Math.round(speed * 2f)) < 0.001f) {
            return String.format(Locale.US, "%.1fx", speed);
        }
        return String.format(Locale.US, "%.2fx", speed);
    }

    private void setLessonStage(@NonNull LessonStage stage) {
        if (currentLessonStage == stage) {
            return;
        }
        currentLessonStage = stage;
        applyStageUi();
    }

    private void pushPracticeStage() {
        setLessonStage(LessonStage.PRACTICE);
    }

    private void applyStageUi() {
        if (stageLectureDot == null
                || stagePracticeDot == null
                || stageLectureText == null
                || stagePracticeText == null
                || stageLectureRow == null
                || stagePracticeRow == null) {
            return;
        }

        if (currentLessonStage == LessonStage.LECTURE) {
            stageLectureDot.setBackgroundResource(R.drawable.bg_step_active);
            stagePracticeDot.setBackgroundResource(R.drawable.bg_step_inactive);

            stageLectureRow.setAlpha(1f);
            stagePracticeRow.setAlpha(0.55f);
            if (stageConnector != null) {
                stageConnector.setAlpha(0.35f);
            }

            stageLectureText.setTypeface(Typeface.DEFAULT_BOLD);
            stagePracticeText.setTypeface(Typeface.DEFAULT);
            stageLectureText.setTextColor(ContextCompat.getColor(this, R.color.white));
            stagePracticeText.setTextColor(ContextCompat.getColor(this, R.color.white));
            return;
        }

        stageLectureDot.setBackgroundResource(R.drawable.bg_step_active);
        stagePracticeDot.setBackgroundResource(R.drawable.bg_step_active);

        stageLectureRow.setAlpha(0.78f);
        stagePracticeRow.setAlpha(1f);
        if (stageConnector != null) {
            stageConnector.setAlpha(0.72f);
        }

        stageLectureText.setTypeface(Typeface.DEFAULT);
        stagePracticeText.setTypeface(Typeface.DEFAULT_BOLD);
        stageLectureText.setTextColor(ContextCompat.getColor(this, R.color.white));
        stagePracticeText.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void updateWaveformForState(@NonNull VoiceFlowEngine.State state) {
        if (heroWaveform == null) {
            return;
        }

        switch (state) {
            case LISTENING:
                heroWaveform.setMode(VoiceWaveformView.Mode.LISTENING);
                break;
            case PROCESSING:
                heroWaveform.setMode(VoiceWaveformView.Mode.PROCESSING);
                break;
            case SPEAKING:
                heroWaveform.setMode(VoiceWaveformView.Mode.SPEAKING);
                break;
            case IDLE:
            default:
                heroWaveform.resetMicLevel();
                heroWaveform.setMode(VoiceWaveformView.Mode.IDLE);
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castSerializableList(Object value) {
        if (value instanceof ArrayList) {
            return new ArrayList<>((ArrayList<T>) value);
        }
        if (value instanceof List) {
            return new ArrayList<>((List<T>) value);
        }
        return new ArrayList<>();
    }

    private void startLesson() {
        setLessonStage(LessonStage.LECTURE);

        if (lessonVocab == null) {
            lessonVocab = new ArrayList<>();
        }
        if (flowSteps == null) {
            flowSteps = new ArrayList<>();
        }

        if (!flowSteps.isEmpty()) {
            bootstrapFlowLesson();
            return;
        }

        if (lessonVocab.isEmpty()) {
            startProactiveChat();
            return;
        }

        ensureKeywordsFromVocabulary();
        ragContext = buildRagContext();

        List<String> introSequence = new ArrayList<>();
        introSequence.add("Chào mừng đến với bài học " + currentTopic + "! Trước hết, hãy luyện tập các từ vựng quan trọng sau nhé.");
        introSequence.add("Bước học từ vựng: nhấn mic cạnh từng từ để luyện phát âm.");
        playGuidedIntroSequence(introSequence, 0, () -> showVocabularyHubCard("Luyện tập từ vựng"));
    }

    private void bootstrapFlowLesson() {
        quizSteps.clear();
        quizStepIndex = 0;
        pendingQuizStep = null;
        vocabStepByWord.clear();

        List<String> introMessages = new ArrayList<>();

        for (LessonVocabulary vocab : lessonVocab) {
            if (vocab != null && vocab.getWord() != null) {
                vocabStepByWord.put(normalizeForCompare(vocab.getWord()), null);
            }
        }

        for (MapLessonFlowStep step : flowSteps) {
            if (step == null) {
                continue;
            }
            String type = step.getTypeNormalized();
            if ("intro".equals(type)) {
                if (!step.getContent().isEmpty()) {
                    introMessages.add(step.getContent());
                }
            } else if ("vocabulary".equals(type) || "vocab".equals(type)) {
                appendVocabularyFromStep(step);
            } else if ("situational_quiz".equals(type) || "quiz".equals(type)) {
                quizSteps.add(step);
            }

            if (roleContextFromIntent.isEmpty() && !step.getRoleContext().isEmpty()) {
                roleContextFromIntent = step.getRoleContext();
            }

            if (!step.getExpectedKeyword().isEmpty()) {
                addKeywordsFromDelimited(step.getExpectedKeyword());
            }
            for (String accepted : step.getAcceptedAnswers()) {
                addKeyword(accepted);
            }
        }

        ensureKeywordsFromVocabulary();
        ragContext = buildRagContext();

        List<String> scriptedIntro = new ArrayList<>();
        if (introMessages.isEmpty()) {
            scriptedIntro.add("Bắt đầu hành trình " + currentTopic + " nào. Mình sẽ đi qua 3 bước: Học, Hành, Kiểm tra.");
        } else {
            scriptedIntro.addAll(introMessages);
        }

        Runnable afterIntro = () -> {
            if (!lessonVocab.isEmpty()) {
                String guided = "Bước học từ vựng: nhấn mic cạnh từng từ để luyện phát âm. Hoàn thành xong mình sẽ mở phần kiểm tra.";
                playGuidedIntroSequence(
                        Collections.singletonList(guided),
                        0,
                        () -> showVocabularyHubCard("Luyện phát âm từ khóa")
                );
                return;
            }

            if (hasPendingQuiz()) {
                String leadIn = "Bắt đầu phần kiểm tra tình huống nhé.";
                playGuidedIntroSequence(
                        Collections.singletonList(leadIn),
                        0,
                        this::promptNextQuizStep
                );
                return;
            }

            startProactiveChat();
        };

        playGuidedIntroSequence(scriptedIntro, 0, afterIntro);
    }

    private void playGuidedIntroSequence(List<String> messages, int index, Runnable onDone) {
        if (isLessonClosed || isFinishing()) {
            return;
        }

        List<String> normalized = new ArrayList<>();
        if (messages != null) {
            for (String item : messages) {
                String text = nonEmpty(item);
                if (!text.isEmpty()) {
                    normalized.add(text);
                }
            }
        }

        if (normalized.isEmpty() || index >= normalized.size()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }

        guidedIntroMessages.clear();
        guidedIntroMessages.addAll(normalized);
        guidedIntroCursor = Math.max(0, index);
        guidedIntroOnDone = onDone;
        guidedIntroRunning = true;
        playNextGuidedIntroStep();
    }

    private void playNextGuidedIntroStep() {
        if (!guidedIntroRunning) {
            return;
        }
        if (isLessonClosed || isFinishing()) {
            completeGuidedIntroSequence(false);
            return;
        }

        while (guidedIntroCursor < guidedIntroMessages.size()) {
            String current = nonEmpty(guidedIntroMessages.get(guidedIntroCursor));
            guidedIntroCursor++;
            if (current.isEmpty()) {
                continue;
            }
            typewriterMessage(current);
            safeSpeak(current);
            return;
        }

        completeGuidedIntroSequence(true);
    }

    private void completeGuidedIntroSequence(boolean runCompletion) {
        Runnable completion = guidedIntroOnDone;
        guidedIntroRunning = false;
        guidedIntroMessages.clear();
        guidedIntroCursor = 0;
        guidedIntroOnDone = null;

        if (runCompletion && completion != null && !isLessonClosed && !isFinishing()) {
            completion.run();
        }
    }

    private void showVocabularyHubCard(String title) {
        ChatMessage hub = new ChatMessage(title, true);
        hub.isVocabHub = true;
        hub.nodeVocabEntries = lessonVocab;
        addMessage(hub);
    }

    private void appendVocabularyFromStep(MapLessonFlowStep step) {
        if (step == null || step.getWord().isEmpty()) {
            return;
        }

        String normalized = normalizeForCompare(step.getWord());
        vocabStepByWord.put(normalized, step);

        boolean exists = false;
        for (LessonVocabulary vocab : lessonVocab) {
            if (normalizeForCompare(vocab.getWord()).equals(normalized)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            lessonVocab.add(new LessonVocabulary(
                    step.getWord(),
                    step.getIpa(),
                    step.getMeaning(),
                    step.getContent()
            ));
        }

        addKeyword(step.getWord());
    }

    private void ensureKeywordsFromVocabulary() {
        for (LessonVocabulary vocab : lessonVocab) {
            if (vocab != null) {
                addKeyword(vocab.getWord());
            }
        }
    }

    private void addKeyword(String raw) {
        String value = nonEmpty(raw);
        if (value.isEmpty()) {
            return;
        }
        if (lessonKeywords == null) {
            lessonKeywords = new ArrayList<>();
        }
        for (String item : lessonKeywords) {
            if (item != null && item.equalsIgnoreCase(value)) {
                return;
            }
        }
        lessonKeywords.add(value);
    }

    private void addKeywordsFromDelimited(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        String[] parts = raw.split("[,|/]");
        for (String part : parts) {
            addKeyword(part);
        }
    }

    private void practiceWord(LessonVocabulary vocab) {
        if (!canUseVoice()) {
            return;
        }
        currentPracticingWord = vocab;
        String instruction = "Nhấn mic và đọc theo";

        // Keep Vietnamese coaching and English target in separate chunks for cleaner TTS pronunciation.
        String prompt = instruction + ". " + vocab.getWord() + ". " + vocab.getWord() + ".";

        pendingPronunciationListen = true;
        safeSpeak(prompt);
        listeningStatus.setText("Đang lắng nghe: " + vocab.getWord());
    }

    private void evaluatePronunciation(String text) {
        if (currentPracticingWord == null) {
            return;
        }

        String targetWord = currentPracticingWord.getWord();
        double score = computePronunciationScore(text, targetWord);
        MapLessonFlowStep step = vocabStepByWord.get(normalizeForCompare(targetWord));

        if (score >= 0.90d) {
            safeSpeak("Tuyệt vời! Bạn phát âm đúng rồi.");
            currentPracticingWord.setPracticed(true);
            currentPracticingWord = null;
            notifyVocabularyHubChanged();

            if (areAllVocabularyPracticed()) {
                onVocabularyCompleted();
            }
            return;
        }

        String hint = step != null ? nonEmpty(step.getHint()) : "";
        String feedback = hint.isEmpty()
                ? "Oops, thử lại nhé. Hãy đọc rõ cụm: " + targetWord
                : "Oops, thử lại nhé. " + hint;
        addMessage(new ChatMessage(feedback, true));

        String retryVoice = hint.isEmpty()
            ? "Bạn đọc chưa rõ. Hãy đọc lại cụm: " + targetWord
            : "Bạn đọc chưa rõ. " + hint + " Hãy đọc lại nhé.";
        pendingPronunciationListen = true;
        listeningStatus.setText("Flow đang nhắc lại, chuẩn bị bật mic...");
        safeSpeak(retryVoice);
    }

    private boolean areAllVocabularyPracticed() {
        if (lessonVocab == null || lessonVocab.isEmpty()) {
            return true;
        }
        for (LessonVocabulary vocab : lessonVocab) {
            if (vocab != null && !vocab.isPracticed()) {
                return false;
            }
        }
        return true;
    }

    private void onVocabularyCompleted() {
        if (vocabCompletionPromptShown) {
            return;
        }

        vocabCompletionPromptShown = true;
        pendingPronunciationListen = false;

        String doneLine = "Bạn đã hoàn thành học từ vựng, bắt đầu phần kiểm tra nhé.";
        typewriterMessage(doneLine);
        safeSpeak(doneLine);

        if (hasPendingQuiz()) {
            showQuizCtaMessage();
        }
    }

    private void showQuizCtaMessage() {
        for (ChatMessage message : chatMessages) {
            if (message != null && message.isQuizCta) {
                return;
            }
        }

        ChatMessage cta = new ChatMessage("", true);
        cta.isQuizCta = true;
        addMessage(cta);
    }

    private void handleQuizCtaClick(ChatMessage ctaMessage) {
        if (ctaMessage == null || ctaMessage.quizCtaProcessing) {
            return;
        }

        ctaMessage.quizCtaProcessing = true;
        int position = chatMessages.indexOf(ctaMessage);
        if (position >= 0) {
            chatAdapter.notifyItemChanged(position);
        }

        if (!areAllVocabularyPracticed()) {
            ctaMessage.quizCtaProcessing = false;
            if (position >= 0) {
                chatAdapter.notifyItemChanged(position);
            }
            addMessage(new ChatMessage("Bạn cần luyện xong tất cả từ vựng trước khi vào kiểm tra.", true));
            return;
        }

        if (!hasPendingQuiz()) {
            ctaMessage.quizCtaProcessing = false;
            if (position >= 0) {
                chatAdapter.notifyItemChanged(position);
            }
            startProactiveChat();
            return;
        }

        pushPracticeStage();
        playGuidedIntroSequence(
                Collections.singletonList("Mình mở phần kiểm tra ngay cho bạn."),
                0,
                () -> {
                    if (pendingQuizStep == null) {
                        promptNextQuizStep();
                    }
                }
        );
    }

    private boolean tryCaptureVocabularyFromFreeInput(String text) {
        if (lessonVocab == null || lessonVocab.isEmpty()) {
            return false;
        }

        LessonVocabulary bestMatch = null;
        double bestScore = 0d;

        for (LessonVocabulary vocab : lessonVocab) {
            if (vocab == null || vocab.isPracticed()) {
                continue;
            }

            String target = nonEmpty(vocab.getWord());
            if (target.isEmpty()) {
                continue;
            }

            double score = computePronunciationScore(text, target);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = vocab;
            }
        }

        if (bestMatch == null || bestScore < FREE_INPUT_VOCAB_MATCH_THRESHOLD) {
            return false;
        }

        bestMatch.setPracticed(true);
        notifyVocabularyHubChanged();

        String confirmLine = "Tốt lắm, bạn phát âm đúng từ " + bestMatch.getWord() + ".";
        typewriterMessage(confirmLine);
        safeSpeak(confirmLine);

        if (areAllVocabularyPracticed()) {
            onVocabularyCompleted();
        }
        return true;
    }

    private boolean hasPendingQuiz() {
        return pendingQuizStep != null || quizStepIndex < quizSteps.size();
    }

    private void onVocabularyHubReady() {
        if (!areAllVocabularyPracticed()) {
            addMessage(new ChatMessage("Bạn cần luyện xong tất cả từ vựng trước khi sang bước tiếp theo.", true));
            return;
        }

        if (hasPendingQuiz()) {
            pushPracticeStage();
            playGuidedIntroSequence(
                    Collections.singletonList("Mình chuyển sang phần kiểm tra nhé."),
                    0,
                    () -> {
                        if (pendingQuizStep == null) {
                            promptNextQuizStep();
                        }
                    }
            );
            return;
        }

        startProactiveChat();
    }

    private void promptNextQuizStep() {
        pushPracticeStage();

        if (pendingQuizStep != null) {
            return;
        }

        if (quizStepIndex >= quizSteps.size()) {
            startProactiveChat();
            return;
        }

        pendingQuizStep = quizSteps.get(quizStepIndex);
        clearQuizCtaProcessingState();
        String question = nonEmpty(pendingQuizStep.getQuestion(), pendingQuizStep.getContent());
        if (question.isEmpty()) {
            question = "Hãy trả lời bằng từ khóa phù hợp với tình huống.";
        }

        typewriterMessage(question);
        safeSpeak(question);
    }

    private void evaluateQuizAnswer(String text) {
        if (pendingQuizStep == null) {
            return;
        }

        Set<String> expected = buildExpectedAnswers(pendingQuizStep);
        String normalizedInput = normalizeForCompare(text);
        boolean isCorrect = false;

        for (String answer : expected) {
            if (answer.isEmpty()) {
                continue;
            }
            if (normalizedInput.contains(answer) || computePronunciationScore(normalizedInput, answer) >= 0.86d) {
                isCorrect = true;
                break;
            }
        }

        if (isCorrect) {
            final String successLine = "Chính xác! Bạn đã vượt qua phần kiểm tra.";
            final String transitionLine = "Tuyệt! Giờ mình bước vào phần hội thoại thực chiến.";
            pendingQuizStep = null;
            quizStepIndex++;

            if (hasPendingQuiz()) {
                playGuidedIntroSequence(Collections.singletonList(successLine), 0, this::promptNextQuizStep);
            } else {
                playGuidedIntroSequence(Arrays.asList(successLine, transitionLine), 0, this::startProactiveChat);
            }
            return;
        }

        String hint = nonEmpty(pendingQuizStep.getHint(), "Thử lại và nhớ dùng từ khóa quan trọng.");
        String feedback = "Chưa đúng rồi. " + hint;
        addMessage(new ChatMessage(feedback, true));
        safeSpeak("Chưa đúng rồi, thử lại nhé.");
    }

    private Set<String> buildExpectedAnswers(MapLessonFlowStep step) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (step == null) {
            return values;
        }

        String raw = nonEmpty(step.getExpectedKeyword());
        if (!raw.isEmpty()) {
            String[] parts = raw.split("[,|/]");
            for (String part : parts) {
                String normalized = normalizeForCompare(part);
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
            if (values.isEmpty()) {
                values.add(normalizeForCompare(raw));
            }
        }

        for (String answer : step.getAcceptedAnswers()) {
            String normalized = normalizeForCompare(answer);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }

        return values;
    }

    private void handleLearnerInput(String rawText) {
        String text = nonEmpty(rawText);
        if (text.isEmpty()) {
            return;
        }

        addMessage(new ChatMessage(text, false));

        if (currentPracticingWord != null) {
            evaluatePronunciation(text);
            return;
        }

        if (pendingQuizStep != null) {
            evaluateQuizAnswer(text);
            return;
        }

        if (!isRoleplayActive) {
            if (tryCaptureVocabularyFromFreeInput(text)) {
                return;
            }
            addMessage(new ChatMessage("Hãy hoàn thành phần học từ vựng và kiểm tra trước khi hội thoại nhập vai.", true));
            return;
        }

        sendRoleplayMessage(text);
    }

    private void sendRoleplayMessage(String text) {
        exchangeCount++;
        updateProgress();

        groqChatService.getChatResponse(text, currentTopic, customPrompt, chatMessages, ragContext, new GroqChatService.ChatCallback() {
            @Override
            public void onSuccess(String response,
                                  String correction,
                                  String explanation,
                                  String vocabWord,
                                  String vocabIpa,
                                  String vocabMeaning,
                                  String vocabExample,
                                  String vocabExampleVi) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    typewriterMessage(response, correction, explanation, vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi, true);
                    safeSpeak(response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MapConversationActivity.this, "AI Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startProactiveChat() {
        pushPracticeStage();

        if (isRoleplayActive) {
            return;
        }

        isRoleplayActive = true;
        customPrompt = buildRoleplayPrompt();
        ragContext = buildRagContext();
        notifyVocabularyHubChanged();

        String openingPrompt = buildOpeningPrompt();
        groqChatService.getRawAiResponse(openingPrompt, new GroqChatService.RawCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    typewriterMessage(rawResponse, true);
                    safeSpeak(rawResponse);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    String fallback = "Hello! I'm ready to practice " + currentTopic + " with you.";
                    typewriterMessage(fallback, true);
                    safeSpeak(fallback);
                });
            }
        });
    }

    private String buildRoleplayPrompt() {
        String scenario = resolveRoleContext();
        StringBuilder prompt = new StringBuilder();
        prompt.append("ROLEPLAY SCENARIO: ").append(scenario).append("\n");
        prompt.append("LEARNER LEVEL: ").append(lessonMinLevel).append("\n");
        prompt.append("GUIDELINES:\n");
        prompt.append("1. Stay in role and keep responses practical.\n");
        prompt.append("2. Use short, natural English (1-3 sentences).\n");
        prompt.append("3. Ask exactly one follow-up question when appropriate.\n");
        if (!lessonKeywords.isEmpty()) {
            prompt.append("4. Encourage usage of these keywords: ").append(String.join(", ", lessonKeywords)).append("\n");
        }
        return prompt.toString();
    }

    private String buildOpeningPrompt() {
        String scenario = resolveRoleContext();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Act as this role: ").append(scenario).append("\n");
        prompt.append("Learner CEFR level: ").append(lessonMinLevel).append("\n");
        if (!lessonKeywords.isEmpty()) {
            prompt.append("Try to naturally include one keyword: ")
                    .append(lessonKeywords.get(0))
                    .append("\n");
        }
        prompt.append("Give one short opening line in English to start the roleplay. Return plain text only.");
        return prompt.toString();
    }

    private String resolveRoleContext() {
        if (!roleContextFromIntent.isEmpty()) {
            return roleContextFromIntent;
        }

        for (MapLessonFlowStep step : flowSteps) {
            if (step != null && !step.getRoleContext().isEmpty()) {
                return step.getRoleContext();
            }
        }

        return "You are Flow, a supportive English coach guiding the learner through daily situations.";
    }

    private GroqChatService.RagContext buildRagContext() {
        GroqChatService.RagContext context = new GroqChatService.RagContext();
        context.lessonId = nonEmpty(nodeId, "journey_lesson");
        context.lessonTitle = nonEmpty(currentTopic, "English Practice");
        context.minLevel = nonEmpty(lessonMinLevel, "A1");
        context.scenario = resolveRoleContext();
        context.keywords = new ArrayList<>(lessonKeywords);

        LinkedHashSet<String> expectedExpressions = new LinkedHashSet<>();
        for (LessonVocabulary vocab : lessonVocab) {
            if (vocab != null) {
                String word = nonEmpty(vocab.getWord());
                if (!word.isEmpty()) {
                    expectedExpressions.add(word);
                }
            }
        }
        for (MapLessonFlowStep step : flowSteps) {
            if (step == null) {
                continue;
            }
            String expected = nonEmpty(step.getExpectedKeyword());
            if (!expected.isEmpty()) {
                expectedExpressions.add(expected);
            }
        }
        context.expectedExpressions = new ArrayList<>(expectedExpressions);

        List<String> knowledgeChunks = new ArrayList<>();
        for (MapLessonFlowStep step : flowSteps) {
            if (step == null) {
                continue;
            }
            if (!step.getContent().isEmpty()) {
                knowledgeChunks.add(step.getContent());
            }
            if (!step.getQuestion().isEmpty()) {
                knowledgeChunks.add(step.getQuestion());
            }
            if (!step.getWord().isEmpty()) {
                String chunk = step.getWord();
                if (!step.getMeaning().isEmpty()) {
                    chunk += " = " + step.getMeaning();
                }
                knowledgeChunks.add(chunk);
            }
        }
        context.knowledgeChunks = knowledgeChunks;
        return context;
    }

    private double computePronunciationScore(String rawInput, String rawTarget) {
        String input = normalizeForCompare(rawInput);
        String target = normalizeForCompare(rawTarget);
        if (input.isEmpty() || target.isEmpty()) {
            return 0d;
        }

        if (input.contains(target)) {
            return 1d;
        }

        double best = computeTextSimilarity(input, target);

        String[] inputTokens = input.split(" ");
        String[] targetTokens = target.split(" ");
        int n = targetTokens.length;
        if (n > 0 && inputTokens.length >= n) {
            for (int i = 0; i <= inputTokens.length - n; i++) {
                StringBuilder candidate = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) {
                        candidate.append(' ');
                    }
                    candidate.append(inputTokens[i + j]);
                }
                double score = computeTextSimilarity(candidate.toString(), target);
                if (score > best) {
                    best = score;
                }
            }
        }

        return best;
    }

    private double computeTextSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1d;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1d;
        }
        int distance = levenshteinDistance(a, b);
        return 1d - ((double) distance / (double) maxLen);
    }

    private int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[b.length()];
    }

    private String normalizeForCompare(String text) {
        String value = nonEmpty(text).toLowerCase(Locale.US);
        value = value.replaceAll("[^a-z0-9\\s]", " ");
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    private void startTimer() {
        final long startTime = System.currentTimeMillis();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLessonClosed || isFinishing()) {
                    return;
                }
                long elapsed = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsed / 1000) % 60;
                int minutes = (int) (elapsed / (1000 * 60));
                timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(timerRunnable);
    }

    private void startListening() {
        if (!canUseVoice()) {
            return;
        }
        voiceFlowEngine.startListening(resolveListeningLocale());
        listeningStatus.setText("Đang lắng nghe...");
    }

    private String resolveListeningLocale() {
        if (currentPracticingWord != null || pendingQuizStep != null || isRoleplayActive) {
            return "en-US";
        }
        return "vi-VN";
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateProgress() {
        if (progressView != null) {
            String status = exchangeCount + "/" + minExchanges;
            progressView.setText(status);

            if (mapProgressBar != null) {
                int progress = (int) ((float) exchangeCount / minExchanges * 100);
                mapProgressBar.setProgress(Math.min(100, progress));
            }

            if (exchangeCount >= minExchanges) {
                ivFinishFlag.setAlpha(1.0f);
                ensureNodeCompletionSaved();
                if (isRoleplayActive && !isDoneShown) {
                    isDoneShown = true;
                    showCompletionDialog();
                }
            }
        }
    }

    private void showCompletionDialog() {
        if (isFinishing()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_lesson_complete, null);

        ((TextView) v.findViewById(R.id.tvCompleteTitle)).setText("Bài học hoàn tất!");
        ((TextView) v.findViewById(R.id.tvCompleteMetrics)).setText(
                "Bạn đã thực hiện " + exchangeCount + " lượt hội thoại và nắm vững " +
                        (lessonVocab != null ? lessonVocab.size() : 0) + " từ vựng."
        );

        v.findViewById(R.id.btnFinishLesson).setOnClickListener(view -> {
            ensureNodeCompletionSaved();
            dialog.dismiss();
            exitLesson();
        });

        dialog.setContentView(v);
        dialog.show();
    }

    private void renderState(@NonNull VoiceFlowEngine.State state) {
        updateWaveformForState(state);

        switch (state) {
            case LISTENING:
                micButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
                listeningStatus.setVisibility(View.VISIBLE);
                listeningStatus.setText("Đang lắng nghe...");
                break;
            case SPEAKING:
                micButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                listeningStatus.setVisibility(View.VISIBLE);
                listeningStatus.setText("Flow đang phản hồi...");
                break;
            case PROCESSING:
                listeningStatus.setVisibility(View.VISIBLE);
                listeningStatus.setText("Đang xử lý...");
                break;
            case IDLE:
            default:
                micButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                listeningStatus.setVisibility(View.INVISIBLE);
                break;
        }
    }

    private void addMessage(ChatMessage message) {
        chatMessages.add(message);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollChatToLatest(true);
    }

    private void typewriterMessage(String fullText) {
        typewriterMessage(fullText, false);
    }

    private void typewriterMessage(String fullText, boolean enableHintCoach) {
        typewriterMessage(fullText, null, null, null, null, null, null, null, enableHintCoach);
    }

    private void typewriterMessage(String fullText,
                                   String correction,
                                   String explanation,
                                   String vocabWord,
                                   String vocabIpa,
                                   String vocabMeaning,
                                   String vocabExample,
                                   String vocabExampleVi,
                                   boolean enableHintCoach) {
        String safeText = fullText == null ? "" : fullText;

        ChatMessage message = new ChatMessage(safeText, true);
        message.correction = correction;
        message.explanation = explanation;
        message.vocabWord = vocabWord;
        message.vocabIpa = vocabIpa;
        message.vocabMeaning = vocabMeaning;
        message.vocabExample = vocabExample;
        message.vocabExampleVi = vocabExampleVi;
        message.hintEligible = enableHintCoach && isHintEligibleText(safeText);
        message.hintReady = false;
        message.text = "";
        message.fullText = safeText;

        chatMessages.add(message);
        int position = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(position);
        scrollChatToLatest(true);

        final int textLength = safeText.length();
        final int charStep = textLength > 240 ? 4 : (textLength > 120 ? 3 : (textLength > 60 ? 2 : 1));
        final long frameDelayMs = textLength > 240 ? 12L : (textLength > 120 ? 16L : 24L);
        final int[] index = {0};
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isLessonClosed || isFinishing()) {
                    return;
                }
                if (index[0] < textLength) {
                    index[0] = Math.min(textLength, index[0] + charStep);
                    message.text = safeText.substring(0, index[0]);
                    chatAdapter.notifyItemChanged(position, ChatAdapter.PAYLOAD_TEXT_ONLY);
                    uiHandler.postDelayed(this, frameDelayMs);
                    return;
                }

                if (message.hintEligible && !message.hintReady) {
                    message.hintReady = true;
                    chatAdapter.notifyItemChanged(position);
                    scrollChatToLatest(false);
                }
            }
        });
    }

    private boolean isHintEligibleText(String text) {
        String value = nonEmpty(text);
        if (value.isEmpty()) {
            return false;
        }

        int englishWordCount = 0;
        int totalWordCount = 0;
        String lower = value.toLowerCase(Locale.US);
        String[] tokens = lower.split("\\s+");
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^a-zàáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            totalWordCount++;
            if (cleaned.matches("[a-z']+")) {
                englishWordCount++;
            }
        }

        if (totalWordCount == 0) {
            return false;
        }

        double ratio = (double) englishWordCount / (double) totalWordCount;
        return ratio >= 0.70d;
    }

    private void onHintCoachClicked(ChatMessage message) {
        if (message == null || !message.hintEligible || !message.hintReady) {
            return;
        }

        int position = chatMessages.indexOf(message);
        if (position < 0) {
            return;
        }

        if (message.hintExpanded && !message.hintLoading) {
            message.hintExpanded = false;
            chatAdapter.notifyItemChanged(position);
            return;
        }

        if (message.hintTranslationVi != null && !message.hintTranslationVi.isEmpty() && !message.hintLoading) {
            message.hintExpanded = true;
            chatAdapter.notifyItemChanged(position);
            speakHintCoach(message);
            return;
        }

        if (message.hintLoading) {
            return;
        }

        message.hintLoading = true;
        message.hintExpanded = true;
        chatAdapter.notifyItemChanged(position);

        String sourceText = nonEmpty(message.fullText, message.text).replace("\"", "\\\\\"");

        String prompt = "Bạn là trợ giảng tiếng Anh. Hãy phân tích câu sau cho người mới học: \""
            + sourceText
                + "\"\\n"
                + "Trả đúng 2 dòng, không đánh số:\\n"
                + "VI: <bản dịch tiếng Việt tự nhiên, ngắn gọn>\\n"
                + "COACH: <hướng dẫn bằng tiếng Việt để người học trả lời tiếp + 1 câu mẫu tiếng Anh ngắn>.";

        groqChatService.getRawAiResponse(prompt, new GroqChatService.RawCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    applyHintCoachResponse(message, rawResponse, true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    applyHintCoachResponse(message, null, false);
                });
            }
        });
    }

    private void applyHintCoachResponse(ChatMessage message, String rawResponse, boolean fromAi) {
        if (message == null) {
            return;
        }

        String translation = null;
        String coach = null;

        if (fromAi && rawResponse != null) {
            String[] lines = rawResponse.split("\\r?\\n");
            List<String> plainLines = new ArrayList<>();
            for (String line : lines) {
                String cleaned = nonEmpty(line);
                if (cleaned.isEmpty()) {
                    continue;
                }

                plainLines.add(cleaned.replaceFirst("^[\\-•*]+\\s*", ""));

                String lower = cleaned.toLowerCase(Locale.US);
                if (lower.startsWith("vi:")) {
                    translation = nonEmpty(cleaned.substring(3));
                } else if (lower.startsWith("coach:")) {
                    coach = nonEmpty(cleaned.substring(6));
                }
            }

            if ((translation == null || translation.isEmpty()) && !plainLines.isEmpty()) {
                translation = plainLines.get(0).replaceFirst("(?i)^vi\\s*:\\s*", "").trim();
            }
            if ((coach == null || coach.isEmpty()) && plainLines.size() >= 2) {
                coach = plainLines.get(1).replaceFirst("(?i)^coach\\s*:\\s*", "").trim();
            }
        }

        if (translation == null || translation.isEmpty()) {
            translation = getString(R.string.map_conversation_hint_translation_fallback);
        }
        if (coach == null || coach.isEmpty()) {
            coach = getString(R.string.map_conversation_hint_coach_fallback);
        }

        message.hintLoading = false;
        message.hintExpanded = true;
        message.hintTranslationVi = translation;
        message.hintCoachVi = coach;

        int position = chatMessages.indexOf(message);
        if (position >= 0) {
            chatAdapter.notifyItemChanged(position);
            scrollChatToLatest(false);
        } else {
            int lastIndex = chatMessages.size() - 1;
            if (lastIndex >= 0) {
                chatAdapter.notifyItemChanged(lastIndex);
            }
        }

        speakHintCoach(message);
    }

    private void ensureNodeCompletionSaved() {
        if (mapNodeCompletionSaved) {
            return;
        }
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return;
        }
        AppRepository.getInstance(this).saveMapNodeCompleted(nodeId);
        mapNodeCompletionSaved = true;
    }

    private void notifyVocabularyHubChanged() {
        int position = findLatestMessagePosition(true, false);
        if (position >= 0) {
            chatAdapter.notifyItemChanged(position);
        }
    }

    private void notifyQuizCtaChanged() {
        int position = findLatestMessagePosition(false, true);
        if (position >= 0) {
            chatAdapter.notifyItemChanged(position);
        }
    }

    private void clearQuizCtaProcessingState() {
        boolean changed = false;
        for (ChatMessage message : chatMessages) {
            if (message != null && message.isQuizCta && message.quizCtaProcessing) {
                message.quizCtaProcessing = false;
                changed = true;
            }
        }
        if (changed) {
            notifyQuizCtaChanged();
        }
    }

    private int findLatestMessagePosition(boolean vocabHub, boolean quizCta) {
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = chatMessages.get(i);
            if (message == null) {
                continue;
            }
            if (vocabHub && message.isVocabHub) {
                return i;
            }
            if (quizCta && message.isQuizCta) {
                return i;
            }
        }
        return -1;
    }

    private void scrollChatToLatest(boolean smooth) {
        if (chatRecyclerView == null || chatMessages.isEmpty()) {
            return;
        }

        int target = chatMessages.size() - 1;
        RecyclerView.LayoutManager layoutManager = chatRecyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            int lastVisible = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
            boolean nearBottom = lastVisible >= target - 2;
            if (smooth && nearBottom) {
                chatRecyclerView.smoothScrollToPosition(target);
            } else {
                chatRecyclerView.scrollToPosition(target);
            }
            return;
        }

        chatRecyclerView.scrollToPosition(target);
    }

    private void speakHintCoach(ChatMessage message) {
        if (message == null) {
            return;
        }
        String translation = nonEmpty(message.hintTranslationVi);
        String coach = nonEmpty(message.hintCoachVi);
        String voice = "Bản dịch: " + translation + ". Gợi ý phản hồi: " + coach;
        safeSpeak(voice);
    }

    private void showTypeDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_map_type, null);
        dialog.setContentView(view);

        EditText edtMessage = view.findViewById(R.id.edtTypeMessage);
        view.findViewById(R.id.btnSendType).setOnClickListener(v -> {
            String text = edtMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                handleUserResponse(text);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showInspirationDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_map_inspiration, null);
        dialog.setContentView(view);

        ProgressBar loading = view.findViewById(R.id.suggestedLoading);
        TextView[] suggTexts = {
                view.findViewById(R.id.suggestedText1),
                view.findViewById(R.id.suggestedText2),
                view.findViewById(R.id.suggestedText3)
        };

        StringBuilder history = new StringBuilder();
        for (int i = Math.max(0, chatMessages.size() - 3); i < chatMessages.size(); i++) {
            history.append(chatMessages.get(i).isAi ? "AI: " : "User: ")
                    .append(chatMessages.get(i).text)
                    .append("\n");
        }

        String prompt = "Give me 3 very short, natural English response suggestions (max 10 words each) for a student in this chat:\n"
                + history + "\nReturn ONLY 3 lines of plain text, no numbering.";

        groqChatService.getRawAiResponse(prompt, new GroqChatService.RawCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    String[] lines = rawResponse.split("\\n");
                    int count = 0;
                    for (String line : lines) {
                        String clean = line.replaceAll("^\\d+[.)]\\s*", "").trim();
                        if (clean.isEmpty() || count >= 3) {
                            continue;
                        }

                        suggTexts[count].setText(clean);
                        suggTexts[count].setVisibility(View.VISIBLE);
                        suggTexts[count].setOnClickListener(v -> {
                            handleUserResponse(clean);
                            dialog.dismiss();
                        });
                        count++;
                    }
                    if (count == 0) {
                        suggTexts[0].setText("I'm ready to learn!");
                        suggTexts[0].setVisibility(View.VISIBLE);
                        suggTexts[0].setOnClickListener(v -> {
                            handleUserResponse("I'm ready to learn!");
                            dialog.dismiss();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    suggTexts[0].setText("I'm ready!");
                    suggTexts[0].setVisibility(View.VISIBLE);
                    suggTexts[0].setOnClickListener(v -> {
                        handleUserResponse("I'm ready!");
                        dialog.dismiss();
                    });
                });
            }
        });

        dialog.show();
    }

    private void handleUserResponse(String text) {
        handleLearnerInput(text);
    }

    private String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    public static class ChatMessage {
        public final long id;
        public String text;
        public String fullText;
        public boolean isAi;
        public String correction;
        public String explanation;
        public String vocabWord;
        public String vocabIpa;
        public String vocabMeaning;
        public String vocabExample;
        public String vocabExampleVi;

        public boolean isVocabHub = false;
        public List<LessonVocabulary> nodeVocabEntries;
        public boolean isQuizCta = false;
        public boolean quizCtaProcessing = false;

        public boolean hintEligible;
        public boolean hintReady;
        public boolean hintExpanded;
        public boolean hintLoading;
        public String hintTranslationVi;
        public String hintCoachVi;

        public ChatMessage(String text, boolean isAi) {
            this.id = MESSAGE_ID_GENERATOR.getAndIncrement();
            this.text = text;
            this.fullText = text;
            this.isAi = isAi;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_AI = 0;
        private static final int TYPE_USER = 1;
        private static final int TYPE_VOCAB_HUB = 2;
        private static final int TYPE_QUIZ_CTA = 3;
        private static final String PAYLOAD_TEXT_ONLY = "payload_text_only";

        private final List<ChatMessage> messages;

        ChatAdapter(List<ChatMessage> messages) {
            this.messages = messages;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return messages.get(position).id;
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage msg = messages.get(position);
            if (msg.isVocabHub) {
                return TYPE_VOCAB_HUB;
            }
            if (msg.isQuizCta) {
                return TYPE_QUIZ_CTA;
            }
            return msg.isAi ? TYPE_AI : TYPE_USER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_VOCAB_HUB) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_vocab_hub, parent, false);
                return new VocabHubViewHolder(v);
            }
            if (viewType == TYPE_QUIZ_CTA) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_quiz_cta, parent, false);
                return new QuizCtaViewHolder(v);
            }
            int layout = (viewType == TYPE_AI) ? R.layout.item_chat_ai : R.layout.item_chat_user;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return (viewType == TYPE_AI) ? new AiViewHolder(v) : new UserViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
                                     int position,
                                     @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && payloads.contains(PAYLOAD_TEXT_ONLY)) {
                ChatMessage msg = messages.get(position);
                if (holder instanceof AiViewHolder) {
                    ((AiViewHolder) holder).messageText.setText(msg.text);
                    return;
                }
                if (holder instanceof UserViewHolder) {
                    ((UserViewHolder) holder).messageText.setText(msg.text);
                    return;
                }
            }

            super.onBindViewHolder(holder, position, payloads);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);

            if (holder instanceof VocabHubViewHolder) {
                VocabHubViewHolder hub = (VocabHubViewHolder) holder;
                hub.container.removeAllViews();
                boolean allDone = true;

                for (LessonVocabulary vocab : msg.nodeVocabEntries) {
                    View row = LayoutInflater.from(MapConversationActivity.this)
                            .inflate(R.layout.item_vocab_row, hub.container, false);
                    ((TextView) row.findViewById(R.id.vocabWord)).setText(vocab.getWord());
                    ((TextView) row.findViewById(R.id.vocabMeaning)).setText(vocab.getIpa() + " • " + vocab.getMeaning());
                    row.findViewById(R.id.checkDone).setVisibility(vocab.isPracticed() ? View.VISIBLE : View.GONE);
                    row.findViewById(R.id.btnPracticeWord).setOnClickListener(view -> practiceWord(vocab));
                    if (!vocab.isPracticed()) {
                        allDone = false;
                    }
                    hub.container.addView(row);
                }

                final boolean ready = allDone;
                hub.btnStart.setEnabled(ready);
                hub.btnStart.setText(hasPendingQuiz() ? "Bắt đầu phần kiểm tra" : "Bắt đầu thực hành hội thoại");
                hub.btnStart.setVisibility(isRoleplayActive ? View.GONE : View.VISIBLE);
                hub.btnStart.setOnClickListener(v -> {
                    if (!ready) {
                        return;
                    }
                    onVocabularyHubReady();
                    hub.btnStart.setVisibility(View.GONE);
                    hub.btnStart.setEnabled(false);
                });
                return;
            }

            if (holder instanceof QuizCtaViewHolder) {
                QuizCtaViewHolder cta = (QuizCtaViewHolder) holder;
                cta.btnQuizNow.setEnabled(!msg.quizCtaProcessing);
                cta.btnQuizNow.setText(msg.quizCtaProcessing
                        ? getString(R.string.map_conversation_quiz_cta_processing)
                        : getString(R.string.map_conversation_quiz_cta_label));
                cta.btnQuizNow.setOnClickListener(v -> handleQuizCtaClick(msg));
                return;
            }

            if (holder instanceof AiViewHolder) {
                AiViewHolder ai = (AiViewHolder) holder;
                ai.messageText.setText(msg.text);

                boolean showHintButton = msg.hintEligible && msg.hintReady;
                ai.hintActionRow.setVisibility(showHintButton ? View.VISIBLE : View.GONE);
                ai.hintCoachBlock.setVisibility((msg.hintExpanded || msg.hintLoading) ? View.VISIBLE : View.GONE);
                ai.btnHintCoach.setOnClickListener(v -> onHintCoachClicked(msg));

                if (msg.hintExpanded || msg.hintLoading) {
                    if (msg.hintLoading) {
                        ai.hintTranslationText.setText(getString(R.string.map_conversation_hint_loading));
                        ai.hintCoachLabel.setVisibility(View.GONE);
                        ai.hintCoachText.setVisibility(View.GONE);
                    } else {
                        ai.hintTranslationText.setText(nonEmpty(msg.hintTranslationVi, getString(R.string.map_conversation_hint_translation_fallback)));
                        ai.hintCoachLabel.setVisibility(View.VISIBLE);
                        ai.hintCoachText.setVisibility(View.VISIBLE);
                        ai.hintCoachText.setText(nonEmpty(msg.hintCoachVi, getString(R.string.map_conversation_hint_coach_fallback)));
                    }
                }

                boolean hasVocab = msg.vocabWord != null && !msg.vocabWord.isEmpty();
                ai.vocabActionBar.setVisibility(hasVocab ? View.VISIBLE : View.GONE);
                ai.vocabDetailBlock.setVisibility(hasVocab ? View.VISIBLE : View.GONE);
                if (hasVocab) {
                    ai.tvVocabWord.setText(msg.vocabWord);
                    ai.tvVocabIpa.setText(msg.vocabIpa);
                    ai.tvVocabMeaning.setText(msg.vocabMeaning);
                    ai.tvVocabExample.setText(msg.vocabExample);
                    ai.tvVocabExampleVi.setText(msg.vocabExampleVi);

                    ai.btnSpeak.setOnClickListener(v -> safeSpeak(msg.vocabWord));
                    ai.btnSaveVocab.setOnClickListener(v -> Toast.makeText(MapConversationActivity.this, "Đã lưu từ vựng!", Toast.LENGTH_SHORT).show());
                }

                boolean hasCorrection = msg.correction != null && !msg.correction.isEmpty();
                ai.correctionBlock.setVisibility(hasCorrection ? View.VISIBLE : View.GONE);
                if (hasCorrection) {
                    ai.correctionText.setText("Sửa lỗi: " + msg.correction);
                    ai.correctionExplain.setText(msg.explanation);
                }
            } else {
                ((UserViewHolder) holder).messageText.setText(msg.text);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class AiViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            View vocabActionBar;
            View vocabDetailBlock;
            View correctionBlock;
            TextView tvVocabWord;
            TextView tvVocabIpa;
            TextView tvVocabMeaning;
            TextView tvVocabExample;
            TextView tvVocabExampleVi;
            TextView correctionText;
            TextView correctionExplain;
            View hintActionRow;
            View hintCoachBlock;
            TextView hintTranslationText;
            TextView hintCoachLabel;
            TextView hintCoachText;
            View btnSpeak;
            View btnSaveVocab;
            View btnHintCoach;

            AiViewHolder(View v) {
                super(v);
                messageText = v.findViewById(R.id.chatAiMessage);
                vocabActionBar = v.findViewById(R.id.vocabActionBar);
                vocabDetailBlock = v.findViewById(R.id.vocabDetailBlock);
                correctionBlock = v.findViewById(R.id.correctionBlock);
                tvVocabWord = v.findViewById(R.id.tvVocabWord);
                tvVocabIpa = v.findViewById(R.id.tvVocabIpa);
                tvVocabMeaning = v.findViewById(R.id.tvVocabMeaning);
                tvVocabExample = v.findViewById(R.id.tvVocabExample);
                tvVocabExampleVi = v.findViewById(R.id.tvVocabExampleVi);
                correctionText = v.findViewById(R.id.correctionText);
                correctionExplain = v.findViewById(R.id.correctionExplain);
                hintActionRow = v.findViewById(R.id.hintActionRow);
                hintCoachBlock = v.findViewById(R.id.hintCoachBlock);
                hintTranslationText = v.findViewById(R.id.hintTranslationText);
                hintCoachLabel = v.findViewById(R.id.hintCoachLabel);
                hintCoachText = v.findViewById(R.id.hintCoachText);
                btnSpeak = v.findViewById(R.id.btnSpeak);
                btnSaveVocab = v.findViewById(R.id.btnSaveVocab);
                btnHintCoach = v.findViewById(R.id.btnHintCoach);
            }
        }

        class VocabHubViewHolder extends RecyclerView.ViewHolder {
            LinearLayout container;
            com.google.android.material.button.MaterialButton btnStart;

            VocabHubViewHolder(View v) {
                super(v);
                container = v.findViewById(R.id.vocabListContainer);
                btnStart = v.findViewById(R.id.btnStartRoleplay);
            }
        }

        class QuizCtaViewHolder extends RecyclerView.ViewHolder {
            com.google.android.material.button.MaterialButton btnQuizNow;

            QuizCtaViewHolder(View v) {
                super(v);
                btnQuizNow = v.findViewById(R.id.btnQuizNow);
            }
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;

            UserViewHolder(View v) {
                super(v);
                messageText = v.findViewById(R.id.chatUserMessage);
            }
        }
    }
}
