package com.example.englishflow.ui.fragments;

import android.app.AlertDialog;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.ui.adapters.AchievementAdapter;
import com.example.englishflow.ui.adapters.DictionaryAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private AppRepository repository;
    private TextToSpeech textToSpeech;

    private TextView nameText;
    private TextView levelText;
    private TextView statsText;
    private LinearLayout chartContainer;
    private RecyclerView dictionaryRecycler;
    private DictionaryAdapter dictionaryAdapter;

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

        nameText = view.findViewById(R.id.profileName);
        levelText = view.findViewById(R.id.profileLevel);
        statsText = view.findViewById(R.id.profileStats);
        chartContainer = view.findViewById(R.id.chartContainer);
        dictionaryRecycler = view.findViewById(R.id.dictionaryRecycler);

        MaterialButton btnChangeName = view.findViewById(R.id.btnChangeName);
        MaterialButton btnReset = view.findViewById(R.id.btnResetProgress);

        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        RecyclerView achievementRecycler = view.findViewById(R.id.achievementRecycler);
        achievementRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        achievementRecycler.setAdapter(new AchievementAdapter(repository.getAchievements()));

        dictionaryRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        bindDictionary();
        renderProfile();

        btnChangeName.setOnClickListener(v -> showChangeNameDialog());
        btnReset.setOnClickListener(v -> {
            repository.resetProgress();
            bindDictionary();
            renderProfile();
            Toast.makeText(requireContext(), "Đã reset toàn bộ tiến độ", Toast.LENGTH_SHORT).show();
        });
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
        statsText.setText(
                "Từ đã học: " + repository.getLearnedWords() + "\n"
                        + "Ảnh đã scan: " + repository.getScannedImages() + "\n"
                        + "Số cuộc chat: " + repository.getChatSessions() + "\n"
                        + "Streak dài nhất: " + repository.getBestStreak()
        );
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
            int barHeight = (int) (95f * ((float) value / (float) max));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(barHeight, 8));
            bar.setLayoutParams(barParams);
            bar.setBackgroundResource(R.drawable.bg_card_gradient_green);

            TextView label = new TextView(requireContext());
            label.setText(String.valueOf(value));
            label.setTextSize(11f);
            label.setTextColor(getResources().getColor(R.color.ef_text_secondary));
            label.setPadding(0, 6, 0, 0);
            label.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

            column.addView(bar);
            column.addView(label);
            chartContainer.addView(column);
        }
    }

    private void bindDictionary() {
        List<WordEntry> words = repository.getSavedWords();
        dictionaryAdapter = new DictionaryAdapter(words, new DictionaryAdapter.DictionaryActionListener() {
            @Override
            public void onPronounce(WordEntry wordEntry) {
                textToSpeech.speak(wordEntry.getWord(), TextToSpeech.QUEUE_FLUSH, null, "dictionary-word");
            }

            @Override
            public void onDelete(WordEntry wordEntry) {
                repository.removeWord(wordEntry);
                bindDictionary();
            }
        });
        dictionaryRecycler.setAdapter(dictionaryAdapter);
    }

    private void showChangeNameDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Nhập tên mới");
        input.setText(repository.getUserName());
        input.setPadding(40, 24, 40, 24);

        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi tên")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!newName.isEmpty()) {
                        repository.setUserName(newName);
                        renderProfile();
                    }
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }
}
