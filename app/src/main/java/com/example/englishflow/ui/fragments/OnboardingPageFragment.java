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

        TextView emojiView = view.findViewById(R.id.onboardingEmoji);
        TextView titleView = view.findViewById(R.id.onboardingTitle);
        TextView descriptionView = view.findViewById(R.id.onboardingDescription);

        emojiView.setText(emoji);
        titleView.setText(title);
        descriptionView.setText(description);

        // Stagger animation based on position
        long delay = position * 150L;
        view.setAlpha(0f);
        view.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(delay)
            .start();
    }
}
