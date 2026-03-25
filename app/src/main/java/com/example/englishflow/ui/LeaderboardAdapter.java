package com.example.englishflow.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.LeaderboardItem;

import java.text.DecimalFormat;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private List<LeaderboardItem> items;
    private String currentUserEmail;
    private static final DecimalFormat scoreFormatter = new DecimalFormat("#,###");

    public LeaderboardAdapter(List<LeaderboardItem> items, String currentUserEmail) {
        this.items = items;
        this.currentUserEmail = currentUserEmail;
    }

    public void updateData(List<LeaderboardItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardItem item = items.get(position);
        
        holder.txtRank.setText(String.valueOf(item.rank));
        holder.txtName.setText(item.name);
        holder.txtEmail.setText(item.email);
        holder.txtScore.setText(scoreFormatter.format(item.score));
        holder.txtInitial.setText(item.name != null && !item.name.isEmpty() ? item.name.substring(0, 1).toUpperCase() : "?");

        // Highlight current user
        if (currentUserEmail != null && currentUserEmail.equals(item.email)) {
            holder.itemView.setBackgroundResource(R.drawable.bg_stats_premium_card_highlighted);
            holder.txtName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary));
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_stats_premium_card);
            holder.txtName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_primary));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtRank, txtName, txtEmail, txtScore, txtInitial;

        ViewHolder(View itemView) {
            super(itemView);
            txtRank = itemView.findViewById(R.id.txtRank);
            txtName = itemView.findViewById(R.id.txtName);
            txtEmail = itemView.findViewById(R.id.txtEmail);
            txtScore = itemView.findViewById(R.id.txtScore);
            txtInitial = itemView.findViewById(R.id.txtAvatarInitial);
        }
    }
}
