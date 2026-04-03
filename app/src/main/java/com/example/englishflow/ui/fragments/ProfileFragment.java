package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.LocalAuthStore;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.ui.adapters.AchievementAdapter;
import com.example.englishflow.ui.adapters.DictionaryAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private static final float TTS_SPEECH_RATE = 0.9f;
    private static final float TTS_PITCH = 1.0f;

    private AppRepository repository;
    private LocalAuthStore localAuthStore;
    private TextToSpeech textToSpeech;

    private TextView nameText;
    private TextView levelText;
    private TextView tvDetailLearned, tvDetailScanned, tvDetailChats, tvDetailBestStreak;
    private TextView tvLearnedCount, tvStreakCount, tvXpCount;
    private LinearLayout chartContainer;

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

        nameText = view.findViewById(R.id.profileName);
        levelText = view.findViewById(R.id.profileLevel);
        
        tvDetailLearned = view.findViewById(R.id.tvDetailLearned);
        tvDetailScanned = view.findViewById(R.id.tvDetailScanned);
        tvDetailChats = view.findViewById(R.id.tvDetailChats);
        tvDetailBestStreak = view.findViewById(R.id.tvDetailBestStreak);
        
        tvLearnedCount = view.findViewById(R.id.tvLearnedCount);
        tvStreakCount = view.findViewById(R.id.tvStreakCount);
        tvXpCount = view.findViewById(R.id.tvXpCount);
        chartContainer = view.findViewById(R.id.chartContainer);

        MaterialButton btnChangeName = view.findViewById(R.id.btnChangeName);
        MaterialButton btnReset = view.findViewById(R.id.btnResetProgress);
        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        MaterialButton btnViewDictionary = view.findViewById(R.id.btnViewSavedDictionary);

        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(TTS_SPEECH_RATE);
                textToSpeech.setPitch(TTS_PITCH);
            }
        });

        RecyclerView achievementRecycler = view.findViewById(R.id.achievementRecycler);
        achievementRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        achievementRecycler.setAdapter(new AchievementAdapter(repository.getAchievements()));

        renderProfile();
        
        btnViewDictionary.setOnClickListener(v -> {
            if (getActivity() != null) {
                startActivity(new android.content.Intent(requireContext(), com.example.englishflow.ui.LearnedWordsActivity.class));
            }
        });

        btnChangeName.setOnClickListener(v -> showChangeNameDialog());
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
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    private void renderProfile() {
        nameText.setText(repository.getUserName());
        levelText.setText("Level: " + repository.getCefrLevel() + " • " + repository.getLearnedWords() + " từ đã học");
        
        tvLearnedCount.setText(String.valueOf(repository.getLearnedWords()));
        tvStreakCount.setText(String.valueOf(repository.getStreakDays()));
        tvXpCount.setText(String.valueOf(repository.getXpToday()));

        // Detailed Stats
        tvDetailLearned.setText(String.valueOf(repository.getLearnedWords()));
        tvDetailScanned.setText(String.valueOf(repository.getScannedImages()));
        tvDetailChats.setText(String.valueOf(repository.getChatSessions()));
        tvDetailBestStreak.setText(String.valueOf(repository.getBestStreak()));
        
        renderWeeklyChart(repository.getWeeklyStudyMinutes());
    }

    private void renderWeeklyChart(List<Integer> values) {
        chartContainer.removeAllViews();
        int max = 1;
        for (Integer value : values) {
            if (value > max) {
                max = value;
            }
        }

        for (int i = 0; i < values.size(); i++) {
            int value = values.get(i);

            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i < values.size() - 1) {
                columnParams.setMarginEnd(6);
            }
            column.setLayoutParams(columnParams);

            View bar = new View(requireContext());
            int barHeight = (int) (100f * ((float) value / (float) max));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(barHeight, 5));
            bar.setLayoutParams(barParams);
            bar.setBackgroundResource(R.drawable.bg_bar_premium);

            TextView label = new TextView(requireContext());
            label.setText(String.valueOf(value));
            label.setTextSize(10f);
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.ef_text_secondary));
            label.setPadding(0, 4, 0, 0);
            label.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

            column.addView(bar);
            column.addView(label);
            chartContainer.addView(column);
        }
    }



    private void showChangeNameDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Nhập tên mới");
        input.setText(repository.getUserName());
        input.setPadding(40, 24, 40, 24);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đổi tên hiển thị")
                .setView(input)
                .setPositiveButton("Lưu thay đổi", (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newName.isEmpty()) {
                        repository.setUserName(newName);
                        localAuthStore.updateCurrentDisplayName(newName);
                        renderProfile();
                        Toast.makeText(requireContext(), "Đã cập nhật tên mới", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
