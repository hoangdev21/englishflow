package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.ChatItem;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatItem> messages;

    public ChatAdapter(List<ChatItem> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getRole();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatItem.ROLE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        }
        if (viewType == ChatItem.ROLE_TYPING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_typing, parent, false);
            return new TypingViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
        return new AiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).message.setText(item.getMessage());
        } else if (holder instanceof AiViewHolder) {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            aiHolder.message.setText(item.getMessage());
            if (item.getCorrection() != null && !item.getCorrection().isEmpty()) {
                aiHolder.correctionBlock.setVisibility(View.VISIBLE);
                aiHolder.correctionText.setText("Sửa: " + item.getCorrection());
                aiHolder.correctionExplain.setText(item.getExplanation());
            } else {
                aiHolder.correctionBlock.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView message;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.chatUserMessage);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        final TextView message;
        final View correctionBlock;
        final TextView correctionText;
        final TextView correctionExplain;

        AiViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.chatAiMessage);
            correctionBlock = itemView.findViewById(R.id.correctionBlock);
            correctionText = itemView.findViewById(R.id.correctionText);
            correctionExplain = itemView.findViewById(R.id.correctionExplain);
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        TypingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
