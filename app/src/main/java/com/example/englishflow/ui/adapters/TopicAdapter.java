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
    private final String domainEmoji;
    private String currentQuery = "";
    private String currentStatus = "Tất cả";

    public TopicAdapter(List<TopicItem> topics, String domainEmoji, OnTopicClickListener listener) {
        this.originalTopics = new java.util.ArrayList<>(topics);
        this.filteredTopics = new java.util.ArrayList<>(topics);
        this.domainEmoji = domainEmoji;
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
        holder.iconText.setText(domainEmoji);
        
        // Professional 3D-styled Badge Theme
        String status = topicItem.getStatus();
        if (TopicItem.STATUS_COMPLETED.equals(status)) {
            holder.statusText.setBackgroundResource(R.drawable.bg_3d_badge_completed);
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.ef_badge_success_text));
        } else if (TopicItem.STATUS_LEARNING.equals(status)) {
            holder.statusText.setBackgroundResource(R.drawable.bg_3d_badge_learning);
            holder.statusText.setTextColor(0xFF92400E); // Deep Amber
        } else {
            // NOT STARTED or default
            holder.statusText.setBackgroundResource(R.drawable.bg_3d_badge_silver);
            holder.statusText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.ef_text_secondary));
        }
        
        holder.itemView.setOnClickListener(v -> listener.onTopicClick(topicItem));
    }

    @Override
    public int getItemCount() {
        return filteredTopics.size();
    }

    public void submitTopics(List<TopicItem> topics) {
        originalTopics.clear();
        originalTopics.addAll(topics);
        filteredTopics = new java.util.ArrayList<>(topics);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        this.currentQuery = query != null ? query : "";
        applyFilters();
    }

    public void filterStatus(String status) {
        this.currentStatus = status != null ? status : "Tất cả";
        applyFilters();
    }

    private void applyFilters() {
        filteredTopics.clear();
        String q = currentQuery.toLowerCase().trim();
        
        for (TopicItem item : originalTopics) {
            boolean matchesQuery = q.isEmpty() || item.getTitle().toLowerCase().contains(q);
            boolean matchesStatus = "Tất cả".equals(currentStatus) || item.getStatus().equals(currentStatus);
            
            if (matchesQuery && matchesStatus) {
                filteredTopics.add(item);
            }
        }
        notifyDataSetChanged();
    }

    static class TopicViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView statusText;
        final TextView iconText;

        TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.topicName);
            statusText = itemView.findViewById(R.id.topicStatus);
            iconText = itemView.findViewById(R.id.topicIcon);
        }
    }
}
