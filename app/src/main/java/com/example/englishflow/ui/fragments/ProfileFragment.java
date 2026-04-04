package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

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
import com.example.englishflow.data.LocalAuthStore;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.ui.SettingsActivity;
import com.example.englishflow.ui.adapters.AchievementAdapter;
import com.example.englishflow.ui.adapters.DictionaryAdapter;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class ProfileFragment extends Fragment {

    private static final float TTS_SPEECH_RATE = 0.9f;
    private static final float TTS_PITCH = 1.0f;

    private AppRepository repository;
    private LocalAuthStore localAuthStore;
    private AppSettingsStore settingsStore;
    private TextToSpeech textToSpeech;

    private TextView nameText;
    private TextView levelText;
    private TextView levelBadgeText;
    private TextView tvDetailLearned, tvDetailScanned, tvDetailChats, tvDetailBestStreak;
    private TextView tvLearnedCount, tvStreakCount, tvXpCount;
    private ShapeableImageView profileAvatar;
    private LinearLayout chartContainer;
    private View weeklyEmptyState;
    private AchievementAdapter achievementAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        localAuthStore = new LocalAuthStore(requireContext());
        settingsStore = new AppSettingsStore(requireContext());

        nameText = view.findViewById(R.id.profileName);
        levelText = view.findViewById(R.id.profileLevel);
        levelBadgeText = view.findViewById(R.id.profileLevelBadgeText);
        profileAvatar = view.findViewById(R.id.profileAvatar);
        
        tvDetailLearned = view.findViewById(R.id.tvDetailLearned);
        tvDetailScanned = view.findViewById(R.id.tvDetailScanned);
        tvDetailChats = view.findViewById(R.id.tvDetailChats);
        tvDetailBestStreak = view.findViewById(R.id.tvDetailBestStreak);
        
        tvLearnedCount = view.findViewById(R.id.tvLearnedCount);
        tvStreakCount = view.findViewById(R.id.tvStreakCount);
        tvXpCount = view.findViewById(R.id.tvXpCount);
        chartContainer = view.findViewById(R.id.chartContainer);
        weeklyEmptyState = view.findViewById(R.id.profileWeeklyEmptyState);

        View btnOpenSettings = view.findViewById(R.id.btnOpenSettings);
        MaterialButton btnReset = view.findViewById(R.id.btnResetProgress);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        MaterialButton btnViewDictionary = view.findViewById(R.id.btnViewSavedDictionary);
        MaterialButton btnStartLearningWeek = view.findViewById(R.id.btnStartLearningWeek);

        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(TTS_SPEECH_RATE);
                textToSpeech.setPitch(TTS_PITCH);
            }
        });

        RecyclerView achievementRecycler = view.findViewById(R.id.achievementRecycler);
        achievementRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        achievementAdapter = new AchievementAdapter(new ArrayList<>());
        achievementRecycler.setAdapter(achievementAdapter);

        renderProfile();
        
        btnViewDictionary.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new android.content.Intent(requireContext(), com.example.englishflow.ui.LearnedWordsActivity.class));
            }
        });

        if (btnStartLearningWeek != null) {
            btnStartLearningWeek.setOnClickListener(v -> navigateToTab(1));
        }

        btnOpenSettings.setOnClickListener(v -> {
            if (isAdded()) {
                android.content.Intent intent = new android.content.Intent(requireContext(), SettingsActivity.class);
                try {
                    if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(requireContext(), "Không tìm thấy màn hình cài đặt", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Không thể mở cài đặt lúc này", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnReset.setOnClickListener(v -> {
            repository.resetProgress();
            renderProfile();
            Toast.makeText(requireContext(), "Đã reset toàn bộ tiến độ", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> {
            localAuthStore.logout();
            if (getActivity() != null) {
                startActivity(new android.content.Intent(requireContext(), com.example.englishflow.LoginActivity.class));
                getActivity().finish();
            }
        });

        View headerContent = view.findViewById(R.id.profileHeaderContent);
        if (headerContent != null) {
            final int initialLeftPadding = headerContent.getPaddingLeft();
            final int initialTopPadding = headerContent.getPaddingTop();
            final int initialRightPadding = headerContent.getPaddingRight();
            final int initialBottomPadding = headerContent.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(headerContent, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(initialLeftPadding, systemBars.top + initialTopPadding, initialRightPadding, initialBottomPadding);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(headerContent);
        }

        View scrollView = view.findViewById(R.id.profileScrollView);
        if (scrollView != null) {
            final int initialBottomPadding = scrollView.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottomPadding + systemBars.bottom);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(scrollView);
        }
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            renderProfile();
        }
    }

    private void renderProfile() {
        repository.getDashboardSnapshotAsync(snapshot -> {
            if (!isAdded()) {
                return;
            }

            nameText.setText(snapshot.userName);
            levelText.setText(snapshot.userProgress.totalWordsLearned + " từ đã học");
            if (levelBadgeText != null) {
                levelBadgeText.setText(snapshot.userProgress.cefrLevel);
            }
            if (profileAvatar != null && isAdded()) {
                Glide.with(requireContext())
                     .load(settingsStore.getAvatarResId())
                     .into(profileAvatar);
            }

            tvLearnedCount.setText(String.valueOf(snapshot.userProgress.totalWordsLearned));
            tvStreakCount.setText(String.valueOf(snapshot.userProgress.currentStreak));
            tvXpCount.setText(String.valueOf(snapshot.userProgress.xpTodayEarned));

            // Detailed Stats
            tvDetailLearned.setText(String.valueOf(snapshot.userProgress.totalWordsLearned));
            tvDetailScanned.setText(String.valueOf(snapshot.userProgress.totalWordsScanned));
            tvDetailChats.setText(String.valueOf(snapshot.chatSessions));
            tvDetailBestStreak.setText(String.valueOf(snapshot.userProgress.bestStreak));

            if (achievementAdapter != null) {
                achievementAdapter.updateData(snapshot.achievements);
            }

            renderWeeklyChart(snapshot.weeklyStudyMinutes);
        });
    }

    private void renderWeeklyChart(List<Integer> values) {
        chartContainer.removeAllViews();
        chartContainer.setVisibility(View.VISIBLE);
        if (weeklyEmptyState != null) weeklyEmptyState.setVisibility(View.GONE);

        // Ensure 7 days
        List<Integer> chartValues = (values != null) ? new java.util.ArrayList<>(values) : new java.util.ArrayList<>(java.util.Collections.nCopies(7, 0));
        while (chartValues.size() < 7) chartValues.add(0);

        int max = 0;
        for (Integer val : chartValues) if (val != null && val > max) max = val;
        if (max < 30) max = 30;

        String[] days = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < 7; i++) {
            final int value = (i < chartValues.size() && chartValues.get(i) != null) ? chartValues.get(i) : 0;
            String label = days[i];

            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            column.setLayoutParams(columnParams);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(value + "p");
            tvTime.setTextSize(11f);
            tvTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_primary_dark));
            tvTime.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvTime.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tvTime.setPadding(0, 0, 0, (int)(6 * density));
            if (value == 0) tvTime.setAlpha(0.2f);
            column.addView(tvTime);

            FrameLayout barContainer = new FrameLayout(requireContext());
            LinearLayout.LayoutParams barFrameParams = new LinearLayout.LayoutParams((int)(24 * density), (int)(130 * density)); 
            barContainer.setLayoutParams(barFrameParams);
            
            View track = new View(requireContext());
            FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams((int)(14 * density), FrameLayout.LayoutParams.MATCH_PARENT);
            trackParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            track.setLayoutParams(trackParams);
            track.setBackgroundResource(R.drawable.bg_chart_track_premium);
            barContainer.addView(track);
            
            if (value > 0) {
                View activeBar = new View(requireContext());
                int barHeightScaled = (int) (130 * density * ((float) value / (float) max));
                FrameLayout.LayoutParams activeParams = new FrameLayout.LayoutParams((int)(14 * density), Math.min((int)(130 * density), Math.max((int)(12 * density), barHeightScaled)));
                activeParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
                activeBar.setLayoutParams(activeParams);
                activeBar.setBackgroundResource(R.drawable.bg_bar_premium_glow);
                barContainer.addView(activeBar);
            }
            column.addView(barContainer);

            TextView tvDay = new TextView(requireContext());
            tvDay.setText(label);
            tvDay.setTextSize(11f);
            tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_secondary));
            tvDay.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tvDay.setPadding(0, (int)(8 * density), 0, 0);
            tvDay.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            column.addView(tvDay);

            chartContainer.addView(column);
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
}
