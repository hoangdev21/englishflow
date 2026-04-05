package com.example.englishflow.ui.fragments;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.DictionaryRepository;
import com.example.englishflow.data.DictionaryResult;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.data.FirebaseUserStore;
import com.example.englishflow.data.FreeDictionaryService;
import com.example.englishflow.data.MyMemoryService;
import com.example.englishflow.data.TopicItem;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.reminder.AdminNotificationCenter;
import com.example.englishflow.reminder.StudyReminderScheduler;
import com.example.englishflow.ui.IpaActivity;
import com.example.englishflow.ui.LearnedWordsActivity;
import com.example.englishflow.ui.LeaderboardActivity;
import com.example.englishflow.ui.LearnedWordsAdapter;
import com.example.englishflow.ui.NotificationDetailActivity;
import com.example.englishflow.ui.views.LightningProgressBar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Date;

import okhttp3.OkHttpClient;

public class HomeFragment extends Fragment {

    private static final OkHttpClient SHARED_HTTP_CLIENT = new OkHttpClient();
    private static final int REQUEST_NOTIFICATION_PERMISSION = 9001;
    private static final String DEFAULT_DICT_TITLE = "Mở tra từ điển";
    private static final String DEFAULT_DICT_HINT = "Nhập từ tiếng Anh hoặc tiếng Việt để tra IPA, nghĩa, ví dụ và từ đồng nghĩa.";
    private static final String DEFAULT_DICT_EXAMPLE = "Bạn có thể bấm vào từ đồng nghĩa trong kết quả để tra tiếp ngay lập tức.";
    private static final long UI_REFRESH_MIN_INTERVAL_MS = 1500L;
        private static final int USER_NOTIFICATION_LIMIT = 60;
        private static final SimpleDateFormat NOTIFICATION_TIME_FORMAT =
            new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    private AppRepository repository;
        private AppSettingsStore settingsStore;
        private final FirebaseUserStore userStore = new FirebaseUserStore();
    private DictionaryRepository dictionaryRepository;
    private TextView reminderText;
        private TextView notificationBadgeText;
        private View notificationButton;
    private DictionaryResult currentDictionaryResult;
        private FirebaseUserStore.NotificationSubscription userNotificationSubscription;
        private String observedNotificationUid = "";
        private final List<FirebaseUserStore.AdminNotificationEntry> cachedUserNotifications = new ArrayList<>();
        private boolean notificationSnapshotInitialized = false;
    private long lastDashboardRenderAt = 0L;

