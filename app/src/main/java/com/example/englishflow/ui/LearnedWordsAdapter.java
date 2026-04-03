package com.example.englishflow.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.LearnedWordEntity;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class LearnedWordsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface DictionaryActionListener {
        void onPronounce(LearnedWordEntity word);
        void onDelete(LearnedWordEntity word);
    }

    private List<Object> items;
    private DictionaryActionListener listener;

    public LearnedWordsAdapter(List<Object> items, DictionaryActionListener listener) {
        this.items = new ArrayList<>(items);
        this.listener = listener;
    }

    public void updateData(List<Object> newItems) {
        List<Object> oldItems = new ArrayList<>(items);
        List<Object> updated = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return updated.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = updated.get(newItemPosition);

                if (oldItem instanceof String && newItem instanceof String) {
                    return oldItem.equals(newItem);
                }
                if (oldItem instanceof LearnedWordEntity && newItem instanceof LearnedWordEntity) {
                    LearnedWordEntity oldWord = (LearnedWordEntity) oldItem;
                    LearnedWordEntity newWord = (LearnedWordEntity) newItem;
                    if (oldWord.id != 0 && newWord.id != 0) {
                        return oldWord.id == newWord.id;
                    }
                    return Objects.equals(oldWord.word, newWord.word)
                            && oldWord.learnedAt == newWord.learnedAt;
                }
                return false;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Object oldItem = oldItems.get(oldItemPosition);
                Object newItem = updated.get(newItemPosition);

                if (oldItem instanceof String && newItem instanceof String) {
                    return oldItem.equals(newItem);
                }
                if (oldItem instanceof LearnedWordEntity && newItem instanceof LearnedWordEntity) {
                    LearnedWordEntity oldWord = (LearnedWordEntity) oldItem;
                    LearnedWordEntity newWord = (LearnedWordEntity) newItem;
                    return Objects.equals(oldWord.word, newWord.word)
                            && Objects.equals(oldWord.ipa, newWord.ipa)
                            && Objects.equals(oldWord.meaning, newWord.meaning)
                            && Objects.equals(oldWord.domain, newWord.domain)
                            && oldWord.learnedAt == newWord.learnedAt;
                }
                return false;
            }
        });

        this.items = updated;
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_HEADER;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_learned_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            // Use the dictionary layout which has pronounce and delete icons
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dictionary_word, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            String dateLabel = (String) items.get(position);
            ((HeaderViewHolder) holder).txtHeader.setText(dateLabel);
        } else if (holder instanceof ItemViewHolder) {
            LearnedWordEntity word = (LearnedWordEntity) items.get(position);
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            
            // Format word with IPA
            String ipa = (word.ipa != null && !word.ipa.isEmpty()) ? " " + word.ipa : "";
            itemHolder.txtWord.setText(word.word + ipa);
            
            // Subtitle: meaning + domain
            String domain = (word.domain != null && !word.domain.isEmpty()) ? word.domain : "Khác";
            String meaning = (word.meaning != null) ? word.meaning : "";
            itemHolder.txtMeaning.setText(meaning + " • " + domain);

            itemHolder.btnPronounce.setOnClickListener(v -> {
                if (listener != null) listener.onPronounce(word);
            });

            itemHolder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(word);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView txtHeader;

        HeaderViewHolder(View itemView) {
            super(itemView);
            txtHeader = itemView.findViewById(R.id.txtHeader);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView txtWord, txtMeaning;
        View btnPronounce, btnDelete;

        ItemViewHolder(View itemView) {
            super(itemView);
            txtWord = itemView.findViewById(R.id.dictionaryWord);
            txtMeaning = itemView.findViewById(R.id.dictionaryMeaning);
            btnPronounce = itemView.findViewById(R.id.btnPronounceDictionary);
            btnDelete = itemView.findViewById(R.id.btnDeleteDictionary);
        }
    }
}
