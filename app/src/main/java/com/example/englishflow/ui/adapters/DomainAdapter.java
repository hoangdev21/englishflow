package com.example.englishflow.ui.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.DomainItem;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public void filter(String query) {
        if (query.isEmpty()) {
            filteredDomains = new ArrayList<>(originalDomains);
        } else {
            String lowercaseQuery = query.toLowerCase().trim();
            filteredDomains = originalDomains.stream()
                    .filter(item -> item.getName().toLowerCase().contains(lowercaseQuery))
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    public void sort(boolean ascending) {
        filteredDomains.sort((a, b) -> ascending ? 
            a.getName().compareToIgnoreCase(b.getName()) : 
            b.getName().compareToIgnoreCase(a.getName()));
        notifyDataSetChanged();
    }
    
    public void sortByProgress(boolean ascending) {
        filteredDomains.sort((a, b) -> ascending ? 
            Integer.compare(a.getProgress(), b.getProgress()) : 
            Integer.compare(b.getProgress(), a.getProgress()));
        notifyDataSetChanged();
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

        // Apply color to emoji circle
        int color = Color.parseColor(item.getGradientStart());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        // Add a subtle alpha for "glass" effect if needed, but here we want "button" feel
        // Let's use 20% alpha of the color for the container background and 100% for something else?
        // No, let's keep it simple: solid or 15% alpha
        circle.setColor(adjustAlpha(color, 0.15f));
        holder.emojiContainer.setBackground(circle);
        
        // Icon color
        holder.emojiText.setTextColor(color);

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

        DomainViewHolder(@NonNull View itemView) {
            super(itemView);
            emojiContainer = itemView.findViewById(R.id.emojiContainer);
            emojiText = itemView.findViewById(R.id.domainEmoji);
            nameText = itemView.findViewById(R.id.domainName);
            progressText = itemView.findViewById(R.id.domainProgressText);
            progressPercent = itemView.findViewById(R.id.domainProgressPercent);
            progressIndicator = itemView.findViewById(R.id.domainProgress);
        }
    }
}