    private TextToSpeech textToSpeech;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final float TTS_SPEECH_RATE = 0.9f;
    private static final float TTS_PITCH = 1.0f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize repository safely
        try {
            repository = AppRepository.getInstance(requireContext());
            settingsStore = new AppSettingsStore(requireContext());
            dictionaryRepository = new DictionaryRepository(
                    new FreeDictionaryService(SHARED_HTTP_CLIENT),
                    new MyMemoryService(SHARED_HTTP_CLIENT)
            );
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khởi tạo ứng dụng", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            setupBasicViews(view);
            initTextToSpeech();
            observeUserStats();
            refreshData(true);
            AdminNotificationCenter.ensureChannel(requireContext());

            // ══ Window Insets Handling (Safe for All Screen Types) ══
            View headerContent = view.findViewById(R.id.headerContent);
            if (headerContent != null) {
                final int initialLeftPadding = headerContent.getPaddingLeft();
                final int initialTopPadding = headerContent.getPaddingTop();
                final int initialRightPadding = headerContent.getPaddingRight();
                final int initialBottomPadding = headerContent.getPaddingBottom();
                ViewCompat.setOnApplyWindowInsetsListener(headerContent, (v, windowInsets) -> {
                    Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    // Keep the XML base spacing and only add actual status bar inset.
                    v.setPadding(initialLeftPadding, systemBars.top + initialTopPadding, initialRightPadding, initialBottomPadding);
                    return windowInsets;
                });
                ViewCompat.requestApplyInsets(headerContent);
            }

            bindNotificationHeroInsets(view);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi tải dữ liệu trang chủ", Toast.LENGTH_SHORT).show();
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(TTS_SPEECH_RATE);
                textToSpeech.setPitch(TTS_PITCH);
            }
        });
    }

    @Override
    public void onDestroyView() {
        stopUserNotificationObserver();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && repository != null) {
            refreshData(false);
        }
        startUserNotificationObserver();
    }

    private void observeUserStats() {
        if (repository != null) {
            repository.getLiveUserStats().observe(getViewLifecycleOwner(), stats -> {
                if (stats != null && isAdded()) {
                    refreshData(true);
                }
            });
        }
    }

    private void setupBasicViews(View view) {
        try {
            // Premium header defaults
            TextView greetingText = view.findViewById(R.id.txtGreeting);
            if (greetingText != null) greetingText.setText("Xin chào");

            // Default stats
            setDefaultStats(view);
            resetDictionaryCard(view);

            // Set up all interactive buttons
            setupQuickActionButtons(view);
            setupNotificationBell(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupQuickActionButtons(View view) {
        try {
            // Quick action navigation buttons (Now using custom card views)
            View quickLearnBtn = view.findViewById(R.id.btnQuickLearn);
            View quickScanBtn = view.findViewById(R.id.btnQuickScan);
            View quickChatBtn = view.findViewById(R.id.btnQuickChat);

            if (quickLearnBtn != null) quickLearnBtn.setOnClickListener(v -> navigateToTab(1));
            if (quickScanBtn != null) quickScanBtn.setOnClickListener(v -> navigateToTab(2));
            if (quickChatBtn != null) quickChatBtn.setOnClickListener(v -> navigateToTab(3));

            // Continue Learning button
            MaterialButton continueBtn = view.findViewById(R.id.btnContinue);
            if (continueBtn != null) continueBtn.setOnClickListener(v -> navigateToTab(1));

            // Dictionary buttons
            MaterialButton saveDictionaryWordBtn = view.findViewById(R.id.btnSaveDictionaryWord);
            MaterialButton homeDictSearchBtn = view.findViewById(R.id.btnHomeDictSearch);
            EditText homeDictInput = view.findViewById(R.id.homeDictInput);

            View.OnClickListener searchDictionaryAction = v -> performHomeDictionarySearch(view);

            if (homeDictSearchBtn != null) homeDictSearchBtn.setOnClickListener(searchDictionaryAction);
            if (saveDictionaryWordBtn != null) {
                saveDictionaryWordBtn.setOnClickListener(v -> saveCurrentDictionaryWord());
            }

            if (homeDictInput != null) {
                homeDictInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        performHomeDictionarySearch(view);
                        return true;
                    }
                    return false;
                });
            }

            // Set Reminder button
            reminderText = view.findViewById(R.id.txtReminder);
            MaterialButton setReminderBtn = view.findViewById(R.id.btnSetReminder);
            if (setReminderBtn != null && reminderText != null) {
                setReminderBtn.setOnClickListener(v -> {
                    int currentHour = repository.getReminderHour();
                    int currentMinute = repository.getReminderMinute();
                    TimePickerDialog dialog = new TimePickerDialog(requireContext(),
                            (timePicker, selectedHour, selectedMinute) -> {
                                repository.setReminderTime(selectedHour, selectedMinute);
                                StudyReminderScheduler.scheduleDailyReminder(requireContext(), selectedHour, selectedMinute);
                                ensureNotificationPermission();
                                renderReminderText();
                                Toast.makeText(requireContext(), "Đã đặt nhắc học hằng ngày", Toast.LENGTH_SHORT).show();
                            }, currentHour, currentMinute, true);
                    dialog.show();
                });
            }

            // Learned Words & Leaderboard
            View btnIpa = view.findViewById(R.id.btnQuickIpa);
            if (btnIpa != null) {
                btnIpa.setOnClickListener(v1 -> startActivity(new Intent(getActivity(), IpaActivity.class)));
            }

            View learnedWordsCard = view.findViewById(R.id.btnLearnedWords);
            if (learnedWordsCard != null) {
                learnedWordsCard.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), LearnedWordsActivity.class);
                    startActivity(intent);
                });
            }

            View leaderboardCard = view.findViewById(R.id.btnLeaderboard);
            if (leaderboardCard != null) {
                leaderboardCard.setOnClickListener(v -> {
                    Intent intent = new Intent(requireContext(), LeaderboardActivity.class);
                    startActivity(intent);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupNotificationBell(@NonNull View view) {
        notificationButton = view.findViewById(R.id.btnHomeHeroNotification);
        notificationBadgeText = view.findViewById(R.id.txtHomeHeroNotificationBadge);

        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> showNotificationCenterDialog());
        }

        renderNotificationBadge(0);
    }

    private void bindNotificationHeroInsets(@NonNull View rootView) {
        View bell = rootView.findViewById(R.id.btnHomeHeroNotification);
        if (bell == null) {
            return;
        }

        ViewGroup.LayoutParams rawLayoutParams = bell.getLayoutParams();
        if (!(rawLayoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) rawLayoutParams;
        final int baseTopMargin = marginLayoutParams.topMargin;
        final int baseEndMargin = marginLayoutParams.getMarginEnd();

        ViewCompat.setOnApplyWindowInsetsListener(bell, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams params = v.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams updatedParams = (ViewGroup.MarginLayoutParams) params;
                updatedParams.topMargin = baseTopMargin + systemBars.top + dpToPx(4);
                updatedParams.setMarginEnd(baseEndMargin);
                v.setLayoutParams(updatedParams);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(bell);
    }

    private void startUserNotificationObserver() {
        if (!isAdded()) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getUid())) {
            stopUserNotificationObserver();
            cachedUserNotifications.clear();
            renderNotificationBadge(0);
            return;
        }

        String uid = user.getUid();
        if (userNotificationSubscription != null && uid.equals(observedNotificationUid)) {
            return;
        }

        stopUserNotificationObserver();
        observedNotificationUid = uid;
        notificationSnapshotInitialized = false;

        userNotificationSubscription = userStore.observeUserNotifications(uid, USER_NOTIFICATION_LIMIT, entries -> {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> consumeUserNotifications(entries));
        });
    }

    private void stopUserNotificationObserver() {
        if (userNotificationSubscription != null) {
            userNotificationSubscription.remove();
            userNotificationSubscription = null;
        }
        observedNotificationUid = "";
    }

    private void consumeUserNotifications(@NonNull List<FirebaseUserStore.AdminNotificationEntry> entries) {
        cachedUserNotifications.clear();
        cachedUserNotifications.addAll(entries);

        long lastReadAt = settingsStore != null ? settingsStore.getLastAdminNotificationReadAt() : 0L;
        int unreadCount = countUnreadNotifications(entries, lastReadAt);
        renderNotificationBadge(unreadCount);

        if (settingsStore == null || !settingsStore.isAdminNotificationsEnabled() || entries.isEmpty()) {
            notificationSnapshotInitialized = true;
            return;
        }

        long lastAlertedAt = settingsStore.getLastAdminNotificationAlertedAt();
        if (!notificationSnapshotInitialized) {
            notificationSnapshotInitialized = true;
            if (lastAlertedAt <= 0L) {
                settingsStore.setLastAdminNotificationAlertedAt(entries.get(0).createdAt);
            }
            return;
        }

        FirebaseUserStore.AdminNotificationEntry latestForAlert = null;
        for (FirebaseUserStore.AdminNotificationEntry entry : entries) {
            if (entry.createdAt > lastAlertedAt) {
                latestForAlert = entry;
                break;
            }
        }

        if (latestForAlert == null) {
            return;
        }

        ensureNotificationPermission();
        AdminNotificationCenter.notifyAdminMessage(
                requireContext(),
                nonEmptyText(latestForAlert.title, "Thông báo mới từ Admin"),
                nonEmptyText(latestForAlert.message, "Bạn vừa nhận thông báo mới."),
                latestForAlert.createdAt
        );
        settingsStore.setLastAdminNotificationAlertedAt(latestForAlert.createdAt);
        
        // Refresh local data to reflect any stats changes (e.g., XP grant) mentioned in notification
        refreshData(true);
    }

    private void renderNotificationBadge(int unreadCount) {
        if (notificationBadgeText == null) {
            return;
        }

        if (unreadCount <= 0) {
            notificationBadgeText.setVisibility(View.GONE);
            return;
        }

        notificationBadgeText.setVisibility(View.VISIBLE);
        notificationBadgeText.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
    }

    private void showNotificationCenterDialog() {
        if (!isAdded()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialogTheme);
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_home_notifications, null, false);

        RecyclerView recyclerView = content.findViewById(R.id.homeNotificationRecycler);
        TextView emptyText = content.findViewById(R.id.homeNotificationEmpty);
        TextView subtitleText = content.findViewById(R.id.txtHomeNotificationSubtitle);
        MaterialButton closeButton = content.findViewById(R.id.btnHomeNotificationClose);

        List<FirebaseUserStore.AdminNotificationEntry> items = new ArrayList<>(cachedUserNotifications);
        long lastReadAt = settingsStore != null ? settingsStore.getLastAdminNotificationReadAt() : 0L;
        int unreadCount = countUnreadNotifications(items, lastReadAt);

        HomeNotificationAdapter adapter = new HomeNotificationAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        adapter.submit(items, lastReadAt);

        if (subtitleText != null) {
            subtitleText.setText("Mới: " + unreadCount);
        }
        if (emptyText != null) {
            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.setContentView(content);
        dialog.show();

        if (items.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && !TextUtils.isEmpty(currentUser.getUid())) {
                String uid = currentUser.getUid();
                userStore.fetchUserNotifications(uid, USER_NOTIFICATION_LIMIT, fetchedEntries -> {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        if (!dialog.isShowing()) {
                            return;
                        }

                        cachedUserNotifications.clear();
                        cachedUserNotifications.addAll(fetchedEntries);

                        items.clear();
                        items.addAll(fetchedEntries);

                        long latestReadAt = settingsStore != null ? settingsStore.getLastAdminNotificationReadAt() : 0L;
                        adapter.submit(items, latestReadAt);

                        if (emptyText != null) {
                            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                        if (subtitleText != null) {
                            subtitleText.setText("Mới: " + countUnreadNotifications(items, latestReadAt));
                        }

                        markNotificationsAsRead(items);
                        long updatedReadAt = settingsStore != null ? settingsStore.getLastAdminNotificationReadAt() : latestReadAt;
                        adapter.submit(items, updatedReadAt);
                        if (subtitleText != null) {
                            subtitleText.setText("Mới: " + countUnreadNotifications(items, updatedReadAt));
                        }
                    });
                });
            }
        }

        markNotificationsAsRead(items);

        long updatedReadAt = settingsStore != null ? settingsStore.getLastAdminNotificationReadAt() : lastReadAt;
        adapter.submit(items, updatedReadAt);
        if (subtitleText != null) {
            subtitleText.setText("Mới: " + countUnreadNotifications(items, updatedReadAt));
        }
    }

    private void markNotificationsAsRead(@NonNull List<FirebaseUserStore.AdminNotificationEntry> entries) {
        if (settingsStore == null || entries.isEmpty()) {
            return;
        }

        long newestCreatedAt = 0L;
        for (FirebaseUserStore.AdminNotificationEntry entry : entries) {
            newestCreatedAt = Math.max(newestCreatedAt, entry.createdAt);
        }

        if (newestCreatedAt <= 0L) {
            return;
        }

        if (newestCreatedAt > settingsStore.getLastAdminNotificationReadAt()) {
            settingsStore.setLastAdminNotificationReadAt(newestCreatedAt);
            renderNotificationBadge(0);
        }
    }

    private int countUnreadNotifications(
            @NonNull List<FirebaseUserStore.AdminNotificationEntry> entries,
            long lastReadAt
    ) {
        int unread = 0;
        for (FirebaseUserStore.AdminNotificationEntry entry : entries) {
            if (entry.createdAt > lastReadAt) {
                unread++;
            }
        }
        return unread;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String formatNotificationTime(long millis) {
        if (millis <= 0L) {
            return "--";
        }
        if (DateUtils.isToday(millis)) {
            return "Hôm nay " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        }
        return NOTIFICATION_TIME_FORMAT.format(new Date(millis));
    }

    private String nonEmptyText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void openNotificationDetail(@NonNull FirebaseUserStore.AdminNotificationEntry entry) {
        if (!isAdded()) {
            return;
        }

        Intent intent = new Intent(requireContext(), NotificationDetailActivity.class);
        intent.putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION_ID, nonEmptyText(entry.id, ""));
        intent.putExtra(NotificationDetailActivity.EXTRA_TITLE, nonEmptyText(entry.title, "Thông báo từ Admin"));
        intent.putExtra(NotificationDetailActivity.EXTRA_MESSAGE, nonEmptyText(entry.message, "Bạn có thông báo mới."));
        intent.putExtra(NotificationDetailActivity.EXTRA_CREATED_AT, entry.createdAt);
        intent.putExtra(NotificationDetailActivity.EXTRA_CREATED_BY_NAME, nonEmptyText(entry.createdByName, "Admin"));
        intent.putExtra(NotificationDetailActivity.EXTRA_TARGET_TYPE, nonEmptyText(entry.targetType, "all"));
        intent.putExtra(NotificationDetailActivity.EXTRA_TARGET_EMAIL, nonEmptyText(entry.targetEmail, ""));
        startActivity(intent);
    }

    private class HomeNotificationAdapter extends RecyclerView.Adapter<HomeNotificationAdapter.NotificationViewHolder> {
        private final List<FirebaseUserStore.AdminNotificationEntry> items = new ArrayList<>();
        private long readAt;

        void submit(@NonNull List<FirebaseUserStore.AdminNotificationEntry> values, long lastReadAt) {
            items.clear();
            items.addAll(values);
            readAt = Math.max(0L, lastReadAt);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_home_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            FirebaseUserStore.AdminNotificationEntry entry = items.get(position);
            holder.title.setText(nonEmptyText(entry.title, "Thông báo từ Admin"));
            holder.message.setText(nonEmptyText(entry.message, "Bạn có thông báo mới."));
            holder.time.setText(formatNotificationTime(entry.createdAt));

            boolean unread = entry.createdAt > readAt;
            holder.unreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
            int strokeColor = ContextCompat.getColor(
                    requireContext(),
                    unread ? R.color.ef_primary : R.color.ef_outline
            );
            holder.card.setStrokeColor(strokeColor);
                holder.card.setOnClickListener(v -> openNotificationDetail(entry));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class NotificationViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView title;
            final TextView message;
            final TextView time;
            final View unreadDot;

            NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardHomeNotification);
                title = itemView.findViewById(R.id.txtHomeNotificationItemTitle);
                message = itemView.findViewById(R.id.txtHomeNotificationItemMessage);
                time = itemView.findViewById(R.id.txtHomeNotificationItemTime);
                unreadDot = itemView.findViewById(R.id.viewHomeNotificationUnreadDot);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // DICTIONARY SEARCH — FIXED LOGIC
    // ─────────────────────────────────────────────────────────

    private void performHomeDictionarySearch(View rootView) {
        if (dictionaryRepository == null || !isAdded()) {
            return;
        }

        EditText homeDictInput = rootView.findViewById(R.id.homeDictInput);
        ProgressBar homeDictLoading = rootView.findViewById(R.id.homeDictLoading);
        TextView homeDictError = rootView.findViewById(R.id.homeDictError);

        String query = homeDictInput != null ? String.valueOf(homeDictInput.getText()).trim() : "";
        if (TextUtils.isEmpty(query)) {
            resetDictionaryCard(rootView);
            if (homeDictError != null) {
                homeDictError.setText("Vui lòng nhập từ cần tra");
                homeDictError.setVisibility(View.VISIBLE);
            }
            return;
        }

        String validationError = getLookupValidationError(query);
        if (validationError != null) {
            resetDictionaryCard(rootView);
            if (homeDictError != null) {
                homeDictError.setText(validationError);
                homeDictError.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Show loading and hide old results/errors
        if (homeDictError != null) homeDictError.setVisibility(View.GONE);
        if (homeDictLoading != null) homeDictLoading.setVisibility(View.VISIBLE);

        dictionaryRepository.search(query, new DictionaryRepository.SearchCallback() {
            @Override
            public void onSuccess(DictionaryResult result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    bindHomeDictionaryResult(rootView, result);
                    if (homeDictLoading != null) homeDictLoading.setVisibility(View.GONE);
                    if (homeDictError != null) homeDictError.setVisibility(View.GONE);
                });
            }

            @Override
            public void onNotFound(String missingQuery) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (homeDictLoading != null) homeDictLoading.setVisibility(View.GONE);
                    resetDictionaryCard(rootView);
                    if (homeDictError != null) {
                        homeDictError.setText("Không tìm thấy từ: " + missingQuery);
                        homeDictError.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (homeDictLoading != null) homeDictLoading.setVisibility(View.GONE);
                    resetDictionaryCard(rootView);
                    if (homeDictError != null) {
                        homeDictError.setText(message);
                        homeDictError.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void bindHomeDictionaryResult(View rootView, DictionaryResult result) {
        if (result == null) return;
        currentDictionaryResult = result;

        TextView dictTitle = rootView.findViewById(R.id.txtDictionaryTitle);
        TextView dictIpa = rootView.findViewById(R.id.txtDictIpa);
        TextView dictPartOfSpeech = rootView.findViewById(R.id.txtDictPartOfSpeech);
        TextView dictHint = rootView.findViewById(R.id.txtDictionaryHint);
        TextView dictExample = rootView.findViewById(R.id.txtDictionaryExample);
        LinearLayout dictExampleContainer = rootView.findViewById(R.id.dictExampleContainer);
        LinearLayout dictSynonymsContainer = rootView.findViewById(R.id.dictSynonymsContainer);
        TextView dictSynonyms = rootView.findViewById(R.id.txtDictSynonyms);

        // Word + Translation
        if (dictTitle != null) {
            String queryWord = result.getQueryWord();
            String englishWord = result.getWord();
            String translatedWord = result.getTranslatedWord();
            
            if (result.isVietnameseSearch()) {
                // Search "Mèo" -> Mèo (Cat)
                dictTitle.setText(capitalize(queryWord) + " (" + capitalize(englishWord) + ")");
            } else {
                // Search "Cat" -> Cat (con mèo)
                String displayWord = capitalize(englishWord);
                if (translatedWord != null && !translatedWord.isEmpty()) {
                    dictTitle.setText(displayWord + " (" + translatedWord + ")");
                } else {
                    dictTitle.setText(displayWord);
                }
            }
        }

        View btnDictPronounce = rootView.findViewById(R.id.btnDictPronounce);
        if (btnDictPronounce != null) {
            btnDictPronounce.setVisibility(View.VISIBLE);
            btnDictPronounce.setOnClickListener(v -> {
                if (result != null && textToSpeech != null) {
                    textToSpeech.speak(result.getWord(), TextToSpeech.QUEUE_FLUSH, null, "home-word");
                }
            });
        }

        // IPA
        String ipa = safeText(result.getIpa(), "");
        if (dictIpa != null) {
            if (!ipa.isEmpty()) {
                dictIpa.setText(ipa);
                dictIpa.setVisibility(View.VISIBLE);
            } else {
                dictIpa.setVisibility(View.GONE);
            }
        }

        // First definition
        DictionaryResult.Definition firstDefinition = null;
        if (result.getDefinitions() != null && !result.getDefinitions().isEmpty()) {
            firstDefinition = result.getDefinitions().get(0);
        }

        String meaning = firstDefinition != null ? safeText(firstDefinition.getMeaning(), "") : "";
        String partOfSpeech = firstDefinition != null ? safeText(firstDefinition.getPartOfSpeech(), "") : "";
        String example = firstDefinition != null ? safeText(firstDefinition.getExample(), "") : "";

        // Part of speech tag
        if (dictPartOfSpeech != null) {
            if (!partOfSpeech.isEmpty()) {
                dictPartOfSpeech.setText(partOfSpeech);
                dictPartOfSpeech.setVisibility(View.VISIBLE);
            } else {
                dictPartOfSpeech.setVisibility(View.GONE);
            }
        }

        // Meaning
        if (dictHint != null) {
            String primaryMeaning = firstDefinition != null ? safeText(firstDefinition.getTranslatedMeaning(), "") : "";
            if (primaryMeaning.isEmpty() && firstDefinition != null) {
                primaryMeaning = safeText(firstDefinition.getMeaning(), "");
            }
            
            if (!primaryMeaning.isEmpty()) {
                dictHint.setText(primaryMeaning);
            } else {
                dictHint.setText("Đã tra xong, nhưng chưa có định nghĩa phù hợp.");
            }
        }

        // Usage Note
        TextView dictUsageNote = rootView.findViewById(R.id.txtDictUsageNote);
        LinearLayout dictUsageContainer = rootView.findViewById(R.id.dictUsageContainer);
        if (dictUsageNote != null && dictUsageContainer != null) {
            String usage = firstDefinition != null ? safeText(firstDefinition.getUsageNote(), "") : "";
            if (!usage.isEmpty()) {
                dictUsageNote.setText(usage);
                dictUsageContainer.setVisibility(View.VISIBLE);
            } else {
                dictUsageContainer.setVisibility(View.GONE);
            }
        }

        // Example
        if (dictExample != null && dictExampleContainer != null) {
            if (!example.isEmpty()) {
                dictExample.setText(example);
                dictExampleContainer.setVisibility(View.VISIBLE);
            } else if (!meaning.isEmpty()) {
                dictExample.setText(meaning);
                dictExampleContainer.setVisibility(View.VISIBLE);
            } else {
                dictExampleContainer.setVisibility(View.VISIBLE);
                dictExample.setText("Chưa có ví dụ cho từ này.");
            }
        }

        // Synonyms
        List<String> synonyms = result.getSynonyms();
        if (dictSynonymsContainer != null && dictSynonyms != null) {
            if (synonyms != null && !synonyms.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < synonyms.size(); i++) {
                    if (i > 0) sb.append(" • ");
                    sb.append(synonyms.get(i));
                }
                dictSynonyms.setText(sb.toString());
                dictSynonymsContainer.setVisibility(View.VISIBLE);

                // Make synonyms clickable — tap to search
                dictSynonyms.setOnClickListener(v -> {
                    if (synonyms.size() > 0) {
                        EditText homeDictInput = rootView.findViewById(R.id.homeDictInput);
                        if (homeDictInput != null) {
                            homeDictInput.setText(synonyms.get(0));
                            performHomeDictionarySearch(rootView);
                        }
                    }
                });
            } else {
                dictSynonymsContainer.setVisibility(View.GONE);
            }
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return "";
        return word.substring(0, 1).toUpperCase(Locale.US) + word.substring(1);
    }

    private String getLookupValidationError(String query) {
        if (query == null) {
            return "Từ không hợp lệ.";
        }

        String normalized = query.trim();
        if (normalized.length() < 1 || normalized.length() > 60) {
            return "Từ cần có độ dài từ 1 đến 60 ký tự.";
        }

        // Allow letters from any language (including Vietnamese), spaces, apostrophes and hyphens.
        if (!normalized.matches("^[\\p{L}\\s'-]+$")) {
            return "Từ không hợp lệ. Chỉ nhập chữ cái, khoảng trắng, dấu - hoặc '.";
        }

        // Ensure there is at least one letter.
        if (!normalized.matches(".*\\p{L}.*")) {
            return "Từ không hợp lệ.";
        }

        return null;
    }

    private void resetDictionaryCard(View rootView) {
        currentDictionaryResult = null;
        TextView dictTitle = rootView.findViewById(R.id.txtDictionaryTitle);
        TextView dictIpa = rootView.findViewById(R.id.txtDictIpa);
        TextView dictPartOfSpeech = rootView.findViewById(R.id.txtDictPartOfSpeech);
        TextView dictHint = rootView.findViewById(R.id.txtDictionaryHint);
        TextView dictExample = rootView.findViewById(R.id.txtDictionaryExample);
        LinearLayout dictExampleContainer = rootView.findViewById(R.id.dictExampleContainer);
        LinearLayout dictSynonymsContainer = rootView.findViewById(R.id.dictSynonymsContainer);
        LinearLayout dictUsageContainer = rootView.findViewById(R.id.dictUsageContainer);
        View btnDictPronounce = rootView.findViewById(R.id.btnDictPronounce);

        if (dictTitle != null) dictTitle.setText(DEFAULT_DICT_TITLE);
        if (dictIpa != null) dictIpa.setVisibility(View.GONE);
        if (btnDictPronounce != null) btnDictPronounce.setVisibility(View.GONE);
        if (dictPartOfSpeech != null) dictPartOfSpeech.setVisibility(View.GONE);
        if (dictHint != null) dictHint.setText(DEFAULT_DICT_HINT);
        if (dictExample != null) dictExample.setText(DEFAULT_DICT_EXAMPLE);
        if (dictExampleContainer != null) dictExampleContainer.setVisibility(View.VISIBLE);
        if (dictSynonymsContainer != null) dictSynonymsContainer.setVisibility(View.GONE);
        if (dictUsageContainer != null) dictUsageContainer.setVisibility(View.GONE);
    }

    private void saveCurrentDictionaryWord() {
        if (repository == null || currentDictionaryResult == null) {
            Toast.makeText(requireContext(), "Hãy tra từ trước khi lưu", Toast.LENGTH_SHORT).show();
            return;
        }

        String word = safeText(currentDictionaryResult.getWord(), "");
        if (word.isEmpty()) {
            Toast.makeText(requireContext(), "Không có từ để lưu", Toast.LENGTH_SHORT).show();
            return;
        }

        DictionaryResult.Definition firstDefinition = null;
        if (currentDictionaryResult.getDefinitions() != null && !currentDictionaryResult.getDefinitions().isEmpty()) {
            firstDefinition = currentDictionaryResult.getDefinitions().get(0);
        }

        String ipa = safeText(currentDictionaryResult.getIpa(), "-");
        String wordType = firstDefinition != null ? safeText(firstDefinition.getPartOfSpeech(), "noun") : "noun";
        String meaning = firstDefinition != null
                ? safeText(firstDefinition.getTranslatedMeaning(), safeText(firstDefinition.getMeaning(), ""))
                : safeText(currentDictionaryResult.getTranslatedWord(), "");
        String example = firstDefinition != null ? safeText(firstDefinition.getExample(), "") : "";

        repository.saveWord(new WordEntry(
                word,
                ipa,
                meaning,
                wordType,
                example,
                "",
                "",
                "Từ điển",
                "Lưu từ trang chủ"
        ));
        Toast.makeText(requireContext(), "Đã lưu từ: " + word, Toast.LENGTH_SHORT).show();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isAdded()) {
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION && isAdded()) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(requireContext(), "Bạn cần bật quyền thông báo để nhận nhắc học và thông báo từ Admin", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // DEFAULT STATS
    // ─────────────────────────────────────────────────────────

    private void setDefaultStats(View view) {
        try {
            TextView learnedCountText = view.findViewById(R.id.txtLearnedCount);
            TextView bestStreakText = view.findViewById(R.id.txtBestStreakCount);
            TextView currentStreakText = view.findViewById(R.id.txtCurrentStreak);
            TextView currentStreakCardText = view.findViewById(R.id.txtCurrentStreakCard);
            TextView scannedCountText = view.findViewById(R.id.txtScannedCount);
            TextView xpText = view.findViewById(R.id.txtXp);
            TextView xpPercentageText = view.findViewById(R.id.txtXpPercentage);
            TextView headerXpText = view.findViewById(R.id.txtHeaderXp);
            TextView weeklyTotalText = view.findViewById(R.id.txtWeeklyTotal);
            TextView unlearnedCountText = view.findViewById(R.id.txtUnlearnedCount);
            TextView cefrLevelText = view.findViewById(R.id.txtCefrLevel);

            if (learnedCountText != null) learnedCountText.setText("0");
            if (bestStreakText != null) bestStreakText.setText("0");
            if (currentStreakText != null) currentStreakText.setText("0");
            if (currentStreakCardText != null) currentStreakCardText.setText("0");
            if (scannedCountText != null) scannedCountText.setText("0");
            if (xpText != null) xpText.setText("XP hôm nay: 0 điểm");
            if (xpPercentageText != null) xpPercentageText.setText("0%");
            if (headerXpText != null) headerXpText.setText("0");
            if (weeklyTotalText != null) weeklyTotalText.setText("0 phút");
            if (unlearnedCountText != null) unlearnedCountText.setText("3000 từ");
            if (cefrLevelText != null) cefrLevelText.setText("A1 - Sơ cấp");

            // Reset Weekly Icons
            int[] streakIconIds = {R.id.streakMon, R.id.streakTue, R.id.streakWed,
                    R.id.streakThu, R.id.streakFri, R.id.streakSat, R.id.streakSun};
            int inactiveColor = ContextCompat.getColor(requireContext(), R.color.ef_text_tertiary);
            for (int id : streakIconIds) {
                ImageView icon = view.findViewById(id);
                if (icon != null) icon.setImageTintList(ColorStateList.valueOf(inactiveColor));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────
    // ASYNC DATA LOADING
    // ─────────────────────────────────────────────────────────

    private void refreshData(boolean forceRefresh) {
        if (!isAdded() || repository == null) return;

        if (forceRefresh) {
            repository.syncCurrentUserProgressToCloud();
        }

        long now = SystemClock.elapsedRealtime();
        if (!forceRefresh && now - lastDashboardRenderAt < UI_REFRESH_MIN_INTERVAL_MS) {
            return;
        }
        
        repository.getDashboardSnapshotAsync(snapshot -> {
            if (!isAdded()) return;
            
            View view = getView();
            if (view == null) return;

            lastDashboardRenderAt = SystemClock.elapsedRealtime();

            com.example.englishflow.data.UserProgress progress = snapshot.userProgress;
            
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            
            String emoji = getTimeEmoji(hour);
            String greeting = buildGreeting(snapshot.userName);
            int xpGoal = snapshot.xpGoal;
            List<Integer> weeklyMinutes = snapshot.weeklyStudyMinutes;
            int weeklyTotal = snapshot.totalWeeklyMinutes;
            
            updateUIWithData(view, emoji, greeting,
                    hour, minute, progress.totalWordsLearned, progress.currentStreak, 
                    progress.bestStreak, progress.totalWordsScanned, progress.xpTodayEarned, 
                    xpGoal, weeklyTotal, weeklyMinutes, progress.cefrLevel, 
                snapshot.unlearnedWordsCount,
                snapshot.lastTopicTitle,
                snapshot.lastTopicDomain,
                snapshot.lastTopicRemainingCount);
        });
    }

    private void updateUIWithData(View view, String emoji, String greeting, int hour, int minute,
                                   int learnedWords, int streak, int bestStreak, int scanned,
                                   int xpToday, int xpGoal, int weeklyTotal,
                                   List<Integer> weeklyMinutes, String cefrLevel, int unlearnedCount,
                                   String lastTopicTitle, String lastTopicDomain, int lastTopicRemaining) {
        try {
            // Header
            TextView greetingText = view.findViewById(R.id.txtGreeting);
            if (greetingText != null) greetingText.setText(greeting);

            // Stat Cards
            TextView learnedCountText = view.findViewById(R.id.txtLearnedCount);
            TextView bestStreakText = view.findViewById(R.id.txtBestStreakCount);
            TextView currentStreakText = view.findViewById(R.id.txtCurrentStreak);
            TextView currentStreakCardText = view.findViewById(R.id.txtCurrentStreakCard);
            TextView scannedCountText = view.findViewById(R.id.txtScannedCount);

            if (learnedCountText != null) learnedCountText.setText(String.valueOf(learnedWords));
            if (bestStreakText != null) bestStreakText.setText(String.valueOf(bestStreak));
            if (currentStreakText != null) currentStreakText.setText(String.valueOf(streak));
            if (currentStreakCardText != null) currentStreakCardText.setText(String.valueOf(streak));
            if (scannedCountText != null) scannedCountText.setText(String.valueOf(scanned));

            // XP Progress
            TextView xpText = view.findViewById(R.id.txtXp);
            TextView xpPercentageText = view.findViewById(R.id.txtXpPercentage);
            TextView headerXpText = view.findViewById(R.id.txtHeaderXp);
            LightningProgressBar xpProgress = view.findViewById(R.id.xpProgress);

            if (xpText != null) xpText.setText("XP hôm nay: " + xpToday + " điểm");
            int xpPercent = xpGoal > 0 ? (int) (((float) xpToday / (float) xpGoal) * 100f) : 0;
            if (xpPercentageText != null) xpPercentageText.setText(Math.min(xpPercent, 100) + "%");
            if (headerXpText != null) headerXpText.setText(String.valueOf(xpToday));
            if (xpProgress != null) xpProgress.setProgress(Math.min(xpPercent, 100));

            // Weekly Total
            TextView weeklyTotalText = view.findViewById(R.id.txtWeeklyTotal);
            if (weeklyTotalText != null) {
                if (weeklyTotal >= 60) {
                    int hours = weeklyTotal / 60;
                    int mins = weeklyTotal % 60;
                    if (mins > 0) {
                        weeklyTotalText.setText(hours + " giờ " + mins + " phút");
                    } else {
                        weeklyTotalText.setText(hours + " giờ");
                    }
                } else {
                    weeklyTotalText.setText(weeklyTotal + " phút");
                }
            }

            // Weekly Chart — update each day's bar dynamically
            updateWeeklyChart(view, weeklyMinutes);

            // CEFR Level
            TextView cefrLevelText = view.findViewById(R.id.txtCefrLevel);
            if (cefrLevelText != null)
                cefrLevelText.setText(cefrLevel + " - " + cefrLevelToFullName(cefrLevel));

            // Progress to next level
            TextView progressToNext = view.findViewById(R.id.txtProgressToNext);
            if (progressToNext != null) {
                int wordsToNext = calculateProgressToNextLevel(cefrLevel, learnedWords);
                String nextLevel = getNextLevel(cefrLevel);
                if (wordsToNext > 0) {
                    progressToNext.setText("Còn " + wordsToNext + " từ → " + nextLevel);
                } else {
                    progressToNext.setText("Trình độ CEFR");
                }
            }

            // Unlearned count
            TextView unlearnedCountText = view.findViewById(R.id.txtUnlearnedCount);
            if (unlearnedCountText != null) unlearnedCountText.setText(unlearnedCount + " từ");

            // Continue Learning
            TextView continueTitle = view.findViewById(R.id.txtContinueTitle);
            TextView continueProgress = view.findViewById(R.id.txtContinueProgress);
            View continueBtn = view.findViewById(R.id.btnContinue);
            if (continueTitle != null) continueTitle.setText(lastTopicTitle);
            if (continueProgress != null) continueProgress.setText(lastTopicRemaining + " thẻ còn lại");
            if (continueBtn != null) {
                continueBtn.setOnClickListener(v -> {
                    repository.setPendingTopicRequest(lastTopicDomain, lastTopicTitle);
                    navigateToTab(1);
                });
            }

            // Reminder
            renderReminderText();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateWeeklyChart(View view, List<Integer> weeklyMinutes) {
        if (weeklyMinutes == null || weeklyMinutes.size() < 7 || !isAdded()) return;

        int[] streakIconIds = {R.id.streakMon, R.id.streakTue, R.id.streakWed,
                R.id.streakThu, R.id.streakFri, R.id.streakSat, R.id.streakSun};
        int[] streakValIds = {R.id.streakMonVal, R.id.streakTueVal, R.id.streakWedVal,
                R.id.streakThuVal, R.id.streakFriVal, R.id.streakSatVal, R.id.streakSunVal};

        int activeColor = ContextCompat.getColor(requireContext(), R.color.ef_stat_streak);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.ef_text_tertiary);

        for (int i = 0; i < 7; i++) {
            ImageView icon = view.findViewById(streakIconIds[i]);
            TextView valText = view.findViewById(streakValIds[i]);
            int minutes = weeklyMinutes.get(i);

            if (icon != null) {
                icon.setImageTintList(ColorStateList.valueOf(minutes > 0 ? activeColor : inactiveColor));
            }
            if (valText != null) {
                if (minutes >= 60) {
                    valText.setText((minutes / 60) + "h" + (minutes % 60) + "'");
                } else {
                    valText.setText(minutes + "'");
                }
                valText.setTextColor(minutes > 0 ? activeColor : inactiveColor);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────

    private String buildGreeting(String name) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Chào buổi sáng";
        } else if (hour < 18) {
            greeting = "Chào buổi chiều";
        } else {
            greeting = "Chào buổi tối";
        }
        return greeting + ", " + name + "!";
    }

    private String getTimeEmoji(int hour) {
        if (hour < 6) return "🌙";
        if (hour < 12) return "🌅";
        if (hour < 18) return "☀️";
        return "🌙";
    }

    private void renderReminderText() {
        try {
            if (reminderText != null) {
                reminderText.setText(String.format(Locale.getDefault(), "%02d:%02d",
                        repository.getReminderHour(), repository.getReminderMinute()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateToTab(int tabIndex) {
        if (getActivity() != null) {
            ViewPager2 viewPager = getActivity().findViewById(R.id.viewPager);
            if (viewPager != null) {
                viewPager.setCurrentItem(tabIndex, true);
            }
        }
    }

    private String cefrLevelToFullName(String level) {
        switch (level) {
            case "A1": return "Sơ cấp 1";
            case "A2": return "Sơ cấp 2";
            case "B1": return "Trung cấp";
            case "B2": return "Trung cấp +";
            case "C1": return "Cao cấp";
            case "C2": return "Thạo nhất";
            default: return "Chưa xác định";
        }
    }

    private String getNextLevel(String currentLevel) {
        switch (currentLevel) {
            case "A1": return "A2";
            case "A2": return "B1";
            case "B1": return "B2";
            case "B2": return "C1";
            case "C1": return "C2";
            default: return "C2";
        }
    }

    private int calculateProgressToNextLevel(String level, int learnedWords) {
        switch (level) {
            case "A1": return Math.max(0, 80 - learnedWords);
            case "A2": return Math.max(0, 160 - learnedWords);
            case "B1": return Math.max(0, 260 - learnedWords);
            case "B2": return Math.max(0, 380 - learnedWords);
            case "C1": return Math.max(0, 520 - learnedWords);
            default: return 0;
        }
    }
}
