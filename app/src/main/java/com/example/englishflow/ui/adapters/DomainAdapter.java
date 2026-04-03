package com.example.englishflow.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.DomainItem;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DomainAdapter extends RecyclerView.Adapter<DomainAdapter.DomainViewHolder> {

    public static final int VIEW_TYPE_GRID = 0;
    public static final int VIEW_TYPE_LIST = 1;

    public interface OnDomainClickListener {
        void onDomainClick(DomainItem domainItem);
    }

    private final List<DomainItem> originalDomains;
    private List<DomainItem> filteredDomains;
    private final OnDomainClickListener listener;
    private int currentViewType = VIEW_TYPE_GRID;

    public DomainAdapter(List<DomainItem> domains, OnDomainClickListener listener) {
        this.originalDomains = new ArrayList<>(domains);
        this.filteredDomains = new ArrayList<>(domains);
        this.listener = listener;
    }

    public void setViewType(int viewType) {
        this.currentViewType = viewType;
        notifyDataSetChanged();
    }

    public int getViewType() {
        return currentViewType;
    }

    public void submitDomains(List<DomainItem> domains) {
        originalDomains.clear();
        originalDomains.addAll(domains);
        updateFilteredDomains(new ArrayList<>(domains));
    }

    public void filter(String query) {
        List<DomainItem> next;
        if (query.isEmpty()) {
            next = new ArrayList<>(originalDomains);
        } else {
            String lowercaseQuery = query.toLowerCase().trim();
            next = originalDomains.stream()
                    .filter(item -> item.getName().toLowerCase().contains(lowercaseQuery))
                    .collect(Collectors.toList());
        }
        updateFilteredDomains(next);
    }

    public void sort(boolean ascending) {
        List<DomainItem> next = new ArrayList<>(filteredDomains);
        next.sort((a, b) -> ascending ?
            a.getName().compareToIgnoreCase(b.getName()) : 
            b.getName().compareToIgnoreCase(a.getName()));
        updateFilteredDomains(next);
    }
    
    public void sortByProgress(boolean ascending) {
        List<DomainItem> next = new ArrayList<>(filteredDomains);
        next.sort((a, b) -> ascending ?
            Integer.compare(a.getProgress(), b.getProgress()) : 
            Integer.compare(b.getProgress(), a.getProgress()));
        updateFilteredDomains(next);
    }

    private void updateFilteredDomains(List<DomainItem> next) {
        List<DomainItem> old = new ArrayList<>(filteredDomains);
        List<DomainItem> updated = next != null ? next : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return updated.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return old.get(oldItemPosition).getName().equals(updated.get(newItemPosition).getName());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                DomainItem oldItem = old.get(oldItemPosition);
                DomainItem newItem = updated.get(newItemPosition);
                return oldItem.getProgress() == newItem.getProgress()
                    && Objects.equals(oldItem.getEmoji(), newItem.getEmoji())
                    && Objects.equals(oldItem.getGradientStart(), newItem.getGradientStart())
                    && Objects.equals(oldItem.getGradientEnd(), newItem.getGradientEnd())
                        && oldItem.getBackgroundImageRes() == newItem.getBackgroundImageRes();
            }
        });

        filteredDomains = new ArrayList<>(updated);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return currentViewType;
    }

    @NonNull
    @Override
    public DomainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == VIEW_TYPE_GRID) ? R.layout.item_domain : R.layout.item_domain_list;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new DomainViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DomainViewHolder holder, int position) {
        DomainItem item = filteredDomains.get(position);
        holder.emojiText.setText(item.getEmoji());
        holder.nameText.setText(item.getName());
        if (holder.progressText != null) {
            holder.progressText.setText(item.getProgress() + "% đã học");
        }
        holder.progressIndicator.setProgress(item.getProgress());
        holder.progressPercent.setText(item.getProgress() + "%");

        // Set default styles
        holder.nameText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_text_primary));
        if (holder.progressText != null) {
            holder.progressText.setTextColor(holder.itemView.getContext().getColor(R.color.ef_text_secondary));
        }
        holder.progressIndicator.setIndicatorColor(holder.itemView.getContext().getColor(R.color.ef_primary));
        holder.progressIndicator.setTrackColor(holder.itemView.getContext().getColor(R.color.ef_xp_track));
        holder.backgroundImage.setVisibility(View.GONE);
        holder.overlay.setVisibility(View.GONE);

        // Dynamic background handling
        if (item.getBackgroundImageRes() != 0) {
            holder.backgroundImage.setVisibility(View.VISIBLE);
            holder.backgroundImage.setImageResource(item.getBackgroundImageRes());
            holder.overlay.setVisibility(View.VISIBLE);
            
            // Brighten text for dark background
            holder.nameText.setTextColor(Color.WHITE);
            if (holder.progressText != null) {
                holder.progressText.setTextColor(Color.parseColor("#CCCCCC"));
            }
            holder.progressIndicator.setIndicatorColor(Color.WHITE);
            holder.progressIndicator.setTrackColor(Color.parseColor("#4DFFFFFF")); // 30% white
        }

        // Apply color to emoji circle
        int color = Color.parseColor(item.getGradientStart());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        // If it's a special card with background, maybe keep the emoji circle but make it more translucent
        float alpha = item.getBackgroundImageRes() != 0 ? 0.3f : 0.15f;
        circle.setColor(adjustAlpha(color, alpha));
        holder.emojiContainer.setBackground(circle);
        
        // Icon color - if background is dark, maybe make icon colored or white
        holder.emojiText.setTextColor(item.getBackgroundImageRes() != 0 ? Color.WHITE : color);

        holder.itemView.setOnClickListener(v -> listener.onDomainClick(item));
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        if (alpha == 0 && factor > 0) alpha = (int)(255 * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public int getItemCount() {
        return filteredDomains.size();
    }

    static class DomainViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout emojiContainer;
        final TextView emojiText;
        final TextView nameText;
        final TextView progressText;
        final TextView progressPercent;
        final LinearProgressIndicator progressIndicator;
        final android.widget.ImageView backgroundImage;
        final View overlay;

        DomainViewHolder(@NonNull View itemView) {
            super(itemView);
            emojiContainer = itemView.findViewById(R.id.emojiContainer);
            emojiText = itemView.findViewById(R.id.domainEmoji);
            nameText = itemView.findViewById(R.id.domainName);
            progressText = itemView.findViewById(R.id.domainProgressText);
            progressPercent = itemView.findViewById(R.id.domainProgressPercent);
            progressIndicator = itemView.findViewById(R.id.domainProgress);
            backgroundImage = itemView.findViewById(R.id.domainBackground);
            overlay = itemView.findViewById(R.id.domainOverlay);
        }
    }
}

