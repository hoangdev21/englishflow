package com.example.englishflow.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.englishflow.R;
import com.example.englishflow.data.LeaderboardItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private List<LeaderboardItem> items;
    private String currentUserEmail;
    private static final DecimalFormat scoreFormatter = new DecimalFormat("#,###");

    public LeaderboardAdapter(List<LeaderboardItem> items, String currentUserEmail) {
        this.items = new ArrayList<>(items);
        this.currentUserEmail = currentUserEmail;
    }

    public void updateData(List<LeaderboardItem> newItems) {
        List<LeaderboardItem> oldItems = new ArrayList<>(items);
        List<LeaderboardItem> updated = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();

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
                return Objects.equals(oldItems.get(oldItemPosition).email, updated.get(newItemPosition).email);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                LeaderboardItem oldItem = oldItems.get(oldItemPosition);
                LeaderboardItem newItem = updated.get(newItemPosition);
                return Objects.equals(oldItem.name, newItem.name)
                        && Objects.equals(oldItem.email, newItem.email)
                        && oldItem.score == newItem.score
                        && oldItem.rank == newItem.rank;
            }
        });

        this.items = updated;
        diffResult.dispatchUpdatesTo(this);
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
        
        if (item.avatarPath != null && !item.avatarPath.isEmpty()) {
            holder.imgAvatar.setVisibility(View.VISIBLE);
            holder.txtInitial.setVisibility(View.GONE);
            Glide.with(holder.itemView.getContext())
                .load(item.avatarPath)
                .placeholder(R.drawable.user_avatar)
                .circleCrop()
                .into(holder.imgAvatar);
        } else {
            holder.imgAvatar.setVisibility(View.GONE);
            holder.txtInitial.setVisibility(View.VISIBLE);
            holder.txtInitial.setText(item.name != null && !item.name.isEmpty() ? item.name.substring(0, 1).toUpperCase() : "?");
        }

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
        android.widget.ImageView imgAvatar;

        ViewHolder(View itemView) {
            super(itemView);
            txtRank = itemView.findViewById(R.id.txtRank);
            txtName = itemView.findViewById(R.id.txtName);
            txtEmail = itemView.findViewById(R.id.txtEmail);
            txtScore = itemView.findViewById(R.id.txtScore);
            txtInitial = itemView.findViewById(R.id.txtAvatarInitial);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
        }
    }
}
