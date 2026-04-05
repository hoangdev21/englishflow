package com.example.englishflow.ui;

import android.Manifest;
import android.content.pm.PackageManager;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MapConversationActivity extends AppCompatActivity {

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
    private ImageButton micButton;
    private View btnType;
    private View btnInspiration;

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
        btnType = findViewById(R.id.btnType);
        btnInspiration = findViewById(R.id.btnInspiration);
        chatRecyclerView = findViewById(R.id.mapChatHistory);

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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
                // This screen currently only needs textual status updates.
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
    protected void onStop() {
        super.onStop();
        stopVoiceImmediately();
    }

    @Override
    protected void onDestroy() {
        isLessonClosed = true;
        stopVoiceImmediately();
        uiHandler.removeCallbacksAndMessages(null);
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
            chatAdapter.notifyDataSetChanged();

            if (areAllVocabularyPracticed()) {
                String nextMessage = hasPendingQuiz()
                        ? "Bạn đã hoàn thành phần từ vựng. Nhấn nút bên dưới để vào phần kiểm tra."
                        : "Bạn đã hoàn thành phần từ vựng. Nhấn nút bên dưới để vào hội thoại nhập vai.";
                addMessage(new ChatMessage(nextMessage, true));
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

    private boolean hasPendingQuiz() {
        return pendingQuizStep != null || quizStepIndex < quizSteps.size();
    }

    private void onVocabularyHubReady() {
        if (!areAllVocabularyPracticed()) {
            addMessage(new ChatMessage("Bạn cần luyện xong tất cả từ vựng trước khi sang bước tiếp theo.", true));
            return;
        }

        if (hasPendingQuiz()) {
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
        if (pendingQuizStep != null) {
            return;
        }

        if (quizStepIndex >= quizSteps.size()) {
            startProactiveChat();
            return;
        }

        pendingQuizStep = quizSteps.get(quizStepIndex);
        String question = nonEmpty(pendingQuizStep.getQuestion(), pendingQuizStep.getContent());
        if (question.isEmpty()) {
            question = "Hãy trả lời bằng từ khóa phù hợp với tình huống.";
        }

        typewriterMessage(question);
        safeSpeak(question);
        chatAdapter.notifyDataSetChanged();
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
            addMessage(new ChatMessage("Chính xác! Bạn đã vượt qua phần kiểm tra.", true));
            safeSpeak("Excellent! Let's continue.");
            pendingQuizStep = null;
            quizStepIndex++;

            if (hasPendingQuiz()) {
                promptNextQuizStep();
            } else {
                addMessage(new ChatMessage("Tuyệt! Giờ mình bước vào phần hội thoại thực chiến.", true));
                startProactiveChat();
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
                    typewriterMessage(response, correction, explanation, vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi);
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
        if (isRoleplayActive) {
            return;
        }

        isRoleplayActive = true;
        customPrompt = buildRoleplayPrompt();
        ragContext = buildRagContext();
        chatAdapter.notifyDataSetChanged();

        String openingPrompt = buildOpeningPrompt();
        groqChatService.getRawAiResponse(openingPrompt, new GroqChatService.RawCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> {
                    if (isLessonClosed || isFinishing()) {
                        return;
                    }
                    typewriterMessage(rawResponse);
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
                    typewriterMessage(fallback);
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
                if (nodeId != null) {
                    AppRepository.getInstance(this).saveMapNodeCompleted(nodeId);
                }
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
            if (nodeId != null) {
                AppRepository.getInstance(this).saveMapNodeCompleted(nodeId);
            }
            dialog.dismiss();
            exitLesson();
        });

        dialog.setContentView(v);
        dialog.show();
    }

    private void renderState(@NonNull VoiceFlowEngine.State state) {
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
        chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
    }

    private void typewriterMessage(String fullText) {
        typewriterMessage(fullText, null, null, null, null, null, null, null);
    }

    private void typewriterMessage(String fullText,
                                   String correction,
                                   String explanation,
                                   String vocabWord,
                                   String vocabIpa,
                                   String vocabMeaning,
                                   String vocabExample,
                                   String vocabExampleVi) {
        String safeText = fullText == null ? "" : fullText;

        ChatMessage message = new ChatMessage(safeText, true);
        message.correction = correction;
        message.explanation = explanation;
        message.vocabWord = vocabWord;
        message.vocabIpa = vocabIpa;
        message.vocabMeaning = vocabMeaning;
        message.vocabExample = vocabExample;
        message.vocabExampleVi = vocabExampleVi;

        chatMessages.add(message);
        int position = chatMessages.size() - 1;
        chatAdapter.notifyItemInserted(position);
        chatRecyclerView.smoothScrollToPosition(position);

        final int[] index = {0};
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isLessonClosed || isFinishing()) {
                    return;
                }
                if (index[0] <= safeText.length()) {
                    message.text = safeText.substring(0, index[0]);
                    chatAdapter.notifyItemChanged(position);
                    index[0]++;
                    uiHandler.postDelayed(this, 25);
                }
            }
        });
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
        public String text;
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

        public ChatMessage(String text, boolean isAi) {
            this.text = text;
            this.isAi = isAi;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_AI = 0;
        private static final int TYPE_USER = 1;
        private static final int TYPE_VOCAB_HUB = 2;

        private final List<ChatMessage> messages;

        ChatAdapter(List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage msg = messages.get(position);
            if (msg.isVocabHub) {
                return TYPE_VOCAB_HUB;
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
            int layout = (viewType == TYPE_AI) ? R.layout.item_chat_ai : R.layout.item_chat_user;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return (viewType == TYPE_AI) ? new AiViewHolder(v) : new UserViewHolder(v);
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

            if (holder instanceof AiViewHolder) {
                AiViewHolder ai = (AiViewHolder) holder;
                ai.messageText.setText(msg.text);

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
            View btnSpeak;
            View btnSaveVocab;

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
                btnSpeak = v.findViewById(R.id.btnSpeak);
                btnSaveVocab = v.findViewById(R.id.btnSaveVocab);
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

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;

            UserViewHolder(View v) {
                super(v);
                messageText = v.findViewById(R.id.chatUserMessage);
            }
        }
    }
}
