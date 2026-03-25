package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CustomVocabularyManageAdapter extends RecyclerView.Adapter<CustomVocabularyManageAdapter.ViewHolder> {

    public interface ActionListener {
        void onEdit(CustomVocabularyEntity item);

        void onDelete(CustomVocabularyEntity item);

        void onToggleLock(CustomVocabularyEntity item);
    }

    private final List<CustomVocabularyEntity> items = new ArrayList<>();
    private final ActionListener actionListener;

    public CustomVocabularyManageAdapter(@NonNull ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submitList(@NonNull List<CustomVocabularyEntity> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_vocabulary_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomVocabularyEntity item = items.get(position);
        holder.wordText.setText(item.word);
        holder.meaningText.setText(item.meaning + "  •  " + item.source + " / " + item.domain);
        
        // Update lock icon based on state
        holder.lockButton.setIconResource(item.isLocked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
        holder.lockButton.setIconTintResource(item.isLocked ? R.color.ef_card_rose_text : R.color.ef_primary);

        holder.editButton.setOnClickListener(v -> actionListener.onEdit(item));
        holder.deleteButton.setOnClickListener(v -> actionListener.onDelete(item));
        holder.lockButton.setOnClickListener(v -> actionListener.onToggleLock(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView wordText;
        final TextView meaningText;
        final MaterialButton editButton;
        final MaterialButton deleteButton;
        final MaterialButton lockButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            wordText = itemView.findViewById(R.id.textWord);
            meaningText = itemView.findViewById(R.id.textMeaning);
            editButton = itemView.findViewById(R.id.btnEdit);
            deleteButton = itemView.findViewById(R.id.btnDelete);
            lockButton = itemView.findViewById(R.id.btnLock);
        }
    }
}
