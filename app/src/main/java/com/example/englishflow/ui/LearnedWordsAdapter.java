package com.example.englishflow.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.WordEntry;

import java.util.List;

public class LearnedWordsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> items;

    public LearnedWordsAdapter(List<Object> items) {
        this.items = items;
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
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_learned_word, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            String domain = (String) items.get(position);
            ((HeaderViewHolder) holder).txtHeader.setText(domain);
        } else if (holder instanceof ItemViewHolder) {
            WordEntry word = (WordEntry) items.get(position);
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            itemHolder.txtWord.setText(word.getWord());
            itemHolder.txtIpa.setText(word.getIpa());
            itemHolder.txtMeaning.setText(word.getMeaning());
            itemHolder.txtExample.setText(word.getExample());
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
        TextView txtWord, txtIpa, txtMeaning, txtExample;

        ItemViewHolder(View itemView) {
            super(itemView);
            txtWord = itemView.findViewById(R.id.txtWord);
            txtIpa = itemView.findViewById(R.id.txtIpa);
            txtMeaning = itemView.findViewById(R.id.txtMeaning);
            txtExample = itemView.findViewById(R.id.txtExample);
        }
    }
}
