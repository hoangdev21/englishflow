package com.example.englishflow.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.englishflow.MainActivity;
import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.ui.LearnedWordsActivity;
import com.example.englishflow.ui.views.LearningGaugeView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

public class LearnOverviewFragment extends Fragment {

    private static final long UI_REFRESH_MIN_INTERVAL_MS = 1500L;

    private static final int[] DOMAIN_IMAGE_IDS = {
            R.id.domainPreview1Image,
            R.id.domainPreview2Image,
            R.id.domainPreview3Image,
            R.id.domainPreview4Image
    };

    private static final int[] DOMAIN_EMOJI_IDS = {
            R.id.domainPreview1Emoji,
            R.id.domainPreview2Emoji,
            R.id.domainPreview3Emoji,
            R.id.domainPreview4Emoji
    };

    private static final int[] DOMAIN_LABEL_IDS = {
            R.id.domainPreview1Label,
            R.id.domainPreview2Label,
            R.id.domainPreview3Label,
            R.id.domainPreview4Label
    };

    private static final int[] DOMAIN_FALLBACK_IMAGES = {
            R.drawable.am_thuc,
            R.drawable.du_lich,
            R.drawable.cong_nghe,
            R.drawable.the_thao
    };

    private static final int[] DOMAIN_FALLBACK_LABELS = {
            R.string.learn_domain_preview_1_label,
            R.string.learn_domain_preview_2_label,
            R.string.learn_domain_preview_3_label,
            R.string.learn_domain_preview_4_label
    };

    private static final int[] DOMAIN_FALLBACK_EMOJIS = {
            R.string.learn_domain_preview_1_emoji,
            R.string.learn_domain_preview_2_emoji,
            R.string.learn_domain_preview_3_emoji,
            R.string.learn_domain_preview_4_emoji
    };

    private static final int[] WEEK_BAR_IDS = {
            R.id.learnBarMon,
            R.id.learnBarTue,
            R.id.learnBarWed,
            R.id.learnBarThu,
            R.id.learnBarFri,
            R.id.learnBarSat,
            R.id.learnBarSun
    };

    private AppRepository repository;

    private LearningGaugeView gaugeView;
    private TextView progressPercent;
    private TextView wordsTodayText;
    private TextView streakText;
    private TextView progressXpText;
    private TextView progressStateText;
    private TextView needToGoChipText;
    private TextView domainCountText;
    private LinearProgressIndicator journeyProgress;
    private TextView journeyCountText;
    private TextView weeklyTotalMinutesText;

