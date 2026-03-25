package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.TopicItem;

import java.util.List;

public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.TopicViewHolder> {

    public interface OnTopicClickListener {
        void onTopicClick(TopicItem topicItem);
    }

    private final List<TopicItem> originalTopics;
    private List<TopicItem> filteredTopics;
    private final OnTopicClickListener listener;

    public TopicAdapter(List<TopicItem> topics, OnTopicClickListener listener) {
        this.originalTopics = new java.util.ArrayList<>(topics);
        this.filteredTopics = new java.util.ArrayList<>(topics);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic, parent, false);
        return new TopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {
        TopicItem topicItem = filteredTopics.get(position);
        holder.nameText.setText(topicItem.getTitle());
        holder.statusText.setText(topicItem.getStatus());
        holder.itemView.setOnClickListener(v -> listener.onTopicClick(topicItem));
    }

    @Override
    public int getItemCount() {
        return filteredTopics.size();
    }

    public void filter(String query) {
        filteredTopics.clear();
        if (query == null || query.isEmpty()) {
            filteredTopics.addAll(originalTopics);
        } else {
            String q = query.toLowerCase().trim();
            for (TopicItem item : originalTopics) {
                if (item.getTitle().toLowerCase().contains(q)) {
                    filteredTopics.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView statusText;

        TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.topicName);
            statusText = itemView.findViewById(R.id.topicStatus);
        }
    }
}
