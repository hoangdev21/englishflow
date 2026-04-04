package com.example.englishflow.admin.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.CloudUserProfile;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminOverviewFragment extends Fragment {

    private final FirebaseUserStore userStore = new FirebaseUserStore();
    private AppRepository repository;

    private TextView statTotalUsers;
    private TextView statDau;
    private TextView statTotalScore;
    private TextView statTotalVocabulary;

    private TextInputEditText notifyTitleInput;
    private TextInputEditText notifyMessageInput;
    private TextInputEditText notifyTargetInput;
    private MaterialButton btnSendNotification;

    private RecyclerView feedRecycler;
    private View feedEmpty;
    private FeedAdapter feedAdapter;

    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#,###");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            repository = AppRepository.getInstance(requireContext());
        } catch (Exception e) {
            repository = null;
        }

        statTotalUsers = view.findViewById(R.id.adminStatTotalUsers);
        statDau = view.findViewById(R.id.adminStatDau);
        statTotalScore = view.findViewById(R.id.adminStatTotalScore);
        statTotalVocabulary = view.findViewById(R.id.adminStatTotalVocabulary);

        notifyTitleInput = view.findViewById(R.id.adminNotifyTitleInput);
        notifyMessageInput = view.findViewById(R.id.adminNotifyMessageInput);
        notifyTargetInput = view.findViewById(R.id.adminNotifyTargetInput);
        btnSendNotification = view.findViewById(R.id.adminBtnSendNotification);

        feedRecycler = view.findViewById(R.id.adminNotificationFeedRecycler);
        feedEmpty = view.findViewById(R.id.adminNotificationFeedEmpty);

        setupFeedRecycler();
        setupSendAction();
        refreshData();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    private void setupFeedRecycler() {
        if (feedRecycler == null) {
            return;
        }

        feedAdapter = new FeedAdapter();
        feedRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        feedRecycler.setNestedScrollingEnabled(false);
        feedRecycler.setAdapter(feedAdapter);
    }

    private void setupSendAction() {
        if (btnSendNotification == null) {
            return;
        }

        btnSendNotification.setOnClickListener(v -> {
            String title = notifyTitleInput != null && notifyTitleInput.getText() != null
                    ? notifyTitleInput.getText().toString().trim()
                    : "";
            String message = notifyMessageInput != null && notifyMessageInput.getText() != null
                    ? notifyMessageInput.getText().toString().trim()
                    : "";
            String targetEmail = notifyTargetInput != null && notifyTargetInput.getText() != null
                    ? notifyTargetInput.getText().toString().trim()
                    : "";

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(message)) {
                Toast.makeText(requireContext(), "Vui lòng nhập tiêu đề và nội dung", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Phiên đăng nhập đã hết", Toast.LENGTH_SHORT).show();
                return;
            }

            setSendingState(true);
            String adminName = currentUser.getDisplayName();
            if (TextUtils.isEmpty(adminName)) {
                adminName = currentUser.getEmail();
            }

            userStore.sendAdminNotification(
                    currentUser.getUid(),
                    TextUtils.isEmpty(adminName) ? "Admin" : adminName,
                    title,
                    message,
                    targetEmail,
                    (success, msg) -> {
                        if (!isAdded()) {
                            return;
                        }
                        setSendingState(false);
                        if (success) {
                            Toast.makeText(requireContext(), "Đã gửi thông báo thành công", Toast.LENGTH_SHORT).show();
                            if (notifyTitleInput != null) notifyTitleInput.setText("");
                            if (notifyMessageInput != null) notifyMessageInput.setText("");
                            loadAnnouncementFeed();
                        } else {
                            Toast.makeText(requireContext(), humanizeNotificationError(msg), Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
    }

    private String humanizeNotificationError(@Nullable String rawMessage) {
        if (TextUtils.isEmpty(rawMessage)) {
            return "Không thể gửi thông báo";
        }

        String message = rawMessage.trim();
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("permission_denied") || lower.contains("missing or insufficient permissions")) {
            return "Bạn chưa có quyền gửi thông báo. Hãy kiểm tra role admin trong users/{uid} và publish lại firestore.rules.";
        }
        return message;
    }

    private void setSendingState(boolean sending) {
        if (btnSendNotification == null) {
            return;
        }
        btnSendNotification.setEnabled(!sending);
        btnSendNotification.setText(sending ? "Đang gửi..." : "Gửi thông báo ngay");
    }

    private void refreshData() {
        userStore.fetchAdminStats(stats -> {
            if (!isAdded()) return;
            statTotalUsers.setText(String.valueOf(stats.totalUsers));
            statDau.setText(String.valueOf(stats.dailyActiveUsers));
            statTotalScore.setText(SCORE_FORMAT.format(stats.totalDailyXp));
        });

        if (repository != null) {
            repository.getSystemVocabularyCountAsync(totalVocabulary -> {
                if (!isAdded()) {
                    return;
                }
                statTotalVocabulary.setText(SCORE_FORMAT.format(totalVocabulary));
            });
        } else {
            statTotalVocabulary.setText("0");
        }

        loadAnnouncementFeed();
    }

    private void loadAnnouncementFeed() {
        userStore.fetchUsers(users -> {
            if (!isAdded()) {
                return;
            }

            userStore.fetchRecentAdminNotifications(40, notifications -> {
                if (!isAdded()) {
                    return;
                }

                List<FeedItem> items = new ArrayList<>();

                for (CloudUserProfile user : users) {
                    if (user == null || user.createdAt <= 0L) {
                        continue;
                    }
                    FeedItem item = new FeedItem();
                    item.iconRes = R.drawable.tong_user;
                    item.title = safeName(user.displayName, user.email) + " vừa đăng ký mới";
                    item.subtitle = TextUtils.isEmpty(user.email) ? "Tài khoản mới" : user.email;
                    item.timestamp = user.createdAt;
                    items.add(item);
                }

                for (FirebaseUserStore.AdminNotificationEntry entry : notifications) {
                    FeedItem item = new FeedItem();
                    item.iconRes = R.drawable.ic_notification_bell;
                    item.title = "Thông báo: " + entry.title;
                    String targetText = "all".equalsIgnoreCase(entry.targetType)
                            ? "Gửi toàn bộ người dùng"
                            : "Gửi tới " + nonEmpty(entry.targetEmail, "1 người dùng");
                    item.subtitle = nonEmpty(entry.createdByName, "Admin") + " • " + targetText + "\n" + entry.message;
                    item.timestamp = entry.createdAt;
                    items.add(item);
                }

                Collections.sort(items, (left, right) -> Long.compare(right.timestamp, left.timestamp));
                if (items.size() > 40) {
                    items = new ArrayList<>(items.subList(0, 40));
                }

                if (feedAdapter != null) {
                    feedAdapter.update(items);
                }
                if (feedEmpty != null) {
                    feedEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    private String safeName(String displayName, String email) {
        if (!TextUtils.isEmpty(displayName)) return displayName;
        if (TextUtils.isEmpty(email)) return "Unknown";
        int atIndex = email.indexOf('@');
        if (atIndex > 0) return email.substring(0, atIndex);
        return email;
    }

    private String formatTime(long millis) {
        if (millis <= 0L) return "--";
        return TIME_FORMAT.format(new Date(millis));
    }

    private String nonEmpty(String value, String fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        return value;
    }

    private static class FeedItem {
        int iconRes;
        String title = "";
        String subtitle = "";
        long timestamp;
    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.FeedViewHolder> {
        private final List<FeedItem> items = new ArrayList<>();

        void update(@NonNull List<FeedItem> values) {
            items.clear();
            items.addAll(values);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_overview_feed, parent, false);
            return new FeedViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
            FeedItem item = items.get(position);
            holder.icon.setImageResource(item.iconRes);
            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            holder.time.setText(formatTime(item.timestamp));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class FeedViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView time;

            FeedViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.adminFeedIcon);
                title = itemView.findViewById(R.id.adminFeedTitle);
                subtitle = itemView.findViewById(R.id.adminFeedSubtitle);
                time = itemView.findViewById(R.id.adminFeedTime);
            }
        }
    }
}
