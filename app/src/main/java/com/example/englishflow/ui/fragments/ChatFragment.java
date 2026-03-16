package com.example.englishflow.ui.fragments;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.ChatItem;
import com.example.englishflow.ui.adapters.ChatAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatFragment extends Fragment {

    private final List<ChatItem> chatItems = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    private Spinner topicSpinner;
    private EditText messageEdit;
    private AppRepository repository;

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

        topicSpinner = view.findViewById(R.id.spinnerTopic);
        recyclerView = view.findViewById(R.id.chatRecycler);
        messageEdit = view.findViewById(R.id.edtMessage);
        MaterialButton sendButton = view.findViewById(R.id.btnSendMessage);

        ArrayAdapter<String> topicsAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                Arrays.asList("Nhà hàng", "Sân bay", "Phỏng vấn", "Mua sắm", "Khám bệnh")
        );
        topicsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        topicSpinner.setAdapter(topicsAdapter);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatAdapter(chatItems);
        recyclerView.setAdapter(adapter);

        chatItems.add(new ChatItem(
                ChatItem.ROLE_AI,
                "Hi! Let's practice English. Tell me your situation today.",
                "",
                ""
        ));
        adapter.notifyItemInserted(chatItems.size() - 1);

        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = messageEdit.getText() != null ? messageEdit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            return;
        }

        int userPosition = chatItems.size();
        chatItems.add(new ChatItem(ChatItem.ROLE_USER, text, "", ""));
        adapter.notifyItemInserted(userPosition);
        recyclerView.scrollToPosition(chatItems.size() - 1);
        messageEdit.setText("");

        int typingPosition = chatItems.size();
        chatItems.add(new ChatItem(ChatItem.ROLE_TYPING, "", "", ""));
        adapter.notifyItemInserted(typingPosition);
        recyclerView.scrollToPosition(chatItems.size() - 1);

        String topic = topicSpinner.getSelectedItem().toString();
        String corrected = buildCorrection(text);
        String explanation = "Giải thích: Câu nên có thì hiện tại đơn rõ chủ ngữ và động từ đúng ngữ cảnh " + topic + ".";
        String aiReply = "Great! In a " + topic + " scenario, your sentence sounds better like: " + corrected;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            int last = chatItems.size() - 1;
            if (last >= 0 && chatItems.get(last).getRole() == ChatItem.ROLE_TYPING) {
                chatItems.remove(last);
                adapter.notifyItemRemoved(last);
            }

            int aiPosition = chatItems.size();
            chatItems.add(new ChatItem(ChatItem.ROLE_AI, aiReply, corrected, explanation));
            adapter.notifyItemInserted(aiPosition);
            recyclerView.scrollToPosition(chatItems.size() - 1);
            repository.increaseChatCount();
        }, 1100);
    }

    private String buildCorrection(String text) {
        String normalized = text.trim();
        if (!normalized.endsWith(".")) {
            normalized = normalized + ".";
        }
        if (normalized.length() > 0) {
            normalized = Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
        }
        return normalized;
    }
}
