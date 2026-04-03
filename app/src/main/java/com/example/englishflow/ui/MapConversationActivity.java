package com.example.englishflow.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;
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
import com.example.englishflow.data.GroqChatService;

public class MapConversationActivity extends AppCompatActivity {

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_PROMPT_KEY = "extra_prompt_key";
    public static final String EXTRA_MIN_EXCHANGES = "extra_min_exchanges";

    private VoiceFlowEngine voiceFlowEngine;

    private TextView titleView;
    private TextView progressView;
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
    private String currentTopic = "English Practice";
    private String customPrompt = "";

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
        customPrompt = getIntent().getStringExtra(EXTRA_PROMPT_KEY);
        minExchanges = Math.max(3, getIntent().getIntExtra(EXTRA_MIN_EXCHANGES, 4));

        titleView.setText(currentTopic);
        updateProgress();
        
        groqChatService = new GroqChatService(this);

        findViewById(R.id.mapConversationBack).setOnClickListener(v -> finish());

        voiceFlowEngine = new VoiceFlowEngine(this, new VoiceFlowEngine.VoiceCallback() {
            @Override
            public void onStateChanged(VoiceFlowEngine.State state) {
                runOnUiThread(() -> renderState(state));
            }

            @Override
            public void onTranscript(String text) {
                runOnUiThread(() -> {
                    addMessage(new ChatMessage(text, false)); // Add User Message
                    
                    exchangeCount++;
                    updateProgress();
                    
                    // Call AI for contextual response, passing the custom prompt and history
                    groqChatService.getChatResponse(text, currentTopic, customPrompt, chatMessages, new GroqChatService.ChatCallback() {
                        @Override
                        public void onSuccess(String response, String correction, String explanation, String vocabWord, String vocabIpa, String vocabMeaning, String vocabExample, String vocabExampleVi) {
                            runOnUiThread(() -> {
                                typewriterMessage(response);
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
            public void onSpeakingDone() {
                runOnUiThread(() -> listeningStatus.setText(getString(R.string.map_conversation_hint)));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MapConversationActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });

        String greeting = "Chào Hoàng! Hôm nay bạn thế nào? Mình rất vui được giúp bạn thực hành chủ đề " + currentTopic + " trong tiếng Anh. Bạn đã sẵn sàng chưa?";
        typewriterMessage(greeting);
        
        // Add a small delay to ensure TTS engine is ready for the first greeting
        new android.os.Handler().postDelayed(() -> {
            voiceFlowEngine.speakResponse(greeting);
        }, 1200);


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

        btnType.setOnClickListener(v -> {
            Toast.makeText(this, "Tính năng nhập văn bản đang được phát triển", Toast.LENGTH_SHORT).show();
        });

        btnInspiration.setOnClickListener(v -> {
            Toast.makeText(this, "Đây là gợi ý giúp bạn trả lời tốt hơn!", Toast.LENGTH_LONG).show();
        });
        
        startTimer();
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
        progressView.setText(getString(R.string.learn_map_speak) + " " + exchangeCount + "/" + minExchanges);
    }

    private void renderState(@NonNull VoiceFlowEngine.State state) {
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
        ChatMessage message = new ChatMessage("", true);
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
                    handler.postDelayed(this, 30); // 30ms per character
                }
            }
        });
    }

    // Inner classes for Chat Support
    public static class ChatMessage {
        public String text;
        public boolean isAi;
        public ChatMessage(String text, boolean isAi) {
            this.text = text;
            this.isAi = isAi;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ChatMessage> messages;
        public ChatAdapter(List<ChatMessage> messages) { this.messages = messages; }

        @Override
        public int getItemViewType(int position) { return messages.get(position).isAi ? 0 : 1; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
                return new AiViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
                return new UserViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            if (holder instanceof AiViewHolder) {
                ((AiViewHolder) holder).messageText.setText(msg.text);
            } else {
                ((UserViewHolder) holder).messageText.setText(msg.text);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class AiViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            AiViewHolder(View v) { super(v); messageText = v.findViewById(R.id.chatAiMessage); }
        }
        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            UserViewHolder(View v) { super(v); messageText = v.findViewById(R.id.chatUserMessage); }
        }
    }
}
