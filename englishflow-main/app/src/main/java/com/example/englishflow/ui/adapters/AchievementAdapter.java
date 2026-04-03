package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AchievementItem;

import java.util.List;

public class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder> {

    private final List<AchievementItem> achievements;

    public AchievementAdapter(List<AchievementItem> achievements) {
        this.achievements = achievements;
    }

    @NonNull
    @Override
    public AchievementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_achievement, parent, false);
        return new AchievementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AchievementViewHolder holder, int position) {
        AchievementItem item = achievements.get(position);
        holder.titleText.setText(item.getTitle());
        holder.descText.setText(item.getDescription());
        holder.stateText.setText(item.isUnlocked() ? "Đã mở khoá" : "Chưa mở");
        holder.stateText.setAlpha(item.isUnlocked() ? 1f : 0.5f);
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    static class AchievementViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView descText;
        final TextView stateText;

        AchievementViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.achievementTitle);
            descText = itemView.findViewById(R.id.achievementDesc);
            stateText = itemView.findViewById(R.id.achievementState);
        }
    }
}
