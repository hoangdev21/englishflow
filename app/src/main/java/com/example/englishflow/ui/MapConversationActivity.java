package com.example.englishflow.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.englishflow.R;
import com.example.englishflow.util.VoiceFlowEngine;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.List;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.GroqChatService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.widget.EditText;
import android.widget.ProgressBar;

public class MapConversationActivity extends AppCompatActivity {

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_PROMPT_KEY = "extra_prompt_key";
    public static final String EXTRA_ROLE_CONTEXT = "extra_role_context";
    public static final String EXTRA_MIN_EXCHANGES = "extra_min_exchanges";

    private VoiceFlowEngine voiceFlowEngine;

    private TextView titleView;
    private TextView progressView;
    private ProgressBar mapProgressBar;
    private View ivFinishFlag;
    private TextView listeningStatus;
    private TextView timerText;
    private ImageButton micButton;
    private android.view.View btnType;
    private android.view.View btnInspiration;
    
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private GroqChatService groqChatService;

    private int minExchanges = 4;
    private int exchangeCount = 0;
    private String nodeId;
    private String currentTopic = "English Practice";
    private String customPrompt = "";
    private List<com.example.englishflow.data.LessonVocabulary> lessonVocab;
    private com.example.englishflow.data.LessonVocabulary currentPracticingWord;
    private boolean isRoleplayActive = false;
    private boolean isDoneShown = false;

    public static final String EXTRA_VOCAB_LIST = "extra_vocab_list";

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
        
        if (titleView != null) titleView.setText(currentTopic);
        
        customPrompt = getIntent().getStringExtra(EXTRA_ROLE_CONTEXT);
        lessonVocab = (List<com.example.englishflow.data.LessonVocabulary>) getIntent().getSerializableExtra(EXTRA_VOCAB_LIST);
        
        groqChatService = new GroqChatService(this);

        findViewById(R.id.mapConversationBack).setOnClickListener(v -> finish());
        
        // Start Step 1
        startLesson();

