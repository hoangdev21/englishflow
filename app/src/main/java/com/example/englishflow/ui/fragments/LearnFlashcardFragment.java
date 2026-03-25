package com.example.englishflow.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FlashcardItem;
import com.example.englishflow.data.TopicItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

public class LearnFlashcardFragment extends Fragment {

    private static final String ARG_DOMAIN = "arg_domain";
    private static final String ARG_TOPIC = "arg_topic";

    private TextView titleText;
    private TextView countText;
    private MaterialCardView cardView;
    private View cardContentRoot;
    private View frontLayout;
    private View backLayout;
    
    private TextView emojiText;
    private TextView wordText;
    private TextView ipaText;
    private TextView meaningText;
    private TextView exampleText;
    private MaterialButton pronounceButton;
    
    private LinearLayout srsButtons;
    private TextView srsHint;
    private com.google.android.material.progressindicator.LinearProgressIndicator sessionProgress;

    private List<FlashcardItem> flashcards;
    private int currentIndex = 0;
    private boolean isBack = false;
    private int earnedXp = 0;
    private String currentTopic;

    private TextToSpeech textToSpeech;

    public static LearnFlashcardFragment newInstance(String domain, String topic) {
        LearnFlashcardFragment fragment = new LearnFlashcardFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_DOMAIN, domain);
        bundle.putString(ARG_TOPIC, topic);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_flashcard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleText = view.findViewById(R.id.flashcardTitle);
        countText = view.findViewById(R.id.flashcardCount);
        cardView = view.findViewById(R.id.flashcardCard);
        cardContentRoot = view.findViewById(R.id.cardContentRoot);
        frontLayout = view.findViewById(R.id.frontLayout);
        backLayout = view.findViewById(R.id.backLayout);
        
        emojiText = view.findViewById(R.id.flashcardEmoji);
        wordText = view.findViewById(R.id.flashcardWord);
        ipaText = view.findViewById(R.id.flashcardIpa);
        meaningText = view.findViewById(R.id.flashcardMeaning);
        exampleText = view.findViewById(R.id.flashcardExample);
        pronounceButton = view.findViewById(R.id.btnPronounceFlashcard);
        
        srsButtons = view.findViewById(R.id.srsButtons);
        srsHint = view.findViewById(R.id.srsHint);
        sessionProgress = view.findViewById(R.id.sessionProgress);
        
        view.findViewById(R.id.btnBackFlashcards).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack();
        });

        String domain = "Lĩnh vực";
        currentTopic = "Đề tài";
        if (getArguments() != null) {
            domain = getArguments().getString(ARG_DOMAIN, domain);
            currentTopic = getArguments().getString(ARG_TOPIC, currentTopic);
        }
        titleText.setText(domain + " • " + currentTopic);

        flashcards = AppRepository.getInstance(requireContext()).getFlashcardsForTopic(currentTopic);
        bindFlashcard();

        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        cardView.setOnClickListener(v -> flipCard());
        setupSwipeNavigation();

        pronounceButton.setOnClickListener(v -> {
            FlashcardItem item = flashcards.get(currentIndex);
            textToSpeech.speak(item.getWord(), TextToSpeech.QUEUE_FLUSH, null, "flashcard-word");
        });

        view.findViewById(R.id.btnHard).setOnClickListener(v -> rateCard(1, "Ôn lại sớm"));
        view.findViewById(R.id.btnOkay).setOnClickListener(v -> rateCard(2, "Đang học..."));
        view.findViewById(R.id.btnEasy).setOnClickListener(v -> rateCard(3, "Đã thuộc!"));
        
        // Setup distance for 3D effect
        float scale = getResources().getDisplayMetrics().density;
        cardView.setCameraDistance(8000 * scale);
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    private void setupSwipeNavigation() {
        GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float deltaX = e2.getX() - e1.getX();
                if (Math.abs(deltaX) > 150 && Math.abs(velocityX) > 300) {
                    if (deltaX > 0) goPrevious(); else goNext();
                    return true;
                }
                return false;
            }
        });
        cardView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void bindFlashcard() {
        FlashcardItem card = flashcards.get(currentIndex);
        countText.setText("Thẻ " + (currentIndex + 1) + " / " + flashcards.size());
        emojiText.setText(card.getEmoji());
        wordText.setText(card.getWord());
        ipaText.setText(card.getIpa());
        meaningText.setText(card.getMeaning());
        exampleText.setText(card.getExample());
        
        int progress = (int) (((float) (currentIndex + 1) / flashcards.size()) * 100);
        sessionProgress.setProgress(progress, true);
        
        isBack = false;
        updateUIState(false);
    }

    private void flipCard() {
        // Professional 3D Flip
        cardView.animate()
                .rotationY(isBack ? 0f : 180f)
                .setDuration(400)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        // Switch content midway through the flip
                        cardView.postDelayed(() -> {
                            isBack = !isBack;
                            updateUIState(true);
                        }, 200);
                    }
                })
                .start();
    }

    private void updateUIState(boolean immediate) {
        if (isBack) {
            frontLayout.setVisibility(View.GONE);
            backLayout.setVisibility(View.VISIBLE);
            // Flip the back layout content so it's not mirrored
            backLayout.setRotationY(180f); 
            srsButtons.setVisibility(View.VISIBLE);
            srsHint.setText("Đánh giá mức độ ghi nhớ");
        } else {
            frontLayout.setVisibility(View.VISIBLE);
            backLayout.setVisibility(View.GONE);
            srsButtons.setVisibility(View.INVISIBLE);
            srsHint.setText("Chạm để lật thẻ • Vuốt để chuyển");
            if (!immediate) cardView.setRotationY(0f);
        }
    }

    private void rateCard(int score, String feedback) {
        earnedXp += score * 10;
        // In real app, update individual card Spaced Repetition data here
        goNext();
    }

    private void goPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            bindFlashcard();
        }
    }

    private void goNext() {
        if (currentIndex >= flashcards.size() - 1) {
            finishSession();
            return;
        }
        currentIndex++;
        bindFlashcard();
    }

    private void finishSession() {
        AppRepository repo = AppRepository.getInstance(requireContext());
        repo.addXp(earnedXp);
        repo.updateTopicStatus(currentTopic, TopicItem.STATUS_COMPLETED);
        
        Fragment parent = getParentFragment();
        if (parent instanceof LearnFlowNavigator) {
            ((LearnFlowNavigator) parent).openCelebration(earnedXp);
        }
    }
}
