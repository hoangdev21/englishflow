package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;

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
    private KonfettiView konfettiView;

    public static LearnCelebrationFragment newInstance(int earnedXp) {
        LearnCelebrationFragment fragment = new LearnCelebrationFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_XP, earnedXp);
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
        
        int xp = 20;
        if (getArguments() != null) {
            xp = getArguments().getInt(ARG_XP, 20);
        }

        TextView xpText = view.findViewById(R.id.xpText);
        xpText.setText("+" + xp + " XP");

        setupInitialStates(view);
        startAnimations(view);

        // Button Listeners
        view.findViewById(R.id.btnCelebrationNext).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack(); // Back to Topics
        });

        view.findViewById(R.id.btnCelebrationBackToTopics).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack(); // Back to Topics
        });

        view.findViewById(R.id.btnCelebrationHome).setOnClickListener(v -> {
            if (getActivity() != null) {
                ((com.example.englishflow.MainActivity) getActivity()).setCurrentTab(0);
                getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
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

