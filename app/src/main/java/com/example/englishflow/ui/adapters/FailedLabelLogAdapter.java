package com.example.englishflow.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.database.entity.FailedLabelLogEntity;

import java.util.ArrayList;
import java.util.List;

public class FailedLabelLogAdapter extends RecyclerView.Adapter<FailedLabelLogAdapter.ViewHolder> {

    private final List<FailedLabelLogEntity> items = new ArrayList<>();

    public void submitList(@NonNull List<FailedLabelLogEntity> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_failed_label_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FailedLabelLogEntity item = items.get(position);
        holder.labelText.setText(item.label);

        String alias = item.suggestedAlias == null || item.suggestedAlias.trim().isEmpty()
                ? "(chua co)"
                : item.suggestedAlias;
        holder.metaText.setText("So lan fail: " + item.failCount + "  •  Alias goi y: " + alias);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView labelText;
        final TextView metaText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            labelText = itemView.findViewById(R.id.textFailedLabel);
            metaText = itemView.findViewById(R.id.textFailedMeta);
        }
    }
}
