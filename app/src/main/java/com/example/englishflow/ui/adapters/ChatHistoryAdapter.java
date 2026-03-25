package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.ChatSessionEntity;

import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSessionEntity session);
        void onSessionDelete(ChatSessionEntity session);
    }

    private final List<ChatSessionEntity> sessions;
    private final OnSessionClickListener listener;

    public ChatHistoryAdapter(List<ChatSessionEntity> sessions, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ChatSessionEntity session = sessions.get(position);
        holder.txtTitle.setText(session.title != null ? session.title : "Hội thoại mới");
        holder.txtTopic.setText("Chủ đề: " + (session.topic != null ? session.topic : "Chưa chọn"));
        holder.txtLastMsg.setText(session.lastMessage != null ? session.lastMessage : "Chưa có nội dung");

        holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));
        holder.btnDelete.setOnClickListener(v -> listener.onSessionDelete(session));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtTopic, txtLastMsg;
        ImageButton btnDelete;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtSessionTitle);
            txtTopic = itemView.findViewById(R.id.txtSessionTopic);
            txtLastMsg = itemView.findViewById(R.id.txtSessionLastMsg);
            btnDelete = itemView.findViewById(R.id.btnDeleteSession);
        }
    }
}
