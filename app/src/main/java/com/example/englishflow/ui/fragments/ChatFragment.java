package com.example.englishflow.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ChatFragment extends Fragment {

    private final List<ChatItem> chatItems = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private Spinner topicSpinner;
    private EditText messageEdit;
    private AppRepository repository;
    private GroqChatService chatService;
    private boolean isInitialSelection = true;
    private String currentSessionId;
    private MaterialButton btnHistory, btnNewChat;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        chatService = new GroqChatService(requireContext());

        topicSpinner = view.findViewById(R.id.spinnerTopic);
        recyclerView = view.findViewById(R.id.chatRecycler);
        messageEdit = view.findViewById(R.id.edtMessage);
        MaterialButton sendButton = view.findViewById(R.id.btnSendMessage);
        btnHistory = view.findViewById(R.id.btnHistory);
        btnNewChat = view.findViewById(R.id.btnNewChat);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatAdapter(chatItems);
        recyclerView.setAdapter(adapter);

        setupIconsAndActions();
        startNewConversation();

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
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (isInitialSelection) {
                    isInitialSelection = false;
                    return;
                }
                
                String selectedTopic = parent.getItemAtPosition(position).toString();
                if (position > 0) { // Not "Chọn chủ đề học tập"
                    // Clear old messages
                    chatItems.clear();
                    adapter.notifyDataSetChanged();

                    String greeting = "Xin chào! Tôi là Flow, trợ lý Tiếng Anh của bạn. Hôm nay chúng ta sẽ cùng ôn tập về chủ đề **" + selectedTopic + "** nhé! 🚀";
                    animateTypewriterResponse(greeting, null, null);
                    saveSession("Hội thoại: " + selectedTopic);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        sendButton.setOnClickListener(v -> sendMessage());

        messageEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // Hide keyboard when touching the list
        recyclerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                hideKeyboard();
            }
            return false;
        });
    }

    private void hideKeyboard() {
        android.view.View view = getActivity().getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    private void sendMessage() {
        String text = messageEdit.getText() != null ? messageEdit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            return;
        }

        // Add User Message
        ChatItem item = new ChatItem(ChatItem.ROLE_USER, text, null, null);
        addMessage(item);
        saveMessage(item);
        messageEdit.setText("");

        // Add Typing Indicator
        int typingPosition = chatItems.size();
        chatItems.add(new ChatItem(ChatItem.ROLE_TYPING, "", null, null));
        adapter.notifyItemInserted(typingPosition);
        recyclerView.smoothScrollToPosition(chatItems.size() - 1);

        String topic = topicSpinner.getSelectedItem().toString();
        
        // Call Groq AI with RAG
        chatService.getChatResponse(text, topic, new GroqChatService.ChatCallback() {
            @Override
            public void onSuccess(String response, String correction, String explanation) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    removeTypingIndicator();
                    // addMessage(new ChatItem(ChatItem.ROLE_AI, response, correction, explanation));
                    animateTypewriterResponse(response, correction, explanation);
                    repository.increaseChatCount();
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    removeTypingIndicator();
                    addMessage(new ChatItem(ChatItem.ROLE_AI, "Xin lỗi, tôi gặp sự cố kết nối. Hãy thử lại nhé!", null, null));
                });
            }
        });
    }

    private void animateTypewriterResponse(String fullText, String correction, String explanation) {
        ChatItem item = new ChatItem(ChatItem.ROLE_AI, "", correction, explanation);
        chatItems.add(item);
        int position = chatItems.size() - 1;
        adapter.notifyItemInserted(position);

        final int[] charIndex = {0};
        final StringBuilder displayedText = new StringBuilder();
        final Handler handler = new Handler(Looper.getMainLooper());
        
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (charIndex[0] < fullText.length()) {
                    // Type 3-4 chars at once for better speed/feel
                    int charsToType = Math.min(8, fullText.length() - charIndex[0]);
                    displayedText.append(fullText.substring(charIndex[0], charIndex[0] + charsToType));
                    charIndex[0] += charsToType;
                    
                    item.setMessage(displayedText.toString());
                    adapter.notifyItemChanged(position);
                    recyclerView.smoothScrollToPosition(position);
                    
                    if (charIndex[0] >= fullText.length()) {
                        saveMessage(item);
                    }
                    
                    handler.postDelayed(this, 1); // Significantly faster (1ms)
                }
            }
        };
        handler.post(runnable);
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
        chatItems.clear();
        adapter.notifyDataSetChanged();
        
        // Initial AI Greeting
        String welcome = "Xin chào! Tôi là Flow, trợ lý Tiếng Anh từ EnglishFlow. Hôm nay bạn muốn luyện tập về chủ đề gì?";
        addMessage(new ChatItem(ChatItem.ROLE_AI, welcome, null, null));
        saveSession(welcome);
    }

    private void saveSession(String lastMsg) {
        String topic = topicSpinner.getSelectedItem() != null ? topicSpinner.getSelectedItem().toString() : "Tự do";
        String title = lastMsg.length() > 30 ? lastMsg.substring(0, 27) + "..." : lastMsg;
        ChatSessionEntity session = new ChatSessionEntity(currentSessionId, title, topic, lastMsg);
        new Thread(() -> {
            repository.getDatabase().chatSessionDao().upsert(session);
        }).start();
    }

    private void saveMessage(ChatItem item) {
        if (currentSessionId == null) return;
        String role = item.getRole() == ChatItem.ROLE_USER ? "user" : "ai";
        ChatMessageEntity entity = new ChatMessageEntity(
                currentSessionId,
                role,
                item.getMessage(),
                item.getCorrection(),
                item.getExplanation()
        );
        new Thread(() -> {
            repository.getDatabase().chatMessageDao().insert(entity);
            repository.getDatabase().chatSessionDao().updateLastMessage(currentSessionId, item.getMessage());
        }).start();
    }

    private void showHistoryBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_history, null);
        dialog.setContentView(sheetView);

        RecyclerView rvHistory = sheetView.findViewById(R.id.recyclerHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        View btnDeleteAll = sheetView.findViewById(R.id.btnDeleteAll);

        new Thread(() -> {
            List<ChatSessionEntity> sessions = repository.getDatabase().chatSessionDao().getAllSessions();
            new Handler(Looper.getMainLooper()).post(() -> {
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
        }).start();

        dialog.show();
    }

    private void confirmDeleteAll(List<ChatSessionEntity> sessions, ChatHistoryAdapter adapter, BottomSheetDialog historyDialog) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        ((TextView)dialogView.findViewById(R.id.dialogTitle)).setText("Xóa tất cả?");
        ((TextView)dialogView.findViewById(R.id.dialogMessage)).setText("Bạn có chắc muốn xóa TOÀN BỘ lịch sử hội thoại? Hành động này không thể hoàn tác.");
        
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                repository.getDatabase().chatSessionDao().deleteAllSessions();
                repository.getDatabase().chatMessageDao().deleteAllMessages();
                new Handler(Looper.getMainLooper()).post(() -> {
                    sessions.clear();
                    adapter.notifyDataSetChanged();
                    startNewConversation();
                    historyDialog.dismiss();
                    Toast.makeText(requireContext(), "Đã xóa toàn bộ lịch sử", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        dialog.show();
    }

    private void loadConversation(ChatSessionEntity session) {
        currentSessionId = session.sessionId;
        chatItems.clear();
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            List<ChatMessageEntity> messages = repository.getDatabase().chatMessageDao().getMessagesBySession(currentSessionId);
            new Handler(Looper.getMainLooper()).post(() -> {
                for (ChatMessageEntity msg : messages) {
                    int role = msg.role.equals("user") ? ChatItem.ROLE_USER : ChatItem.ROLE_AI;
                    chatItems.add(new ChatItem(role, msg.content, msg.correction, msg.explanation));
                }
                adapter.notifyDataSetChanged();
                recyclerView.scrollToPosition(chatItems.size() - 1);
            });
        }).start();
    }

    private void deleteConversation(ChatSessionEntity session, List<ChatSessionEntity> list, ChatHistoryAdapter adapter) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            new Thread(() -> {
                repository.getDatabase().chatSessionDao().deleteSession(session.sessionId);
                repository.getDatabase().chatMessageDao().deleteSessionMessages(session.sessionId);
                new Handler(Looper.getMainLooper()).post(() -> {
                    list.remove(session);
                    adapter.notifyDataSetChanged();
                    if (session.sessionId.equals(currentSessionId)) {
                        startNewConversation();
                    }
                    Toast.makeText(requireContext(), "Đã xóa hội thoại vĩnh viễn", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        dialog.show();
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
}
