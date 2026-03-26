package com.example.englishflow.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.LearnedWordEntity;

import java.util.List;

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
        this.items = items;
        this.listener = listener;
    }

    public void updateData(List<Object> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
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
