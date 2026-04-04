package com.example.englishflow.admin;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.CloudUserProfile;
import com.example.englishflow.data.FirebaseUserStore;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    public interface Listener {
        void onViewDetail(CloudUserProfile profile);
        void onEditDisplayName(CloudUserProfile profile);
        void onToggleRole(CloudUserProfile profile);
        void onResetPassword(CloudUserProfile profile);
        void onDeleteUser(CloudUserProfile profile);
        void onToggleStatus(CloudUserProfile profile);
    }

    private final Listener listener;
    private final List<CloudUserProfile> items = new ArrayList<>();
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#,###");

    public AdminUserAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void updateData(@NonNull List<CloudUserProfile> nextItems) {
        List<CloudUserProfile> oldItems = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return nextItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(oldItems.get(oldItemPosition).uid, nextItems.get(newItemPosition).uid);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CloudUserProfile oldItem = oldItems.get(oldItemPosition);
                CloudUserProfile newItem = nextItems.get(newItemPosition);
                return Objects.equals(oldItem.uid, newItem.uid)
                        && Objects.equals(oldItem.displayName, newItem.displayName)
                        && Objects.equals(oldItem.email, newItem.email)
                    && Objects.equals(oldItem.photoUrl, newItem.photoUrl)
                        && Objects.equals(oldItem.role, newItem.role)
                        && Objects.equals(oldItem.status, newItem.status)
                    && oldItem.isOnline == newItem.isOnline
                        && oldItem.totalXp == newItem.totalXp
                        && oldItem.currentStreak == newItem.currentStreak
                    && oldItem.learnedWords == newItem.learnedWords
                    && oldItem.lastActiveAt == newItem.lastActiveAt
                    && oldItem.lastLoginAt == newItem.lastLoginAt
                    && oldItem.lastStudyAt == newItem.lastStudyAt
                    && oldItem.updatedAt == newItem.updatedAt;
            }
        });

        items.clear();
        items.addAll(nextItems);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CloudUserProfile profile = items.get(position);
        long now = System.currentTimeMillis();

        holder.name.setText(profile.displayName);
        holder.email.setText(profile.email);
        holder.xpValue.setText(SCORE_FORMAT.format(profile.totalXp));
        holder.streakValue.setText(String.valueOf(profile.currentStreak));
        holder.wordsValue.setText(String.valueOf(profile.learnedWords));

        bindAvatar(holder, profile);
        bindPresence(holder, profile, now);

        boolean isAdmin = FirebaseUserStore.ROLE_ADMIN.equalsIgnoreCase(profile.role);
        holder.roleChip.setText(isAdmin ? "ADMIN" : "USER");
        holder.roleChip.setChipBackgroundColorResource(isAdmin ? R.color.ef_card_blue : R.color.ef_primary_container);
        holder.roleChip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), isAdmin ? R.color.ef_card_blue_text : R.color.ef_primary));

        bindStatusChip(holder, profile);
        bindToggleStatusButton(holder, profile);

        holder.btnViewDetail.setOnClickListener(v -> listener.onViewDetail(profile));
        holder.btnToggleStatus.setOnClickListener(v -> listener.onToggleStatus(profile));
        holder.btnMore.setOnClickListener(v -> showMoreActions(v, profile));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView avatarImage;
        final TextView avatarFallback;
        final TextView name;
        final TextView email;
        final TextView presence;
        final View presenceDot;
        final Chip roleChip;
        final Chip statusChip;
        final TextView xpValue;
        final TextView streakValue;
        final TextView wordsValue;
        final MaterialButton btnViewDetail;
        final MaterialButton btnToggleStatus;
        final MaterialButton btnMore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.adminUserAvatarImage);
            avatarFallback = itemView.findViewById(R.id.adminUserAvatarFallback);
            name = itemView.findViewById(R.id.adminUserName);
            email = itemView.findViewById(R.id.adminUserEmail);
            presence = itemView.findViewById(R.id.adminUserPresence);
            presenceDot = itemView.findViewById(R.id.adminUserPresenceDot);
            roleChip = itemView.findViewById(R.id.adminUserRoleChip);
            statusChip = itemView.findViewById(R.id.adminUserStatusChip);
            xpValue = itemView.findViewById(R.id.adminUserXpValue);
            streakValue = itemView.findViewById(R.id.adminUserStreakValue);
            wordsValue = itemView.findViewById(R.id.adminUserWordsValue);
            btnViewDetail = itemView.findViewById(R.id.btnAdminViewDetail);
            btnToggleStatus = itemView.findViewById(R.id.btnAdminToggleStatus);
            btnMore = itemView.findViewById(R.id.btnAdminMore);
        }
    }

    private void bindAvatar(@NonNull ViewHolder holder, @NonNull CloudUserProfile profile) {
        String initial = "U";
        if (!TextUtils.isEmpty(profile.displayName)) {
            initial = profile.displayName.substring(0, 1).toUpperCase(Locale.getDefault());
        }
        holder.avatarFallback.setText(initial);

        if (TextUtils.isEmpty(profile.photoUrl)) {
            holder.avatarImage.setVisibility(View.GONE);
            holder.avatarFallback.setVisibility(View.VISIBLE);
            return;
        }

        holder.avatarImage.setVisibility(View.VISIBLE);
        holder.avatarFallback.setVisibility(View.GONE);
        Glide.with(holder.avatarImage)
                .load(profile.photoUrl)
                .circleCrop()
                .into(holder.avatarImage);
    }

    private void bindPresence(@NonNull ViewHolder holder, @NonNull CloudUserProfile profile, long now) {
        int dotColor;
        String label;
        if (profile.isDeleted()) {
            dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_error);
            label = "Đã xóa khỏi hệ thống";
        } else if (profile.isActiveNow(now)) {
            dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_success);
            label = "Đang hoạt động";
        } else {
            long lastSeen = profile.getLastSeenAt();
            if (lastSeen <= 0L) {
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_text_tertiary);
                label = "Chưa có dữ liệu truy cập";
            } else {
                dotColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_warning_text);
                label = formatLastSeen(lastSeen, now);
            }
        }

        holder.presenceDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        holder.presence.setText(label);
    }

    private void bindStatusChip(@NonNull ViewHolder holder, @NonNull CloudUserProfile profile) {
        if (profile.isDeleted()) {
            holder.statusChip.setText("DELETED");
            holder.statusChip.setChipBackgroundColorResource(R.color.ef_card_rose);
            holder.statusChip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_card_rose_text));
            return;
        }
        if (profile.isLocked()) {
            holder.statusChip.setText("LOCKED");
            holder.statusChip.setChipBackgroundColorResource(R.color.ef_warning_bg);
            holder.statusChip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_warning_text));
            return;
        }
        holder.statusChip.setText("ACTIVE");
        holder.statusChip.setChipBackgroundColorResource(R.color.ef_primary_container);
        holder.statusChip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary));
    }

    private void bindToggleStatusButton(@NonNull ViewHolder holder, @NonNull CloudUserProfile profile) {
        if (profile.isDeleted()) {
            holder.btnToggleStatus.setText("Kích hoạt lại");
            holder.btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary_container)));
            holder.btnToggleStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary));
            return;
        }
        if (profile.isLocked()) {
            holder.btnToggleStatus.setText("Mở khóa");
            holder.btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary_container)));
            holder.btnToggleStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_primary));
            return;
        }
        holder.btnToggleStatus.setText("Khóa tài khoản");
        holder.btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_card_rose)));
        holder.btnToggleStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.ef_card_rose_text));
    }

    private void showMoreActions(@NonNull View anchor, @NonNull CloudUserProfile profile) {
        PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor, Gravity.END);
        popupMenu.inflate(R.menu.admin_user_actions_menu);

        MenuItem roleItem = popupMenu.getMenu().findItem(R.id.action_admin_user_toggle_role);
        if (roleItem != null) {
            roleItem.setTitle(profile.isAdmin() ? "Hạ quyền User" : "Nâng quyền Admin");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_admin_user_edit_name) {
                listener.onEditDisplayName(profile);
                return true;
            }
            if (itemId == R.id.action_admin_user_toggle_role) {
                listener.onToggleRole(profile);
                return true;
            }
            if (itemId == R.id.action_admin_user_reset_password) {
                listener.onResetPassword(profile);
                return true;
            }
            if (itemId == R.id.action_admin_user_delete) {
                listener.onDeleteUser(profile);
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    @NonNull
    private String formatLastSeen(long lastSeenAt, long now) {
        long diff = Math.max(0L, now - lastSeenAt);
        long minutes = diff / (60L * 1000L);
        if (minutes < 1L) {
            return "Vừa truy cập";
        }
        if (minutes < 60L) {
            return "Truy cập " + minutes + " phút trước";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return "Truy cập " + hours + " giờ trước";
        }
        long days = hours / 24L;
        return "Truy cập " + days + " ngày trước";
    }
}