    private ImageView[] domainImages;
    private TextView[] domainEmojis;
    private TextView[] domainLabels;
    private View[] weekBars;
    private long lastDashboardRenderedAt = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());

        bindViews(view);
        setupNavigation(view);
        applyInsets(view);
        refreshDashboard(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDashboard(false);
    }

    private void bindViews(View view) {
        gaugeView = view.findViewById(R.id.learnGaugeView);
        progressPercent = view.findViewById(R.id.learnProgressPercent);
        wordsTodayText = view.findViewById(R.id.learnProgressWords);
        streakText = view.findViewById(R.id.learnProgressStreak);
        progressXpText = view.findViewById(R.id.learnProgressXp);
        progressStateText = view.findViewById(R.id.learnProgressState);
        progressStateText = view.findViewById(R.id.learnProgressState);
        needToGoChipText = view.findViewById(R.id.learnChipNeedToGo);
        domainCountText = view.findViewById(R.id.learnDomainsCount);
        journeyProgress = view.findViewById(R.id.learnJourneyProgress);
        journeyCountText = view.findViewById(R.id.learnJourneyCount);
        weeklyTotalMinutesText = view.findViewById(R.id.learnWeeklyTotalMinutes);

        domainImages = new ImageView[DOMAIN_IMAGE_IDS.length];
        domainEmojis = new TextView[DOMAIN_EMOJI_IDS.length];
        domainLabels = new TextView[DOMAIN_LABEL_IDS.length];

        for (int i = 0; i < DOMAIN_IMAGE_IDS.length; i++) {
            domainImages[i] = view.findViewById(DOMAIN_IMAGE_IDS[i]);
            domainEmojis[i] = view.findViewById(DOMAIN_EMOJI_IDS[i]);
            domainLabels[i] = view.findViewById(DOMAIN_LABEL_IDS[i]);
        }

        weekBars = new View[WEEK_BAR_IDS.length];
        for (int i = 0; i < WEEK_BAR_IDS.length; i++) {
            weekBars[i] = view.findViewById(WEEK_BAR_IDS[i]);
        }
    }

    private void setupNavigation(View view) {
        View.OnClickListener openDomains = v -> openDomains();
        View.OnClickListener openJourney = v -> openJourney();

        View domainsCard = view.findViewById(R.id.cardLearnDomains);
        View domainsButton = view.findViewById(R.id.btnExploreDomains);
        View journeyCard = view.findViewById(R.id.cardLearnJourney);
        View journeyButton = view.findViewById(R.id.btnContinueJourney);
        View startFlashcardsButton = view.findViewById(R.id.btnStartFlashcards);
        View actionFlashcards = view.findViewById(R.id.actionFlashcards);
        View actionJourney = view.findViewById(R.id.actionJourney);
        View actionLearnedWords = view.findViewById(R.id.actionLearnedWords);
        View actionFillBlank = view.findViewById(R.id.actionFillBlank);
        View actionChatPractice = view.findViewById(R.id.actionChatPractice);

        if (domainsCard != null) {
            domainsCard.setOnClickListener(openDomains);
        }
        if (domainsButton != null) {
            domainsButton.setOnClickListener(openDomains);
        }
        if (startFlashcardsButton != null) {
            startFlashcardsButton.setOnClickListener(openDomains);
        }
        if (actionFlashcards != null) {
            actionFlashcards.setOnClickListener(openDomains);
        }

        if (journeyCard != null) {
            journeyCard.setOnClickListener(openJourney);
        }
        if (journeyButton != null) {
            journeyButton.setOnClickListener(openJourney);
        }
        if (actionJourney != null) {
            actionJourney.setOnClickListener(openJourney);
        }

        if (actionLearnedWords != null) {
            actionLearnedWords.setOnClickListener(v -> {
                if (!isAdded()) {
                    return;
                }
                startActivity(new Intent(requireContext(), LearnedWordsActivity.class));
            });
        }

        if (actionFillBlank != null) {
            actionFillBlank.setOnClickListener(v -> {
                String preferredTopic = repository != null ? repository.getLastTopicTitle() : null;
                openFillBlank(preferredTopic);
            });
        }

        if (actionChatPractice != null) {
            actionChatPractice.setOnClickListener(v -> switchToMainTab(3));
        }
    }

    private void applyInsets(View view) {
        View header = view.findViewById(R.id.learnHeroTextContainer);
        if (header == null) {
            return;
        }

        int initialLeft = header.getPaddingLeft();
        int initialTop = header.getPaddingTop();
        int initialRight = header.getPaddingRight();
        int initialBottom = header.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(initialLeft, initialTop + systemBars.top, initialRight, initialBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(header);
    }

    private void refreshDashboard(boolean forceRefresh) {
        if (!isAdded() || repository == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (!forceRefresh && now - lastDashboardRenderedAt < UI_REFRESH_MIN_INTERVAL_MS) {
            return;
        }

        repository.getDashboardSnapshotAsync(snapshot -> {
            if (!isAdded()) {
                return;
            }

            lastDashboardRenderedAt = SystemClock.elapsedRealtime();

            int wordsToday = snapshot.wordsLearnedToday;
            int dailyGoal = snapshot.dailyWordGoal;
            int percent = dailyGoal > 0
                    ? Math.min(100, Math.round((wordsToday * 100f) / dailyGoal))
                    : 0;
            int remainingWords = Math.max(0, dailyGoal - wordsToday);

            if (gaugeView != null) {
                gaugeView.setProgress(percent, true);
            }
            if (progressPercent != null) {
                progressPercent.setText(getString(R.string.learn_progress_percent_format, percent));
            }
            if (wordsTodayText != null) {
                wordsTodayText.setText(getString(R.string.learn_progress_words_format, wordsToday, dailyGoal));
            }

            int streak = snapshot.userProgress.currentStreak;
            if (streakText != null) {
                streakText.setText(getString(R.string.learn_progress_streak_format, streak));
            }

            int xpToday = snapshot.userProgress.xpTodayEarned;
            if (progressXpText != null) {
                progressXpText.setText(getString(R.string.learn_progress_xp_format, xpToday));
            }

            if (needToGoChipText != null) {
                if (remainingWords == 0) {
                    needToGoChipText.setText(R.string.learn_progress_need_to_go_done);
                } else {
                    needToGoChipText.setText(getString(R.string.learn_progress_need_to_go_format, remainingWords));
                }
            }

            if (progressStateText != null) {
                if (percent >= 100) {
                    progressStateText.setText(R.string.learn_progress_state_done);
                } else if (percent >= 70) {
                    progressStateText.setText(R.string.learn_progress_state_strong);
                } else if (percent >= 30) {
                    progressStateText.setText(R.string.learn_progress_state_steady);
                } else {
                    progressStateText.setText(R.string.learn_progress_state_focus);
                }
            }



            List<DomainItem> domains = snapshot.domains;
            updateDomainPreviews(domains);
            if (domainCountText != null) {
                int domainCount = domains != null ? domains.size() : 0;
                domainCountText.setText(getString(R.string.learn_domains_count_format, domainCount));
            }

            int completedNodes = snapshot.completedMapNodes;
            int totalNodes = snapshot.totalMapNodes;
            int journeyPercent = totalNodes > 0
                    ? Math.min(100, Math.round((completedNodes * 100f) / totalNodes))
                    : 0;

            if (journeyProgress != null) {
                journeyProgress.setProgress(journeyPercent, true);
            }
            if (journeyCountText != null) {
                journeyCountText.setText(getString(R.string.learn_journey_progress_format, completedNodes, totalNodes));
            }

            updateWeeklyMomentum(snapshot.weeklyStudyMinutes);
        });
    }

    private void updateWeeklyMomentum(List<Integer> weeklyMinutes) {
        if (weeklyMinutes == null) {
            weeklyMinutes = new ArrayList<>();
        }

        while (weeklyMinutes.size() < WEEK_BAR_IDS.length) {
            weeklyMinutes.add(0);
        }

        int maxMinutes = 1;
        int totalMinutes = 0;
        for (int i = 0; i < WEEK_BAR_IDS.length; i++) {
            int minutes = Math.max(0, weeklyMinutes.get(i));
            totalMinutes += minutes;
            if (minutes > maxMinutes) {
                maxMinutes = minutes;
            }
        }

        int minHeight = dp(12);
        int maxHeight = dp(72);

        for (int i = 0; i < weekBars.length; i++) {
            View bar = weekBars[i];
            if (bar == null) {
                continue;
            }
            int minutes = Math.max(0, weeklyMinutes.get(i));
            int dynamicHeight = minHeight;
            if (maxMinutes > 0) {
                dynamicHeight = minHeight + Math.round(((float) minutes / maxMinutes) * (maxHeight - minHeight));
            }

            ViewGroup.LayoutParams params = bar.getLayoutParams();
            params.height = dynamicHeight;
            bar.setLayoutParams(params);
            bar.setAlpha(minutes > 0 ? 1f : 0.35f);
        }

        if (weeklyTotalMinutesText != null) {
            weeklyTotalMinutesText.setText(getString(R.string.learn_week_total_minutes_format, totalMinutes));
        }
    }

    private void updateDomainPreviews(List<DomainItem> domains) {
        for (int i = 0; i < domainImages.length; i++) {
            ImageView imageView = domainImages[i];
            TextView emojiView = domainEmojis[i];
            TextView labelView = domainLabels[i];
            if (imageView == null || emojiView == null || labelView == null) {
                continue;
            }

            DomainItem domain = domains != null && i < domains.size() ? domains.get(i) : null;
            int fallbackImage = DOMAIN_FALLBACK_IMAGES[i];
            String fallbackEmoji = getString(DOMAIN_FALLBACK_EMOJIS[i]);
            String fallbackLabel = getString(DOMAIN_FALLBACK_LABELS[i]);

            if (domain != null) {
                int imageRes = domain.getBackgroundImageRes() != 0
                        ? domain.getBackgroundImageRes()
                        : fallbackImage;
                imageView.setImageResource(imageRes);

                String emoji = domain.getEmoji();
                if (emoji == null || emoji.trim().isEmpty()) {
                    emoji = fallbackEmoji;
                }
                emojiView.setText(emoji);

                String label = domain.getName();
                if (label == null || label.trim().isEmpty()) {
                    label = fallbackLabel;
                }
                labelView.setText(label);
            } else {
                imageView.setImageResource(fallbackImage);
                emojiView.setText(fallbackEmoji);
                labelView.setText(fallbackLabel);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openFillBlank(String preferredTopic) {
        Fragment parent = getParentFragment();
        if (parent instanceof LearnFlowNavigator) {
            ((LearnFlowNavigator) parent).openFillBlank(preferredTopic);
        }
    }

    private void switchToMainTab(int tabIndex) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setCurrentTab(tabIndex);
        }
    }

    private void openDomains() {
        Fragment parent = getParentFragment();
        if (parent instanceof LearnFlowNavigator) {
            ((LearnFlowNavigator) parent).openDomains();
        }
    }

    private void openJourney() {
        Fragment parent = getParentFragment();
        if (parent instanceof LearnFlowNavigator) {
            ((LearnFlowNavigator) parent).openJourney();
        }
    }
}
