package com.example.englishflow.ui.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.DomainItem;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class DomainAdapter extends RecyclerView.Adapter<DomainAdapter.DomainViewHolder> {

    public interface OnDomainClickListener {
        void onDomainClick(DomainItem domainItem);
    }

    private final List<DomainItem> domains;
    private final OnDomainClickListener listener;

    public DomainAdapter(List<DomainItem> domains, OnDomainClickListener listener) {
        this.domains = domains;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DomainViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_domain, parent, false);
        return new DomainViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DomainViewHolder holder, int position) {
        DomainItem item = domains.get(position);
        holder.emojiText.setText(item.getEmoji());
        holder.nameText.setText(item.getName());
        holder.progressText.setText(item.getProgress() + "% đã học");
        holder.progressIndicator.setProgress(item.getProgress());

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                new int[]{Color.parseColor(item.getGradientStart()), Color.parseColor(item.getGradientEnd())}
        );
        gradient.setCornerRadius(22f);
        holder.backgroundLayout.setBackground(gradient);

        holder.itemView.setOnClickListener(v -> listener.onDomainClick(item));
    }

    @Override
    public int getItemCount() {
        return domains.size();
    }

    static class DomainViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout backgroundLayout;
        final TextView emojiText;
        final TextView nameText;
        final TextView progressText;
        final LinearProgressIndicator progressIndicator;

        DomainViewHolder(@NonNull View itemView) {
            super(itemView);
            backgroundLayout = itemView.findViewById(R.id.domainBg);
            emojiText = itemView.findViewById(R.id.domainEmoji);
            nameText = itemView.findViewById(R.id.domainName);
            progressText = itemView.findViewById(R.id.domainProgressText);
            progressIndicator = itemView.findViewById(R.id.domainProgress);
        }
    }
}
