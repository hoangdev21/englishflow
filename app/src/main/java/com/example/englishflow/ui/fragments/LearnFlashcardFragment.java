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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FlashcardItem;
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
    private TextView emojiText;
    private TextView wordText;
    private TextView ipaText;
    private TextView meaningText;
    private TextView exampleText;
    private MaterialButton pronounceButton;
    private LinearLayout srsButtons;
    private TextView srsHint;

    private List<FlashcardItem> flashcards;
    private int currentIndex = 0;
    private boolean isBack = false;
    private int earnedXp = 0;

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
        emojiText = view.findViewById(R.id.flashcardEmoji);
        wordText = view.findViewById(R.id.flashcardWord);
        ipaText = view.findViewById(R.id.flashcardIpa);
        meaningText = view.findViewById(R.id.flashcardMeaning);
        exampleText = view.findViewById(R.id.flashcardExample);
        pronounceButton = view.findViewById(R.id.btnPronounceFlashcard);
        srsButtons = view.findViewById(R.id.srsButtons);
        srsHint = view.findViewById(R.id.srsHint);

        String domain = "Lĩnh vực";
        String topic = "Đề tài";
        if (getArguments() != null) {
            domain = getArguments().getString(ARG_DOMAIN, domain);
            topic = getArguments().getString(ARG_TOPIC, topic);
        }
        titleText.setText(domain + " • " + topic);

        flashcards = AppRepository.getInstance(requireContext()).getFlashcardsForTopic(topic);
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

        MaterialButton btnHard = view.findViewById(R.id.btnHard);
        MaterialButton btnOkay = view.findViewById(R.id.btnOkay);
        MaterialButton btnEasy = view.findViewById(R.id.btnEasy);

        btnHard.setOnClickListener(v -> rateCard(1, "Ôn lại sau 10 phút"));
        btnOkay.setOnClickListener(v -> rateCard(2, "Ôn lại sau 1 ngày"));
        btnEasy.setOnClickListener(v -> rateCard(3, "Ôn lại sau 3 ngày"));
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
                if (e1 == null || e2 == null) {
                    return false;
                }
                float deltaX = e2.getX() - e1.getX();
                if (Math.abs(deltaX) > 140 && Math.abs(velocityX) > 250) {
                    if (deltaX > 0) {
                        goPrevious();
                    } else {
                        goNext();
                    }
                    return true;
                }
                return false;
            }
        });

        cardView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    private void bindFlashcard() {
        FlashcardItem card = flashcards.get(currentIndex);
        countText.setText("Thẻ " + (currentIndex + 1) + "/" + flashcards.size() + " • Vuốt để chuyển");
        emojiText.setText(card.getEmoji());
        wordText.setText(card.getWord());
        ipaText.setText(card.getIpa());
        meaningText.setText(card.getMeaning());
        exampleText.setText(card.getExample());
        updateFace();
    }

    private void flipCard() {
        cardView.animate()
                .rotationY(90f)
                .setDuration(140)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isBack = !isBack;
                        updateFace();
                        cardView.setRotationY(-90f);
                        cardView.animate().rotationY(0f).setDuration(140).setListener(null).start();
                    }
                })
                .start();
    }

    private void updateFace() {
        ipaText.setVisibility(isBack ? View.VISIBLE : View.GONE);
        meaningText.setVisibility(isBack ? View.VISIBLE : View.GONE);
        exampleText.setVisibility(isBack ? View.VISIBLE : View.GONE);
        pronounceButton.setVisibility(isBack ? View.VISIBLE : View.GONE);
        srsButtons.setVisibility(isBack ? View.VISIBLE : View.GONE);
        srsHint.setText(isBack ? "Đánh giá độ nhớ để hệ thống SRS lên lịch ôn tập" : "Chạm để lật thẻ, sau đó đánh giá độ khó");
    }

    private void rateCard(int score, String scheduleText) {
        earnedXp += score * 5;
        Toast.makeText(requireContext(), "SRS: " + scheduleText, Toast.LENGTH_SHORT).show();
        goNext();
    }

    private void goPrevious() {
        if (currentIndex == 0) {
            return;
        }
        currentIndex -= 1;
        isBack = false;
        bindFlashcard();
    }

    private void goNext() {
        if (currentIndex >= flashcards.size() - 1) {
            AppRepository.getInstance(requireContext()).addXp(earnedXp);
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openCelebration(earnedXp);
            }
            return;
        }
        currentIndex += 1;
        isBack = false;
        bindFlashcard();
    }
}
