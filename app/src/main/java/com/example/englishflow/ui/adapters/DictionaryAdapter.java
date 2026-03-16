package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.WordEntry;

import java.util.List;

public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryAdapter.DictionaryViewHolder> {

    public interface DictionaryActionListener {
        void onPronounce(WordEntry wordEntry);
        void onDelete(WordEntry wordEntry);
    }

    private final List<WordEntry> words;
    private final DictionaryActionListener listener;

    public DictionaryAdapter(List<WordEntry> words, DictionaryActionListener listener) {
        this.words = words;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DictionaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dictionary_word, parent, false);
        return new DictionaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DictionaryViewHolder holder, int position) {
        WordEntry item = words.get(position);
        holder.wordText.setText(item.getWord() + " " + item.getIpa());
        holder.meaningText.setText(item.getMeaning() + " • " + item.getCategory());
        holder.pronounceButton.setOnClickListener(v -> listener.onPronounce(item));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    static class DictionaryViewHolder extends RecyclerView.ViewHolder {
        final TextView wordText;
        final TextView meaningText;
        final ImageButton pronounceButton;
        final ImageButton deleteButton;

        DictionaryViewHolder(@NonNull View itemView) {
            super(itemView);
            wordText = itemView.findViewById(R.id.dictionaryWord);
            meaningText = itemView.findViewById(R.id.dictionaryMeaning);
            pronounceButton = itemView.findViewById(R.id.btnPronounceDictionary);
            deleteButton = itemView.findViewById(R.id.btnDeleteDictionary);
        }
    }
}
