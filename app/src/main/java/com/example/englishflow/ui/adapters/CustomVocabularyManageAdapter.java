package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CustomVocabularyManageAdapter extends ListAdapter<CustomVocabularyEntity, CustomVocabularyManageAdapter.ViewHolder> {

    public interface ActionListener {
        void onEdit(CustomVocabularyEntity item);

        void onDelete(CustomVocabularyEntity item);

        void onToggleLock(CustomVocabularyEntity item);
    }

    private static final DiffUtil.ItemCallback<CustomVocabularyEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CustomVocabularyEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull CustomVocabularyEntity oldItem,
                                               @NonNull CustomVocabularyEntity newItem) {
                    return normalizeWord(oldItem.word).equals(normalizeWord(newItem.word));
                }

                @Override
                public boolean areContentsTheSame(@NonNull CustomVocabularyEntity oldItem,
                                                  @NonNull CustomVocabularyEntity newItem) {
                    return Objects.equals(oldItem.meaning, newItem.meaning)
                            && Objects.equals(oldItem.ipa, newItem.ipa)
                            && Objects.equals(oldItem.example, newItem.example)
                            && Objects.equals(oldItem.exampleVi, newItem.exampleVi)
                            && Objects.equals(oldItem.usage, newItem.usage)
                            && Objects.equals(oldItem.source, newItem.source)
                            && Objects.equals(oldItem.domain, newItem.domain)
                            && oldItem.isLocked == newItem.isLocked
                            && oldItem.updatedAt == newItem.updatedAt;
                }

                private String normalizeWord(String value) {
                    return value == null ? "" : value.trim().toLowerCase(Locale.US);
                }
            };

    private final ActionListener actionListener;

    public CustomVocabularyManageAdapter(@NonNull ActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    public void submitList(@NonNull List<CustomVocabularyEntity> list) {
        super.submitList(new ArrayList<>(list));
    }

    @Override
    public long getItemId(int position) {
        CustomVocabularyEntity item = getItem(position);
        String key = item == null || item.word == null ? "" : item.word.trim().toLowerCase(Locale.US);
        return key.hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_vocabulary_manage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomVocabularyEntity item = getItem(position);
        holder.wordText.setText(item.word);

        String source = item.source == null || item.source.trim().isEmpty()
                ? "saved"
                : item.source.trim().toLowerCase(Locale.US);
        String domain = item.domain == null || item.domain.trim().isEmpty() ? "general" : item.domain.trim();
        String meaning = item.meaning == null ? "" : item.meaning;
        holder.meaningText.setText(meaning + "  •  " + source + " / " + domain);

        holder.lockButton.setIconResource(resolveSourceIcon(source));
        holder.lockButton.setIconTintResource(R.color.ef_primary);

        holder.editButton.setOnClickListener(v -> actionListener.onEdit(item));
        holder.deleteButton.setOnClickListener(v -> actionListener.onDelete(item));
        holder.lockButton.setOnClickListener(v -> actionListener.onToggleLock(item));
    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    private int resolveSourceIcon(String source) {
        if ("chat".equals(source)) {
            return R.drawable.ic_nav_chat;
        }
        if ("scan".equals(source)) {
            return R.drawable.ic_search;
        }
        if ("journey".equals(source)) {
            return R.drawable.ic_nav_learn;
        }
        if ("dictionary".equals(source)) {
            return R.drawable.ic_bookmark;
        }
        return R.drawable.ic_sort;
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
