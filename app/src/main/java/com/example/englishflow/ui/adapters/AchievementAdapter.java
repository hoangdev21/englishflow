package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AchievementItem;
import com.google.android.material.progressindicator.LinearProgressIndicator;

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
        holder.iconText.setText(item.getIcon());

        int target = Math.max(item.getTargetValue(), 1);
        int current = Math.max(item.getCurrentValue(), 0);
        int boundedCurrent = Math.min(current, target);

        holder.progressBar.setMax(target);
        holder.progressBar.setProgressCompat(boundedCurrent, false);
        holder.progressText.setText(boundedCurrent + "/" + target);
        holder.lockOverlay.setVisibility(item.isUnlocked() ? View.GONE : View.VISIBLE);

        if (item.isUnlocked()) {
            holder.container.setAlpha(1.0f);
            holder.titleText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_primary_dark));
            holder.iconFrame.setAlpha(1.0f);
            holder.iconText.setAlpha(1.0f);
            holder.progressBar.setIndicatorColor(holder.itemView.getContext().getColor(R.color.ef_primary));
            holder.progressText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_text_secondary));
        } else {
            holder.container.setAlpha(0.72f);
            holder.titleText.setTextColor(0xFF8892B0);
            holder.iconFrame.setAlpha(0.55f);
            holder.iconText.setAlpha(0.5f);
            holder.progressBar.setIndicatorColor(holder.itemView.getContext().getColor(R.color.ef_outline));
            holder.progressText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_text_tertiary));
        }
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    static class AchievementViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView descText;
        final TextView progressText;
        final TextView iconText;
        final LinearProgressIndicator progressBar;
        final ImageView lockOverlay;
        final View iconFrame;
        final View container;

        AchievementViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.achievementTitle);
            descText = itemView.findViewById(R.id.achievementDesc);
            progressText = itemView.findViewById(R.id.achievementProgressText);
            iconText = itemView.findViewById(R.id.achievementIcon);
            progressBar = itemView.findViewById(R.id.achievementProgress);
            lockOverlay = itemView.findViewById(R.id.achievementLock);
            iconFrame = itemView.findViewById(R.id.iconFrame);
            container = itemView.findViewById(R.id.achievementContainer);
        }
    }
}
