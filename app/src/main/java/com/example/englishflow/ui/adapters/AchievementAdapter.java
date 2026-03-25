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
        holder.iconText.setText(item.getIcon());
        
        holder.stateText.setText(item.isUnlocked() ? "ĐÃ MỞ" : "CHƯA MỞ");
        
        if (item.isUnlocked()) {
            holder.container.setAlpha(1.0f);
            holder.stateText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_primary_dark));
            holder.titleText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_primary_dark));
            holder.iconFrame.setAlpha(1.0f);
            holder.iconText.setAlpha(1.0f);
        } else {
            holder.container.setAlpha(0.6f);
            holder.stateText.setTextColor(0xFF8892B0); // Subdued blue-grey
            holder.titleText.setTextColor(0xFF8892B0);
            holder.iconFrame.setAlpha(0.4f);
            holder.iconText.setAlpha(0.4f);
        }
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    static class AchievementViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView descText;
        final TextView stateText;
        final TextView iconText;
        final View iconFrame;
        final View container;

        AchievementViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.achievementTitle);
            descText = itemView.findViewById(R.id.achievementDesc);
            stateText = itemView.findViewById(R.id.achievementState);
            iconText = itemView.findViewById(R.id.achievementIcon);
            iconFrame = itemView.findViewById(R.id.iconFrame);
            container = itemView.findViewById(R.id.achievementContainer);
        }
    }
}
