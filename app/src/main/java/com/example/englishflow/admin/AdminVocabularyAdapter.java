package com.example.englishflow.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class AdminVocabularyAdapter extends ListAdapter<CustomVocabularyEntity, AdminVocabularyAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<CustomVocabularyEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CustomVocabularyEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull CustomVocabularyEntity oldItem,
                                               @NonNull CustomVocabularyEntity newItem) {
                    return buildIdentityKey(oldItem).equalsIgnoreCase(buildIdentityKey(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull CustomVocabularyEntity oldItem,
                                                  @NonNull CustomVocabularyEntity newItem) {
                    return buildIdentityKey(oldItem).equalsIgnoreCase(buildIdentityKey(newItem))
                            && safeText(oldItem.meaning).equals(safeText(newItem.meaning))
                            && safeText(oldItem.domain).equalsIgnoreCase(safeText(newItem.domain))
                            && safeText(oldItem.source).equalsIgnoreCase(safeText(newItem.source))
                            && oldItem.isLocked == newItem.isLocked
                            && oldItem.updatedAt == newItem.updatedAt;
                }
            };

    public interface ActionListener {
        void onEdit(@NonNull CustomVocabularyEntity item);

        void onToggleLock(@NonNull CustomVocabularyEntity item);
    }

    private final ActionListener actionListener;

    public AdminVocabularyAdapter() {
        this(null);
    }

    public AdminVocabularyAdapter(@Nullable ActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
        setHasStableIds(true);
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
        CustomVocabularyEntity item = getItem(position);
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
                ContextCompat.getColor(
                        holder.itemView.getContext(),
                        item.isLocked ? R.color.ef_card_rose_text : R.color.ef_card_blue_text
                )
        );

        holder.lockButton.setIconResource(item.isLocked ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
        holder.lockButton.setIconTintResource(item.isLocked ? R.color.ef_card_rose_text : R.color.ef_primary);

        if (actionListener != null) {
            holder.editButton.setOnClickListener(v -> actionListener.onEdit(item));
            holder.lockButton.setOnClickListener(v -> actionListener.onToggleLock(item));
        } else {
            holder.editButton.setOnClickListener(null);
            holder.lockButton.setOnClickListener(null);
        }
    }

    @Override
    public long getItemId(int position) {
        String stableKey = buildIdentityKey(getItem(position)).toLowerCase(Locale.US);
        return stableKey.hashCode();
    }

    private static String buildIdentityKey(@NonNull CustomVocabularyEntity item) {
        return safeText(item.word) + "|" + safeText(item.domain);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView wordText;
        final TextView meaningText;
        final TextView metaText;
        final TextView statusText;
        final MaterialButton editButton;
        final MaterialButton lockButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            wordText = itemView.findViewById(R.id.adminVocabWord);
            meaningText = itemView.findViewById(R.id.adminVocabMeaning);
            metaText = itemView.findViewById(R.id.adminVocabMeta);
            statusText = itemView.findViewById(R.id.adminVocabStatus);
            editButton = itemView.findViewById(R.id.adminVocabEditButton);
            lockButton = itemView.findViewById(R.id.adminVocabLockButton);
        }
    }
}
