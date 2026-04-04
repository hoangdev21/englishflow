package com.example.englishflow.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.CustomVocabularyEntity;

import java.util.ArrayList;
import java.util.List;

public class AdminVocabularyAdapter extends RecyclerView.Adapter<AdminVocabularyAdapter.ViewHolder> {

    private final List<CustomVocabularyEntity> items = new ArrayList<>();

    public void submitList(@NonNull List<CustomVocabularyEntity> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_vocabulary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomVocabularyEntity item = items.get(position);
        String word = safeText(item.word);
        String meaning = safeText(item.meaning);
        String domain = safeText(item.domain);
        String source = safeText(item.source);

        if (meaning.isEmpty()) {
            meaning = "(chua cap nhat nghia)";
        }
        if (domain.isEmpty()) {
            domain = "general";
        }
        if (source.isEmpty()) {
            source = "unknown";
        }

        holder.wordText.setText(word.isEmpty() ? "(empty-word)" : word);
        holder.meaningText.setText(meaning);
        holder.metaText.setText(domain + " • " + source);
        holder.statusText.setText(item.isLocked ? "LOCKED" : "OPEN");
        holder.statusText.setTextColor(
                holder.itemView.getResources().getColor(
                        item.isLocked ? R.color.ef_card_rose_text : R.color.ef_card_blue_text
                )
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView wordText;
        final TextView meaningText;
        final TextView metaText;
        final TextView statusText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            wordText = itemView.findViewById(R.id.adminVocabWord);
            meaningText = itemView.findViewById(R.id.adminVocabMeaning);
            metaText = itemView.findViewById(R.id.adminVocabMeta);
            statusText = itemView.findViewById(R.id.adminVocabStatus);
        }
    }
}
