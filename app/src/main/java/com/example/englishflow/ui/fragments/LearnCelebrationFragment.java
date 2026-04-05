package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class LearnCelebrationFragment extends Fragment {

    private static final String ARG_XP = "arg_xp";
    private static final String ARG_TOPIC = "arg_topic";
    private static final String ARG_DOMAIN = "arg_domain";
    private static final String ARG_LEARNED_WORDS = "arg_learned_words";
    private KonfettiView konfettiView;

    public static LearnCelebrationFragment newInstance(int earnedXp,
                                                       String completedTopic,
                                                       String completedDomain,
                                                       int learnedWords) {
        LearnCelebrationFragment fragment = new LearnCelebrationFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_XP, earnedXp);
        bundle.putString(ARG_TOPIC, completedTopic);
        bundle.putString(ARG_DOMAIN, completedDomain);
        bundle.putInt(ARG_LEARNED_WORDS, learnedWords);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_celebration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        konfettiView = view.findViewById(R.id.konfettiView);
        
        int xp = 0;
        String completedTopic = "";
        String completedDomain = "";
        int learnedWords = 0;
        if (getArguments() != null) {
            xp = getArguments().getInt(ARG_XP, 0);
            completedTopic = getArguments().getString(ARG_TOPIC, "");
            completedDomain = getArguments().getString(ARG_DOMAIN, "");
            learnedWords = getArguments().getInt(ARG_LEARNED_WORDS, 0);
        }

        TextView xpText = view.findViewById(R.id.xpText);
        xpText.setText(getString(R.string.flashcard_summary_xp_format, xp));

        TextView titleText = view.findViewById(R.id.tvCelebrationTitle);
        TextView subTitleText = view.findViewById(R.id.tvCelebrationSubTitle);
        TextView descText = view.findViewById(R.id.tvCelebrationDesc);
        titleText.setText(R.string.flashcard_summary_title);

        String safeTopic = completedTopic == null ? "" : completedTopic.trim();
        if (safeTopic.isEmpty()) {
            safeTopic = getString(R.string.learn_flashcard);
        }
        subTitleText.setText(getString(R.string.flashcard_summary_topic_format, safeTopic));

        String safeDomain = completedDomain == null ? "" : completedDomain.trim();
        if (safeDomain.isEmpty()) {
            safeDomain = getString(R.string.status_learning);
        }
        descText.setText(getString(
                R.string.flashcard_summary_desc_format,
                safeDomain,
                learnedWords,
                xp
        ));

        com.google.android.material.button.MaterialButton actionFillBlank = view.findViewById(R.id.btnCelebrationNext);
        com.google.android.material.button.MaterialButton actionFlashcard = view.findViewById(R.id.btnCelebrationBackToTopics);
        com.google.android.material.button.MaterialButton actionHome = view.findViewById(R.id.btnCelebrationHome);

        actionFillBlank.setText(R.string.flashcard_summary_action_fill_blank);
        actionFlashcard.setText(R.string.flashcard_summary_action_flashcard);
        actionHome.setText(R.string.nav_home);

        setupInitialStates(view);
        startAnimations(view);

        // Button Listeners
        String nextTopic = safeTopic;
        actionFillBlank.setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openFillBlank(nextTopic);
                return;
            }
            getParentFragmentManager().popBackStack();
        });

        String restartDomain = safeDomain;
        actionFlashcard.setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                AppRepository repository = AppRepository.getInstance(requireContext());
                DomainItem selectedDomain = null;
                for (DomainItem domainItem : repository.getDomains()) {
                    if (domainItem != null
                            && domainItem.getName() != null
                            && domainItem.getName().trim().equalsIgnoreCase(restartDomain)) {
                        selectedDomain = domainItem;
                        break;
                    }
                }

                if (selectedDomain == null) {
                    selectedDomain = new DomainItem(
                            "📚",
                            restartDomain,
                            0,
                            "#1A7A5E",
                            "#2AAE84",
                            0,
                            repository.getTopicsForDomain(restartDomain)
                    );
                }

                ((LearnFlowNavigator) parent).openTopics(selectedDomain);
                return;
            }
            getParentFragmentManager().popBackStack();
        });

        actionHome.setOnClickListener(v -> {
            if (getActivity() != null) {
                ((com.example.englishflow.MainActivity) getActivity()).setCurrentTab(0);
                getParentFragmentManager().popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                );
            }
        });
    }

    private void setupInitialStates(View view) {
        View iconGroup = view.findViewById(R.id.successIconGroup);
        View textGroup = view.findViewById(R.id.celebrationTextGroup);
        View buttonGroup = view.findViewById(R.id.celebrationButtonGroup);
        
        iconGroup.setAlpha(0f);
        iconGroup.setScaleX(0.5f);
        iconGroup.setScaleY(0.5f);
        
        textGroup.setAlpha(0f);
        textGroup.setTranslationY(60f);
        
        buttonGroup.setAlpha(0f);
        buttonGroup.setTranslationY(80f);
        
        // Stars
        view.findViewById(R.id.star1).setAlpha(0f);
        view.findViewById(R.id.star2).setAlpha(0f);
        view.findViewById(R.id.star3).setAlpha(0f);
    }

    private void startAnimations(View view) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 1. Icon Animation
            View iconGroup = view.findViewById(R.id.successIconGroup);
            iconGroup.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(1000)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
            
            // 2. Start Konfetti
            startCelebration();
            
            // 3. Text Group Animation
            View textGroup = view.findViewById(R.id.celebrationTextGroup);
            textGroup.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(800)
                    .setStartDelay(400)
                    .start();
            
            // 4. Button Group Animation
            View buttonGroup = view.findViewById(R.id.celebrationButtonGroup);
            buttonGroup.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(800)
                    .setStartDelay(700)
                    .start();
            
            // 5. Flying Stars Animation
            animateStars(view);
            
        }, 300);
    }

    private void startCelebration() {
        EmitterConfig emitterConfig = new Emitter(300, TimeUnit.MILLISECONDS).max(300);
        konfettiView.start(
                new PartyFactory(emitterConfig)
                        .shapes(Arrays.asList(nl.dionsegijn.konfetti.core.models.Shape.Square.INSTANCE, nl.dionsegijn.konfetti.core.models.Shape.Circle.INSTANCE))
                        .colors(Arrays.asList(0xfce18a, 0xff726d, 0xf4306d, 0xbda5ff, 0x10b981))
                        .setSpeedBetween(0f, 30f)
                        .position(new Position.Relative(0.5, 0.3))
                        .build()
        );
    }

    private void animateStars(View view) {
        View s1 = view.findViewById(R.id.star1);
        View s2 = view.findViewById(R.id.star2);
        View s3 = view.findViewById(R.id.star3);
        
        animateSingleStar(s1, 0, -200f, -200f);
        animateSingleStar(s2, 200, 200f, -150f);
        animateSingleStar(s3, 400, -150f, -100f);
    }

    private void animateSingleStar(View star, int delay, float xMove, float yMove) {
        star.animate()
                .alpha(0.8f)
                .translationXBy(xMove)
                .translationYBy(yMove)
                .setDuration(3000)
                .setStartDelay(delay)
                .withEndAction(() -> {
                    star.animate().alpha(0f).setDuration(1000).start();
                })
                .start();
        
        star.animate()
                .rotation(360f)
                .setDuration(3000)
                .setStartDelay(delay)
                .start();
    }
}