        voiceFlowEngine = new VoiceFlowEngine(this, new VoiceFlowEngine.VoiceCallback() {
            @Override
            public void onStateChanged(VoiceFlowEngine.State state) {
                runOnUiThread(() -> renderState(state));
            }

            @Override
            public void onTranscript(String text) {
                runOnUiThread(() -> {
                    if (currentPracticingWord != null) {
                        evaluatePronunciation(text);
                        return;
                    }
                    
                    addMessage(new ChatMessage(text, false)); // Add User Message
                    
                    exchangeCount++;
                    updateProgress();
                    
                    // Call AI for contextual response
                    groqChatService.getChatResponse(text, currentTopic, customPrompt, chatMessages, new GroqChatService.ChatCallback() {
                        @Override
                        public void onSuccess(String response, String correction, String explanation, String vocabWord, String vocabIpa, String vocabMeaning, String vocabExample, String vocabExampleVi) {
                            runOnUiThread(() -> {
                                typewriterMessage(response, correction, explanation, vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi);
                                voiceFlowEngine.speakResponse(response);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(MapConversationActivity.this, "AI Error: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                });
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
                runOnUiThread(() -> listeningStatus.setText(getString(R.string.map_conversation_hint)));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MapConversationActivity.this, "Voice Error: " + message, Toast.LENGTH_SHORT).show());
            }
        });

        micButton.setOnClickListener(v -> {
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

    private void startLesson() {
        if (lessonVocab == null || lessonVocab.isEmpty()) {
            startProactiveChat();
            return;
        }

        addMessage(new ChatMessage("Chào mừng đến với bài học " + currentTopic + "! Trước hết, hãy luyện tập các từ vựng quan trọng sau nhé.", true));
        
        ChatMessage hub = new ChatMessage("Luyện tập từ vựng", true);
        hub.isVocabHub = true;
        hub.nodeVocabEntries = lessonVocab;
        addMessage(hub);
    }

    private void practiceWord(com.example.englishflow.data.LessonVocabulary vocab) {
        currentPracticingWord = vocab;
        voiceFlowEngine.speakResponse("Lặp lại theo mình nhé: " + vocab.getWord());
        listeningStatus.setText("Đang lắng nghe: " + vocab.getWord());
        
        new android.os.Handler().postDelayed(() -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voiceFlowEngine.startListening("en-US");
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        }, 1500);
    }

    private void evaluatePronunciation(String text) {
        String target = currentPracticingWord.getWord().toLowerCase();
        String input = text.toLowerCase();
        
        if (input.contains(target) || target.contains(input)) {
            voiceFlowEngine.speakResponse("Tuyệt vời! Bạn phát âm đúng rồi.");
            currentPracticingWord.setPracticed(true);
            chatAdapter.notifyDataSetChanged();
            
            // Check if all done
            boolean allDone = true;
            for (com.example.englishflow.data.LessonVocabulary v : lessonVocab) if (!v.isPracticed()) allDone = false;
            if (allDone) {
                 new android.os.Handler().postDelayed(() -> {
                     addMessage(new ChatMessage("Tốt lắm! Bạn đã sẵn sàng thực hành hội thoại. Hãy bắt đầu ngay thôi!", true));
                     startProactiveChat();
                 }, 2000);
            }
        } else {
            voiceFlowEngine.speakResponse("Gần đúng rồi, hãy thử lại nhé: " + target);
        }
        currentPracticingWord = null;
    }

    private void startProactiveChat() {
        isRoleplayActive = true;
        String roleContext = getIntent().getStringExtra(EXTRA_ROLE_CONTEXT);
        if (roleContext != null && !roleContext.isEmpty()) {
            customPrompt = "ROLEPLAY CONTEXT: " + roleContext + "\n\n" +
                    "CRITICAL INSTRUCTIONS:\n" +
                    "1. YOU ARE THE ROLE DESCRIBED ABOVE.\n" +
                    "2. Greet the user in character.\n" +
                    "3. Engage in natural conversation, ask short questions back to the user.\n" +
                    "4. Stay in character at all times.\n" +
                    "5. Keep responses concise and focused on the topic: " + currentTopic;
            
            String openingPrompt = "As the role: " + roleContext + ", give a short opening greeting to the user to start the conversation. Return only the plain English text. DO NOT USE JSON.";
            groqChatService.getRawAiResponse(openingPrompt, new GroqChatService.RawCallback() {
                @Override
                public void onSuccess(String rawResponse) {
                    runOnUiThread(() -> {
                        typewriterMessage(rawResponse);
                        voiceFlowEngine.speakResponse(rawResponse);
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        String fallback = "Hello! I'm ready to practice " + currentTopic + " with you.";
                        typewriterMessage(fallback);
                        voiceFlowEngine.speakResponse(fallback);
                    });
                }
            });
        }
    }

    private void startTimer() {
        final long startTime = System.currentTimeMillis();
        final android.os.Handler handler = new android.os.Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsed / 1000) % 60;
                int minutes = (int) (elapsed / (1000 * 60));
                timerText.setText(String.format("%02d:%02d", minutes, seconds));
                handler.postDelayed(this, 1000);
            }
        });
    }

    private void startListening() {
        voiceFlowEngine.startListening("vi-VN"); // Change to Vietnamese for this mode
        listeningStatus.setText("Đang lắng nghe...");
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
        // Prevent multiple dialogs
        if (isFinishing()) return;
        
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_lesson_complete, null);
        
        ((TextView) v.findViewById(R.id.tvCompleteTitle)).setText("Bài học hoàn tất!");
        ((TextView) v.findViewById(R.id.tvCompleteMetrics)).setText("Bạn đã thực hiện " + exchangeCount + " lượt hội thoại và nắm vững " + (lessonVocab != null ? lessonVocab.size() : 0) + " từ vựng.");
        
        v.findViewById(R.id.btnFinishLesson).setOnClickListener(view -> {
            if (nodeId != null) {
                AppRepository.getInstance(this).saveMapNodeCompleted(nodeId);
            }
            dialog.dismiss();
            finish(); 
        });
        
        dialog.setContentView(v);
        dialog.show();
    }private void renderState(@NonNull VoiceFlowEngine.State state) {
        switch (state) {
            case LISTENING:
                micButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
                listeningStatus.setVisibility(android.view.View.VISIBLE);
                listeningStatus.setText("Đang lắng nghe...");
                break;
            case SPEAKING:
                micButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                listeningStatus.setVisibility(android.view.View.VISIBLE);
                listeningStatus.setText("Flow đang phản hồi...");
                break;
            case PROCESSING:
                listeningStatus.setVisibility(android.view.View.VISIBLE);
                listeningStatus.setText("Đang xử lý...");
                break;
            case IDLE:
            default:
                micButton.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                listeningStatus.setVisibility(android.view.View.INVISIBLE);
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

    private void typewriterMessage(String fullText, String correction, String explanation, String vocabWord, String vocabIpa, String vocabMeaning, String vocabExample, String vocabExampleVi) {
        ChatMessage message = new ChatMessage(fullText, true);
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

        final android.os.Handler handler = new android.os.Handler();
        final int[] index = {0};
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (index[0] <= fullText.length()) {
                    message.text = fullText.substring(0, index[0]);
                    chatAdapter.notifyItemChanged(position);
                    index[0]++;
                    handler.postDelayed(this, 25); // Faster 25ms per character
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

        // Call AI for suggestions
        String history = "";
        for (int i = Math.max(0, chatMessages.size() - 3); i < chatMessages.size(); i++) {
            history += (chatMessages.get(i).isAi ? "AI: " : "User: ") + chatMessages.get(i).text + "\n";
        }

        String prompt = "Give me 3 very short, natural English response suggestions (max 10 words each) for a student in this chat:\n" + history + 
                       "\nReturn ONLY 3 lines of plain text, no numbering.";

        groqChatService.getRawAiResponse(prompt, new GroqChatService.RawCallback() {
            @Override
            public void onSuccess(String rawResponse) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    String[] lines = rawResponse.split("\n");
                    int count = 0;
                    for (String line : lines) {
                        String clean = line.replaceAll("^\\d+[.)]\\s*", "").trim();
                        if (clean.isEmpty() || count >= 3) continue;
                        
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
                        suggTexts[0].setOnClickListener(v -> { handleUserResponse("I'm ready to learn!"); dialog.dismiss(); });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    suggTexts[0].setText("I'm ready!");
                    suggTexts[0].setVisibility(View.VISIBLE);
                    suggTexts[0].setOnClickListener(v -> { handleUserResponse("I'm ready!"); dialog.dismiss(); });
                });
            }
        });

        dialog.show();
    }

    private void handleUserResponse(String text) {
        addMessage(new ChatMessage(text, false));
        exchangeCount++;
        updateProgress();
        
        groqChatService.getChatResponse(text, currentTopic, customPrompt, chatMessages, new GroqChatService.ChatCallback() {
            @Override
            public void onSuccess(String response, String correction, String explanation, String vocabWord, String vocabIpa, String vocabMeaning, String vocabExample, String vocabExampleVi) {
                runOnUiThread(() -> {
                    typewriterMessage(response, correction, explanation, vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi);
                    voiceFlowEngine.speakResponse(response);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MapConversationActivity.this, "AI Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Inner classes for Chat Support
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
        public List<com.example.englishflow.data.LessonVocabulary> nodeVocabEntries;

        public ChatMessage(String text, boolean isAi) {
            this.text = text;
            this.isAi = isAi;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_AI = 0;
        private static final int TYPE_USER = 1;
        private static final int TYPE_VOCAB_HUB = 2;

        private List<ChatMessage> messages;

        public ChatAdapter(List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessage msg = messages.get(position);
            if (msg.isVocabHub) return TYPE_VOCAB_HUB;
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
                for (com.example.englishflow.data.LessonVocabulary v : msg.nodeVocabEntries) {
                    View row = LayoutInflater.from(MapConversationActivity.this).inflate(R.layout.item_vocab_row, hub.container, false);
                    ((TextView) row.findViewById(R.id.vocabWord)).setText(v.getWord());
                    ((TextView) row.findViewById(R.id.vocabMeaning)).setText(v.getIpa() + " • " + v.getMeaning());
                    row.findViewById(R.id.checkDone).setVisibility(v.isPracticed() ? View.VISIBLE : View.GONE);
                    row.findViewById(R.id.btnPracticeWord).setOnClickListener(view -> practiceWord(v));
                    if (!v.isPracticed()) allDone = false;
                    hub.container.addView(row);
                }
                hub.btnStart.setEnabled(allDone);
                hub.btnStart.setOnClickListener(v -> {
                    addMessage(new ChatMessage("Ok, mình đã sẵn sàng cho hội thoại nhập vai!", false));
                    startProactiveChat();
                    hub.btnStart.setVisibility(View.GONE);
                    hub.btnStart.setEnabled(false);
                });
                return;
            }

            if (holder instanceof AiViewHolder) {
                AiViewHolder ai = (AiViewHolder) holder;
                ai.messageText.setText(msg.text);

                // Show/Hide metadata
                boolean hasVocab = msg.vocabWord != null && !msg.vocabWord.isEmpty();
                ai.vocabActionBar.setVisibility(hasVocab ? View.VISIBLE : View.GONE);
                ai.vocabDetailBlock.setVisibility(hasVocab ? View.VISIBLE : View.GONE);
                if (hasVocab) {
                    ai.tvVocabWord.setText(msg.vocabWord);
                    ai.tvVocabIpa.setText(msg.vocabIpa);
                    ai.tvVocabMeaning.setText(msg.vocabMeaning);
                    ai.tvVocabExample.setText(msg.vocabExample);
                    ai.tvVocabExampleVi.setText(msg.vocabExampleVi);
                    
                    ai.btnSpeak.setOnClickListener(v -> voiceFlowEngine.speakResponse(msg.vocabWord));
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
        public int getItemCount() { return messages.size(); }

        class AiViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            View vocabActionBar, vocabDetailBlock, correctionBlock;
            TextView tvVocabWord, tvVocabIpa, tvVocabMeaning, tvVocabExample, tvVocabExampleVi;
            TextView correctionText, correctionExplain;
            View btnSpeak, btnSaveVocab;

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
            UserViewHolder(View v) { super(v); messageText = v.findViewById(R.id.chatUserMessage); }
        }
    }
}
