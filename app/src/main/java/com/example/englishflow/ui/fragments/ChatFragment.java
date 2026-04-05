package com.example.englishflow.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.ChatItem;
import com.example.englishflow.data.GroqChatService;
import com.example.englishflow.database.entity.ChatMessageEntity;
import com.example.englishflow.database.entity.ChatSessionEntity;
import com.example.englishflow.ui.adapters.ChatAdapter;
import com.example.englishflow.ui.adapters.ChatHistoryAdapter;
import com.example.englishflow.ui.views.VoiceWaveformView;
import com.example.englishflow.util.VoiceFlowEngine;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatFragment extends Fragment {

    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int TYPEWRITER_LONG_TEXT_THRESHOLD = 360;
    private static final int TYPEWRITER_DELAY_MS = 42;

    // ── UI ────────────────────────────────────────────────────────────────────
    private final List<ChatItem> chatItems = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private Spinner topicSpinner;
    private EditText messageEdit;
    private MaterialButton btnHistory, btnNewChat, btnSendMessage, btnMic;
    private LinearLayout voiceStatusBar;
    private TextView tvVoiceStatus;
    private View voiceDot;
    private VoiceWaveformView voiceWaveform;

    // ── Services ──────────────────────────────────────────────────────────────
    private AppRepository repository;
    private GroqChatService chatService;
    private VoiceFlowEngine voiceEngine;
    private ExecutorService dbExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isInitialSelection = true;
    private String currentSessionId;
    private Animation micPulseAnim;
    private boolean isVoiceMode = false;   // true = user triggered voice interaction

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository  = AppRepository.getInstance(requireContext());
        chatService = new GroqChatService(requireContext());
        dbExecutor = Executors.newSingleThreadExecutor();

        // Bind views
        topicSpinner   = view.findViewById(R.id.spinnerTopic);
        recyclerView   = view.findViewById(R.id.chatRecycler);
        messageEdit    = view.findViewById(R.id.edtMessage);
        btnSendMessage = view.findViewById(R.id.btnSendMessage);
        btnHistory     = view.findViewById(R.id.btnHistory);
        btnNewChat     = view.findViewById(R.id.btnNewChat);
        btnMic         = view.findViewById(R.id.btnMic);
        voiceStatusBar = view.findViewById(R.id.voiceStatusBar);
        tvVoiceStatus  = view.findViewById(R.id.tvVoiceStatus);
        voiceDot       = view.findViewById(R.id.voiceDot);
        voiceWaveform  = view.findViewById(R.id.voiceWaveform);

        View chatHeaderSection = view.findViewById(R.id.chatHeaderSection);
        if (chatHeaderSection != null) {
            chatHeaderSection.setClickable(false);
            chatHeaderSection.setFocusable(false);
            chatHeaderSection.bringToFront();
        }

        // RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        adapter = new ChatAdapter(chatItems);
        recyclerView.setAdapter(adapter);

        // Load mic pulse animation
        micPulseAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.mic_pulse);

        // Wire voice engine after both are created
        setupVoiceEngine();
        // Route vocab-word speak button through VoiceFlowEngine
        adapter.setSpeakCallback(text -> voiceEngine.speakResponse(text));
        setupSpinner();
        setupInputActions();
        setupIconsAndActions();
        startNewConversation();

        // ══ Window Insets — Responsive Status Bar Clearance ══
        View headerContent = view.findViewById(R.id.chatHeaderContent);
        if (headerContent != null) {
            final int initialLeftPadding = headerContent.getPaddingLeft();
            final int initialTopPadding = headerContent.getPaddingTop();
            final int initialRightPadding = headerContent.getPaddingRight();
            final int initialBottomPadding = headerContent.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(headerContent, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(initialLeftPadding, systemBars.top + initialTopPadding, initialRightPadding, initialBottomPadding);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(headerContent);
        }

        // ════ Keyboard Handling (IME Insets) — Keep Input Bar Above Keyboard ════
        View inputContainer = view.findViewById(R.id.chatInputContainer);
        if (inputContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                float density = getResources().getDisplayMetrics().density;
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                
                if (imeInsets.bottom > 0) {
                    // Keyboard is visible: Lift input bar above keyboard edge
                    lp.bottomMargin = imeInsets.bottom + (int)(12 * density);
                    // Force scroll to current bottom so messages aren't hidden
                    recyclerView.postDelayed(() -> {
                        if (isAdded() && adapter.getItemCount() > 0) {
                            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                        }
                    }, 100);
                } else {
                    // Keyboard hidden: Reset to standard floating position above BottomNav
                    lp.bottomMargin = (int)(96 * density);
                }
                
                v.setLayoutParams(lp);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
            dbExecutor = null;
        }
        if (adapter != null) adapter.shutdownTts();
        if (voiceEngine != null) voiceEngine.shutdown();
        if (voiceWaveform != null) {
            voiceWaveform.setMode(VoiceWaveformView.Mode.IDLE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VoiceFlowEngine setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupVoiceEngine() {
        voiceEngine = new VoiceFlowEngine(requireContext(), new VoiceFlowEngine.VoiceCallback() {

            @Override
            public void onStateChanged(VoiceFlowEngine.State state) {
                updateVoiceUI(state);
            }

            @Override
            public void onTranscript(String text) {
                // User finished speaking — fill input field and auto-send
                messageEdit.setText(text);
                tvVoiceStatus.setText("\"" + text + "\"");
                // Small delay for user to see what was transcribed
                mainHandler.postDelayed(() -> {
                    sendMessage(true /* fromVoice */);
                }, 400);
            }

            @Override
            public void onPartialTranscript(String text) {
                // Show live transcription in the status bar
                tvVoiceStatus.setText(text);
            }

            @Override
            public void onRmsChanged(float rmsDb) {
                if (voiceWaveform != null) {
                    voiceWaveform.setMicLevel(rmsDb);
                }
            }

            @Override
            public void onSpeakingDone() {
                // AI finished speaking — in voice mode, auto-relisten
                if (isVoiceMode) {
                    mainHandler.postDelayed(() -> {
                        if (isVoiceMode && isAdded()) {
                            startVoiceListening();
                        }
                    }, 700);
                }
            }

            @Override
            public void onError(String message) {
                if (!message.isEmpty()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input actions
    // ─────────────────────────────────────────────────────────────────────────

    private void setupInputActions() {
        btnSendMessage.setOnClickListener(v -> sendMessage(false));

        messageEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage(false);
                return true;
            }
            return false;
        });

        // Mic button — toggle listen / stop AI speech
        btnMic.setOnClickListener(v -> {
            VoiceFlowEngine.State state = voiceEngine.getState();
            if (state == VoiceFlowEngine.State.LISTENING) {
                // Stop early — send what we heard so far
                voiceEngine.stopListening();
            } else if (state == VoiceFlowEngine.State.SPEAKING) {
                // Interrupt AI
                voiceEngine.stopSpeaking();
                isVoiceMode = false;
            } else {
                // Start new voice interaction
                isVoiceMode = true;
                startVoiceListening();
            }
        });

        recyclerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                hideKeyboard();
            }
            return false;
        });
    }

    private void startVoiceListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        hideKeyboard();
        // Always use vi-VN as base — SpeechRecognizer will also accept English
        voiceEngine.startListening("vi-VN");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceListening();
        } else {
            Toast.makeText(requireContext(),
                    "Cần quyền microphone để dùng tính năng hội thoại giọng nói",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice UI state machine
    // ─────────────────────────────────────────────────────────────────────────

    private void updateVoiceUI(VoiceFlowEngine.State state) {
        switch (state) {
            case LISTENING:
                voiceStatusBar.setVisibility(View.VISIBLE);
                tvVoiceStatus.setText("Đang lắng nghe...");
                voiceDot.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFEF4444));
                if (voiceWaveform != null) {
                    voiceWaveform.setVisibility(View.VISIBLE);
                    voiceWaveform.setMode(VoiceWaveformView.Mode.LISTENING);
                }
                btnMic.setBackgroundResource(R.drawable.bg_mic_recording);
                btnMic.setIconResource(R.drawable.ic_mic);
                btnMic.setIconTintResource(android.R.color.holo_red_dark);
                btnMic.startAnimation(micPulseAnim);
                break;

            case PROCESSING:
                voiceStatusBar.setVisibility(View.VISIBLE);
                tvVoiceStatus.setText("Flow đang suy nghĩ...");
                voiceDot.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF3B82F6));
                if (voiceWaveform != null) {
                    voiceWaveform.setVisibility(View.VISIBLE);
                    voiceWaveform.setMode(VoiceWaveformView.Mode.PROCESSING);
                }
                btnMic.clearAnimation();
                btnMic.setBackgroundResource(R.drawable.bg_mic_idle);
                btnMic.setIconResource(R.drawable.ic_mic);
                btnMic.setIconTint(android.content.res.ColorStateList.valueOf(0xFF0F172A));
                break;

            case SPEAKING:
                voiceStatusBar.setVisibility(View.VISIBLE);
                tvVoiceStatus.setText("Flow đang trả lời...");
                voiceDot.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF10B981));
                if (voiceWaveform != null) {
                    voiceWaveform.setVisibility(View.VISIBLE);
                    voiceWaveform.setMode(VoiceWaveformView.Mode.SPEAKING);
                }
                btnMic.clearAnimation();
                btnMic.setBackgroundResource(R.drawable.bg_mic_speaking);
                btnMic.setIconResource(R.drawable.ic_stop);
                btnMic.setIconTint(android.content.res.ColorStateList.valueOf(0xFF10B981));
                break;

            case IDLE:
            default:
                // Hide status bar when idle to free up screen space
                voiceStatusBar.setVisibility(View.GONE);
                if (voiceWaveform != null) {
                    voiceWaveform.setMode(VoiceWaveformView.Mode.IDLE);
                    voiceWaveform.setVisibility(View.GONE);
                }
                btnMic.clearAnimation();
                btnMic.setBackgroundResource(R.drawable.bg_mic_idle);
                btnMic.setIconResource(R.drawable.ic_mic);
                btnMic.setIconTint(android.content.res.ColorStateList.valueOf(0xFF0F172A));
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message sending
    // ─────────────────────────────────────────────────────────────────────────

    private void sendMessage(boolean fromVoice) {
        String text = messageEdit.getText() != null ? messageEdit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        ChatItem item = new ChatItem(ChatItem.ROLE_USER, text, null, null);
        addMessage(item);
        saveMessage(item);
        messageEdit.setText("");
        if (!fromVoice) isVoiceMode = false;

        // Typing indicator
        int typingPosition = chatItems.size();
        chatItems.add(new ChatItem(ChatItem.ROLE_TYPING, "", null, null));
        adapter.notifyItemInserted(typingPosition);
        recyclerView.smoothScrollToPosition(chatItems.size() - 1);

        if (fromVoice) updateVoiceUI(VoiceFlowEngine.State.PROCESSING);

        String topic = topicSpinner.getSelectedItem().toString();

        chatService.getChatResponse(text, topic, new GroqChatService.ChatCallback() {
            @Override
            public void onSuccess(String response, String correction, String explanation,
                                  String vocabWord, String vocabIpa, String vocabMeaning,
                                  String vocabExample, String vocabExampleVi) {
            mainHandler.post(() -> {
                    removeTypingIndicator();
                    animateTypewriterResponse(response, correction, explanation,
                            vocabWord, vocabIpa, vocabMeaning, vocabExample, vocabExampleVi,
                            fromVoice);
                    repository.increaseChatCount();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    removeTypingIndicator();
                    String displayError = (error != null && !error.isEmpty()) 
                        ? "Lỗi hệ thống: " + error 
                        : "Xin lỗi, tôi gặp sự cố kết nối. Hãy thử lại nhé!";
                    
                    addMessage(new ChatItem(ChatItem.ROLE_AI, displayError, null, null));
                    if (fromVoice) {
                        voiceEngine.speakResponse("Tôi gặp sự cố kỹ thuật. Vui lòng kiểm tra lại kết nối.");
                        updateVoiceUI(VoiceFlowEngine.State.SPEAKING);
                    }
                });
            }
        });
    }

    private void animateTypewriterResponse(String fullText, String correction, String explanation,
                                           String vocabWord, String vocabIpa, String vocabMeaning,
                                           String vocabExample, String vocabExampleVi,
                                           boolean autoSpeak) {
        final String responseText = fullText == null ? "" : fullText;

        ChatItem item = new ChatItem(ChatItem.ROLE_AI, "", correction, explanation);
        if (vocabWord != null) {
            item.setVocabWord(vocabWord);
            item.setVocabIpa(vocabIpa);
            item.setVocabMeaning(vocabMeaning);
            item.setVocabExample(vocabExample);
            item.setVocabExampleVi(vocabExampleVi);
        }
        chatItems.add(item);
        int position = chatItems.size() - 1;
        adapter.notifyItemInserted(position);

        // Auto-speak the response if requested (voice mode)
        if (autoSpeak) {
            updateVoiceUI(VoiceFlowEngine.State.SPEAKING);
            voiceEngine.speakResponse(responseText);
            
            // CRITICAL OPTIMIZATION: Disable typewriter for Voice Mode to fix stutter/crackling
            item.setMessage(responseText);
            adapter.notifyItemChanged(position);
            saveMessage(item);
            return;
        }

        if (responseText.length() >= TYPEWRITER_LONG_TEXT_THRESHOLD || TextUtils.isEmpty(responseText)) {
            item.setMessage(responseText);
            adapter.notifyItemChanged(position);
            saveMessage(item);
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            return;
        }

        // Optional: Disable item animations to prevent flickering during rapid updates
        if (recyclerView.getItemAnimator() != null) {
            recyclerView.getItemAnimator().setChangeDuration(0);
        }

        final int[] charIndex = {0};
        final StringBuilder displayedText = new StringBuilder();

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (charIndex[0] < responseText.length()) {
                    int charsToType = Math.min(responseText.length() > 240 ? 20 : 12, responseText.length() - charIndex[0]);
                    displayedText.append(responseText.substring(charIndex[0], charIndex[0] + charsToType));
                    charIndex[0] += charsToType;

                    item.setMessage(displayedText.toString());
                    adapter.notifyItemChanged(position);

                    // Robust "is near bottom" check:
                    // If the current scroll position is close to the bottom (within 300 pixels), follow the AI
                    int extent = recyclerView.computeVerticalScrollExtent();
                    int offset = recyclerView.computeVerticalScrollOffset();
                    int range  = recyclerView.computeVerticalScrollRange();
                    boolean isNearBottom = (range - extent - offset) < 300; 

                    if (isNearBottom) {
                        // Scroll to the very bottom
                        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                    }

                    if (charIndex[0] >= responseText.length()) {
                        saveMessage(item);
                    } else {
                        mainHandler.postDelayed(this, TYPEWRITER_DELAY_MS);
                    }
                }
            }
        };
        mainHandler.post(runnable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spinner / Topic setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupSpinner() {
        ArrayAdapter<String> topicsAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_spinner_topic,
                Arrays.asList(
                        "Chọn chủ đề học tập",
                        "Tiếng Anh giao tiếp",
                        "Công nghệ & Lập trình",
                        "Sức khỏe & Y tế",
                        "Phỏng vấn xin việc",
                        "Du lịch & Khám phá",
                        "Kinh doanh & Đàm phán",
                        "Mua sắm & Ăn uống",
                        "Giáo dục & Khoa học"
                )
        );
        topicsAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        topicSpinner.setAdapter(topicsAdapter);

        topicSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                if (isInitialSelection) { isInitialSelection = false; return; }
                String selectedTopic = parent.getItemAtPosition(position).toString();
                if (position > 0) {
                    int oldSize = chatItems.size();
                    chatItems.clear();
                    if (oldSize > 0) {
                        adapter.notifyItemRangeRemoved(0, oldSize);
                    }
                    String userName  = repository.getUserName();
                    String styledName = "[color:#2563EB]***" + userName + "***[/color]";
                    String greeting  = "Xin chào " + styledName + "! Tôi là Flow, trợ lý Tiếng Anh của bạn. "
                            + "Hôm nay chúng ta sẽ cùng ôn tập về chủ đề **" + selectedTopic + "** nhé! 🚀";
                    animateTypewriterResponse(greeting, null, null, null, null, null, null, null, false);
                    saveSession("Hội thoại: " + selectedTopic);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        android.view.View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    private void addMessage(ChatItem item) {
        chatItems.add(item);
        adapter.notifyItemInserted(chatItems.size() - 1);
        recyclerView.smoothScrollToPosition(chatItems.size() - 1);
    }

    private void setupIconsAndActions() {
        btnHistory.setOnClickListener(v -> showHistoryBottomSheet());
        btnNewChat.setOnClickListener(v -> startNewConversation());
    }

    private void startNewConversation() {
        currentSessionId = UUID.randomUUID().toString();
        int oldSize = chatItems.size();
        chatItems.clear();
        if (oldSize > 0) {
            adapter.notifyItemRangeRemoved(0, oldSize);
        }
        isVoiceMode = false;
        if (voiceEngine != null) voiceEngine.stopSpeaking();
        updateVoiceUI(VoiceFlowEngine.State.IDLE);

        String userName   = repository.getUserName();
        String styledName = "[color:#2563EB]***" + userName + "***[/color]";
        String welcome    = "Xin chào " + styledName + "! Tôi là **Flow**, trợ lý Tiếng Anh thông minh của bạn. "
                + "Bạn có thể gõ hoặc **nhấn nút mic** để nói chuyện tự nhiên với tôi. "
                + "Hôm nay bạn muốn luyện tập chủ đề gì?";
        addMessage(new ChatItem(ChatItem.ROLE_AI, welcome, null, null));
        saveSession(welcome);
    }

    private void saveSession(String lastMsg) {
        String topic = topicSpinner.getSelectedItem() != null
                ? topicSpinner.getSelectedItem().toString() : "Tự do";
        String title = lastMsg.length() > 30 ? lastMsg.substring(0, 27) + "..." : lastMsg;
        ChatSessionEntity session = new ChatSessionEntity(currentSessionId, title, topic, lastMsg);
        if (dbExecutor != null) {
            dbExecutor.execute(() -> repository.getDatabase().chatSessionDao().upsert(session));
        }
    }

    private void saveMessage(ChatItem item) {
        if (currentSessionId == null) return;
        String role = item.getRole() == ChatItem.ROLE_USER ? "user" : "ai";
        ChatMessageEntity entity = new ChatMessageEntity(
                currentSessionId, role, item.getMessage(),
                item.getCorrection(), item.getExplanation());
        if (dbExecutor != null) {
            dbExecutor.execute(() -> {
                repository.getDatabase().chatMessageDao().insert(entity);
                repository.getDatabase().chatSessionDao()
                        .updateLastMessage(currentSessionId, item.getMessage());
            });
        }
    }

    private void removeTypingIndicator() {
        for (int i = 0; i < chatItems.size(); i++) {
            if (chatItems.get(i).getRole() == ChatItem.ROLE_TYPING) {
                chatItems.remove(i);
                adapter.notifyItemRemoved(i);
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // History bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    private void showHistoryBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_history, null);
        dialog.setContentView(sheetView);

        RecyclerView rvHistory = sheetView.findViewById(R.id.recyclerHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        View btnDeleteAll = sheetView.findViewById(R.id.btnDeleteAll);

        if (dbExecutor == null) {
            return;
        }

        dbExecutor.execute(() -> {
            List<ChatSessionEntity> sessions =
                    repository.getDatabase().chatSessionDao().getAllSessions();
            mainHandler.post(() -> {
                final ChatHistoryAdapter[] adapterArr = new ChatHistoryAdapter[1];
                adapterArr[0] = new ChatHistoryAdapter(sessions, new ChatHistoryAdapter.OnSessionClickListener() {
                    @Override
                    public void onSessionClick(ChatSessionEntity session) {
                        loadConversation(session);
                        dialog.dismiss();
                    }
                    @Override
                    public void onSessionDelete(ChatSessionEntity session) {
                        deleteConversation(session, sessions, adapterArr[0]);
                    }
                });
                rvHistory.setAdapter(adapterArr[0]);
                btnDeleteAll.setVisibility(sessions.isEmpty() ? View.GONE : View.VISIBLE);
                btnDeleteAll.setOnClickListener(v -> confirmDeleteAll(sessions, adapterArr[0], dialog));
            });
        });

        dialog.show();
    }

    private void confirmDeleteAll(List<ChatSessionEntity> sessions, ChatHistoryAdapter adapt,
                                  BottomSheetDialog historyDialog) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        ((TextView) dialogView.findViewById(R.id.dialogTitle)).setText("Xóa tất cả?");
        ((TextView) dialogView.findViewById(R.id.dialogMessage)).setText(
                "Bạn có chắc muốn xóa TOÀN BỘ lịch sử hội thoại? Hành động này không thể hoàn tác.");

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            if (dbExecutor == null) {
                return;
            }
            dbExecutor.execute(() -> {
                repository.getDatabase().chatSessionDao().deleteAllSessions();
                repository.getDatabase().chatMessageDao().deleteAllMessages();
                mainHandler.post(() -> {
                    sessions.clear();
                    adapt.notifyDataSetChanged();
                    startNewConversation();
                    historyDialog.dismiss();
                    Toast.makeText(requireContext(), "Đã xóa toàn bộ lịch sử",
                            Toast.LENGTH_SHORT).show();
                });
            });
        });
        dialog.show();
    }

    private void loadConversation(ChatSessionEntity session) {
        currentSessionId = session.sessionId;
        int oldSize = chatItems.size();
        chatItems.clear();
        if (oldSize > 0) {
            adapter.notifyItemRangeRemoved(0, oldSize);
        }
        if (dbExecutor == null) {
            return;
        }

        dbExecutor.execute(() -> {
            List<ChatMessageEntity> messages =
                    repository.getDatabase().chatMessageDao().getMessagesBySession(currentSessionId);
            mainHandler.post(() -> {
                int startIndex = chatItems.size();
                for (ChatMessageEntity msg : messages) {
                    int role = msg.role.equals("user") ? ChatItem.ROLE_USER : ChatItem.ROLE_AI;
                    chatItems.add(new ChatItem(role, msg.content, msg.correction, msg.explanation));
                }
                int inserted = chatItems.size() - startIndex;
                if (inserted > 0) {
                    adapter.notifyItemRangeInserted(startIndex, inserted);
                    recyclerView.scrollToPosition(chatItems.size() - 1);
                }
            });
        });
    }

    private void deleteConversation(ChatSessionEntity session, List<ChatSessionEntity> list,
                                    ChatHistoryAdapter adapt) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            if (dbExecutor == null) {
                return;
            }
            dbExecutor.execute(() -> {
                repository.getDatabase().chatSessionDao().deleteSession(session.sessionId);
                repository.getDatabase().chatMessageDao().deleteSessionMessages(session.sessionId);
                mainHandler.post(() -> {
                    list.remove(session);
                    adapt.notifyDataSetChanged();
                    if (session.sessionId.equals(currentSessionId)) startNewConversation();
                    Toast.makeText(requireContext(), "Đã xóa hội thoại vĩnh viễn",
                            Toast.LENGTH_SHORT).show();
                });
            });
        });
        dialog.show();
    }
}
