package com.example.englishflow.ui.fragments;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DictionaryRepository;
import com.example.englishflow.data.DictionaryResult;
import com.example.englishflow.data.FreeDictionaryService;
import com.example.englishflow.data.MyMemoryService;
import com.example.englishflow.data.WordEntry;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;

public class DictionaryBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PREFILL = "prefill";

    private DictionaryRepository dictionaryRepository;
    private AppRepository appRepository;
    private MediaPlayer mediaPlayer;
    private DictionaryResult currentResult;

    private EditText dictSearchInput;
    private ProgressBar dictLoading;
    private TextView dictError;
    private NestedScrollView dictResultCard;
    private TextView dictWord;
    private TextView dictIpa;
    private TextView dictDefinitions;
    private LinearLayoutHolder synonymsHolder;

    public static DictionaryBottomSheet newInstance(String prefillWord) {
        DictionaryBottomSheet fragment = new DictionaryBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PREFILL, prefillWord == null ? "" : prefillWord);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dictionary_bottom, container, false);

        OkHttpClient okHttpClient = new OkHttpClient();
        dictionaryRepository = new DictionaryRepository(
                new FreeDictionaryService(okHttpClient),
                new MyMemoryService(okHttpClient)
        );
        appRepository = AppRepository.getInstance(requireContext());

        dictSearchInput = view.findViewById(R.id.dictSearchInput);
        dictLoading = view.findViewById(R.id.dictLoading);
        dictError = view.findViewById(R.id.dictError);
        dictResultCard = view.findViewById(R.id.dictResultCard);
        dictWord = view.findViewById(R.id.dictWord);
        dictIpa = view.findViewById(R.id.dictIpa);
        dictDefinitions = view.findViewById(R.id.dictDefinitions);

        View synonymsRow = view.findViewById(R.id.dictSynonymsRow);
        ChipGroup synonymChips = view.findViewById(R.id.dictSynonymChips);
        synonymsHolder = new LinearLayoutHolder(synonymsRow, synonymChips);

        setupSearch(view);
        setupButtons(view);

        String prefill = getArguments() != null ? getArguments().getString(ARG_PREFILL, "") : "";
        if (!TextUtils.isEmpty(prefill)) {
            dictSearchInput.setText(prefill);
            dictSearchInput.setSelection(prefill.length());
            performSearch(prefill, view);
        }

        return view;
    }

    private void setupSearch(View view) {
        ImageButton dictSearchBtn = view.findViewById(R.id.dictSearchBtn);

        dictSearchBtn.setOnClickListener(v -> performSearch(dictSearchInput.getText().toString(), view));

        dictSearchInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean isEnterKey = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (isSearchAction || isEnterKey) {
                performSearch(dictSearchInput.getText().toString(), view);
                return true;
            }
            return false;
        });
    }

    private void setupButtons(View view) {
        MaterialButton dictBtnPronounce = view.findViewById(R.id.dictBtnPronounce);
        MaterialButton dictBtnSave = view.findViewById(R.id.dictBtnSave);

        dictBtnPronounce.setOnClickListener(v -> {
            if (currentResult == null || TextUtils.isEmpty(currentResult.getAudioUrl())) {
                Toast.makeText(requireContext(), "Khong co audio de phat", Toast.LENGTH_SHORT).show();
                return;
            }
            playAudio(currentResult.getAudioUrl());
        });

        dictBtnSave.setOnClickListener(v -> {
            if (currentResult == null || currentResult.getDefinitions() == null || currentResult.getDefinitions().isEmpty()) {
                Toast.makeText(requireContext(), "Khong co du lieu de luu", Toast.LENGTH_SHORT).show();
                return;
            }

            DictionaryResult.Definition firstDefinition = currentResult.getDefinitions().get(0);
            String meaning = safe(firstDefinition.getMeaning());
            String example = safe(firstDefinition.getExample());
            String partOfSpeech = safe(firstDefinition.getPartOfSpeech());
            if (partOfSpeech.isEmpty()) {
                partOfSpeech = "noun";
            }
            String category = "Dictionary";

            WordEntry entry = new WordEntry(
                    safe(currentResult.getWord()),
                    safe(currentResult.getIpa()),
                    meaning,
                    partOfSpeech,
                    example,
                    "",
                    category,
                    ""
            );
            appRepository.saveWord(entry);
            Toast.makeText(requireContext(), "Da luu " + safe(currentResult.getWord()), Toast.LENGTH_SHORT).show();
        });
    }

    private void performSearch(String query, View view) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            dictError.setText("Vui long nhap tu can tra");
            dictError.setVisibility(View.VISIBLE);
            dictResultCard.setVisibility(View.GONE);
            dictLoading.setVisibility(View.GONE);
            return;
        }

        dictLoading.setVisibility(View.VISIBLE);
        dictResultCard.setVisibility(View.GONE);
        dictError.setVisibility(View.GONE);

        dictionaryRepository.search(normalizedQuery, new DictionaryRepository.SearchCallback() {
            @Override
            public void onSuccess(DictionaryResult result) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    bindResult(result, view);
                    dictLoading.setVisibility(View.GONE);
                    dictError.setVisibility(View.GONE);
                    dictResultCard.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onNotFound(String query) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    dictLoading.setVisibility(View.GONE);
                    dictResultCard.setVisibility(View.GONE);
                    dictError.setText("Khong tim thay tu: " + query);
                    dictError.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    dictLoading.setVisibility(View.GONE);
                    dictResultCard.setVisibility(View.GONE);
                    dictError.setText(message);
                    dictError.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void bindResult(DictionaryResult result, View view) {
        currentResult = result;

        dictWord.setText(safe(result.getWord()));
        dictIpa.setText(safe(result.getIpa()));

        StringBuilder definitionsBuilder = new StringBuilder();
        List<DictionaryResult.Definition> definitions = result.getDefinitions();
        if (definitions != null) {
            for (DictionaryResult.Definition definition : definitions) {
                String meaning = safe(definition.getMeaning());
                if (meaning.isEmpty()) {
                    continue;
                }
                String partOfSpeech = safe(definition.getPartOfSpeech());
                definitionsBuilder.append("• ");
                if (!partOfSpeech.isEmpty()) {
                    definitionsBuilder.append("[").append(partOfSpeech).append("] ");
                }
                definitionsBuilder.append(meaning);

                String example = safe(definition.getExample());
                if (!example.isEmpty()) {
                    definitionsBuilder.append("\n  ↳ ").append(example);
                }
                definitionsBuilder.append("\n");
            }
        }

        if (definitionsBuilder.length() == 0) {
            definitionsBuilder.append("Khong co dinh nghia");
        }
        dictDefinitions.setText(definitionsBuilder.toString().trim());

        synonymsHolder.chipGroup.removeAllViews();
        List<String> synonyms = result.getSynonyms();
        if (synonyms != null && !synonyms.isEmpty()) {
            synonymsHolder.row.setVisibility(View.VISIBLE);
            for (String synonym : synonyms) {
                if (TextUtils.isEmpty(synonym)) {
                    continue;
                }
                Chip chip = new Chip(requireContext());
                chip.setText(synonym);
                chip.setCheckable(false);
                chip.setClickable(true);
                chip.setOnClickListener(v -> {
                    dictSearchInput.setText(synonym);
                    dictSearchInput.setSelection(synonym.length());
                    performSearch(synonym, view);
                });
                synonymsHolder.chipGroup.addView(chip);
            }
            if (synonymsHolder.chipGroup.getChildCount() == 0) {
                synonymsHolder.row.setVisibility(View.GONE);
            }
        } else {
            synonymsHolder.row.setVisibility(View.GONE);
        }
    }

    private void playAudio(String audioUrl) {
        releaseMediaPlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> releaseMediaPlayer());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                releaseMediaPlayer();
                Toast.makeText(requireContext(), "Khong phat duoc audio", Toast.LENGTH_SHORT).show();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            releaseMediaPlayer();
            Toast.makeText(requireContext(), "Loi audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        releaseMediaPlayer();
        super.onDestroyView();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class LinearLayoutHolder {
        private final View row;
        private final ChipGroup chipGroup;

        private LinearLayoutHolder(View row, ChipGroup chipGroup) {
            this.row = row;
            this.chipGroup = chipGroup;
        }
    }
}
