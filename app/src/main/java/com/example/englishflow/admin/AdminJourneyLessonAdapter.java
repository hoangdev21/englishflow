package com.example.englishflow.admin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AdminJourneyLessonAdapter extends ListAdapter<AdminJourneyLessonItem, AdminJourneyLessonAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<AdminJourneyLessonItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AdminJourneyLessonItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull AdminJourneyLessonItem oldItem,
                                               @NonNull AdminJourneyLessonItem newItem) {
                    return stableKey(oldItem).equalsIgnoreCase(stableKey(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull AdminJourneyLessonItem oldItem,
                                                  @NonNull AdminJourneyLessonItem newItem) {
                    return nonEmpty(oldItem.getDocumentId()).equalsIgnoreCase(nonEmpty(newItem.getDocumentId()))
                            && nonEmpty(oldItem.getLessonId()).equalsIgnoreCase(nonEmpty(newItem.getLessonId()))
                            && nonEmpty(oldItem.getTitle()).equals(nonEmpty(newItem.getTitle()))
                            && nonEmpty(oldItem.getMinLevel()).equalsIgnoreCase(nonEmpty(newItem.getMinLevel()))
                            && nonEmpty(oldItem.getStatus()).equalsIgnoreCase(nonEmpty(newItem.getStatus()))
                            && oldItem.getOrder() == newItem.getOrder()
                            && oldItem.getMinExchanges() == newItem.getMinExchanges()
                            && oldItem.getVocabularyCount() == newItem.getVocabularyCount()
                            && keywordsEqual(oldItem.getKeywords(), newItem.getKeywords());
                }
            };

    public interface ActionListener {
        void onViewDetails(@NonNull AdminJourneyLessonItem item);

        void onEdit(@NonNull AdminJourneyLessonItem item);

        void onToggleVisibility(@NonNull AdminJourneyLessonItem item);

        void onDelete(@NonNull AdminJourneyLessonItem item);
    }

    private static final int[] ICON_CYCLE = {
            R.drawable.hello,
            R.drawable.happy,
            R.drawable.family,
            R.drawable.coffee,
            R.drawable.work,
            R.drawable.fashion,
            R.drawable.weather,
            R.drawable.house,
            R.drawable.hobby,
            R.drawable.goodbye
    };

    private final ActionListener actionListener;

    public AdminJourneyLessonAdapter(@NonNull ActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_journey_lesson, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminJourneyLessonItem item = getItem(position);
        holder.iconView.setImageResource(resolveIconRes(item));

        holder.titleText.setText(nonEmpty(item.getTitle(), item.getLessonId()));
        holder.metaText.setText(holder.itemView.getResources().getString(
                R.string.admin_content_journey_meta,
                nonEmpty(item.getLessonId(), "unknown"),
                nonEmpty(item.getMinLevel(), "A1"),
                item.getOrder(),
                item.getVocabularyCount(),
                item.getMinExchanges()
        ));

        String keywordText = TextUtils.join(", ", limitKeywords(item.getKeywords(), 5));
        if (keywordText.trim().isEmpty()) {
            keywordText = holder.itemView.getResources().getString(R.string.admin_content_journey_no_keyword);
        }
        holder.keywordText.setText(holder.itemView.getResources().getString(
                R.string.admin_content_journey_keywords,
                keywordText
        ));

        boolean visible = item.isVisibleToLearner();
        holder.statusText.setText(visible
                ? holder.itemView.getResources().getString(R.string.admin_content_journey_status_visible)
                : holder.itemView.getResources().getString(R.string.admin_content_journey_status_hidden));
        holder.statusText.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                visible ? R.color.ef_card_blue_text : R.color.ef_card_rose_text
        ));

        holder.toggleButton.setText(visible
                ? holder.itemView.getResources().getString(R.string.admin_content_journey_hide)
                : holder.itemView.getResources().getString(R.string.admin_content_journey_show));
        holder.toggleButton.setIconResource(visible ? R.drawable.ic_lock_closed : R.drawable.ic_lock_open);
        holder.toggleButton.setIconTintResource(visible ? R.color.ef_card_rose_text : R.color.ef_primary);

        holder.itemView.setOnClickListener(v -> actionListener.onViewDetails(item));
        holder.detailButton.setOnClickListener(v -> actionListener.onViewDetails(item));
        holder.editButton.setOnClickListener(v -> actionListener.onEdit(item));
        holder.toggleButton.setOnClickListener(v -> actionListener.onToggleVisibility(item));
        holder.deleteButton.setOnClickListener(v -> actionListener.onDelete(item));
    }

    @Override
    public long getItemId(int position) {
        return stableKey(getItem(position)).toLowerCase(Locale.US).hashCode();
    }

    private int resolveIconRes(@NonNull AdminJourneyLessonItem item) {
        String combined = (item.getLessonId() + " " + item.getTitle()).toLowerCase(Locale.US);
        if (combined.contains("hello") || combined.contains("chao")) return R.drawable.hello;
        if (combined.contains("happy") || combined.contains("cam xuc")) return R.drawable.happy;
        if (combined.contains("family") || combined.contains("gia dinh")) return R.drawable.family;
        if (combined.contains("coffee") || combined.contains("breakfast") || combined.contains("bua sang")) return R.drawable.coffee;
        if (combined.contains("work") || combined.contains("job") || combined.contains("cong viec")) return R.drawable.work;
        if (combined.contains("fashion") || combined.contains("thoi trang")) return R.drawable.fashion;
        if (combined.contains("weather") || combined.contains("thoi tiet")) return R.drawable.weather;
        if (combined.contains("home") || combined.contains("house") || combined.contains("ngoi nha")) return R.drawable.house;
        if (combined.contains("hobby") || combined.contains("so thich")) return R.drawable.hobby;
        if (combined.contains("bye") || combined.contains("farewell") || combined.contains("hen gap")) return R.drawable.goodbye;

        int index = Math.max(0, item.getOrder() - 1) % ICON_CYCLE.length;
        return ICON_CYCLE[index];
    }

    private List<String> limitKeywords(List<String> keywords, int maxCount) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        int safeMax = Math.max(1, maxCount);
        List<String> result = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            result.add(keyword.trim());
            if (result.size() >= safeMax) {
                break;
            }
        }
        return result;
    }

    private static boolean keywordsEqual(@Nullable List<String> left, @Nullable List<String> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!nonEmpty(left.get(i)).equalsIgnoreCase(nonEmpty(right.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String stableKey(@NonNull AdminJourneyLessonItem item) {
        return nonEmpty(item.getDocumentId(), item.getLessonId(), item.getTitle());
    }

    private static String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconView;
        final TextView titleText;
        final TextView metaText;
        final TextView keywordText;
        final TextView statusText;
        final MaterialButton detailButton;
        final MaterialButton editButton;
        final MaterialButton toggleButton;
        final MaterialButton deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.adminJourneyIcon);
            titleText = itemView.findViewById(R.id.adminJourneyTitle);
            metaText = itemView.findViewById(R.id.adminJourneyMeta);
            keywordText = itemView.findViewById(R.id.adminJourneyKeywords);
            statusText = itemView.findViewById(R.id.adminJourneyStatus);
            detailButton = itemView.findViewById(R.id.adminJourneyDetailButton);
            editButton = itemView.findViewById(R.id.adminJourneyEditButton);
            toggleButton = itemView.findViewById(R.id.adminJourneyVisibilityButton);
            deleteButton = itemView.findViewById(R.id.adminJourneyDeleteButton);
        }
    }
}
