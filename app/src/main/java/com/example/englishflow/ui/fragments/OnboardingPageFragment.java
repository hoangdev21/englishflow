package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.airbnb.lottie.LottieAnimationView;


public class OnboardingPageFragment extends Fragment {

    private static final String ARG_EMOJI = "emoji";
    private static final String ARG_TITLE = "title";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_POSITION = "position";

    public OnboardingPageFragment() {
    }

    public static OnboardingPageFragment newInstance(String emoji, String title, String description, int position) {
        OnboardingPageFragment fragment = new OnboardingPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EMOJI, emoji);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) return;

        String emoji = getArguments().getString(ARG_EMOJI);
        String title = getArguments().getString(ARG_TITLE);
        String description = getArguments().getString(ARG_DESCRIPTION);
        int position = getArguments().getInt(ARG_POSITION);

        View whaleContainer = view.findViewById(R.id.whaleContainer);
        LottieAnimationView whaleLottie = view.findViewById(R.id.whaleLottie);
        android.widget.ImageView waveFront = view.findViewById(R.id.waveFront);
        android.widget.ImageView waveBack = view.findViewById(R.id.waveBack);
        
        TextView titleView = view.findViewById(R.id.onboardingTitle);
        TextView descriptionView = view.findViewById(R.id.onboardingDescription);

        titleView.setText(title);
        descriptionView.setText(description);

        // Use LottieAnimationView to avoid dotlottie native libs that break 16 KB page-size checks.
        whaleLottie.setAnimationFromUrl("https://assets4.lottiefiles.com/packages/lf20_kkflmtur.json");
        whaleLottie.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        whaleLottie.setSpeed(1.2f);
        whaleLottie.playAnimation();

        // Still keep wave swaying for depth effect
        startWaveAnimation(waveFront, waveBack);

        // Stagger animation based on position
        long delay = position * 150L;
        view.setAlpha(0f);
        view.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(delay)
            .start();
    }

    private void startWaveAnimation(View frontWave, View backWave) {
        // Front wave swaying
        frontWave.animate()
            .translationX(20f)
            .setDuration(1500)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .withEndAction(() -> {
                if (isAdded()) {
                    frontWave.animate()
                        .translationX(-20f)
                        .setDuration(1500)
                        .withEndAction(() -> { if (isAdded()) startWaveAnimation(frontWave, backWave); })
                        .start();
                }
            }).start();
            
        // Back wave swaying (opposite direction)
        backWave.animate()
            .translationX(-15f)
            .setDuration(1800)
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .withEndAction(() -> {
                if (isAdded()) {
                    backWave.animate()
                        .translationX(15f)
                        .setDuration(1800)
                        .withEndAction(null)
                        .start();
                }
            }).start();
    }
}
