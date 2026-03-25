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
public class LearnCelebrationFragment extends Fragment {

    private static final String ARG_XP = "arg_xp";

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
        int xp = 20;
        if (getArguments() != null) {
            xp = getArguments().getInt(ARG_XP, 20);
        }

        TextView xpText = view.findViewById(R.id.xpText);
        xpText.setText("+" + xp + " XP");

        // Animations
        View iconGroup = view.findViewById(R.id.successIconGroup);
        iconGroup.setAlpha(0f);
        iconGroup.setScaleX(0.7f);
        iconGroup.setScaleY(0.7f);
        iconGroup.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(new android.view.animation.OvershootInterpolator()).start();

        // Button Listeners
        view.findViewById(R.id.btnCelebrationNext).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack(); // Back to Topics
        });

        view.findViewById(R.id.btnCelebrationBackToTopics).setOnClickListener(v -> {
            getParentFragmentManager().popBackStack(); // Back to Topics
        });

        view.findViewById(R.id.btnCelebrationHome).setOnClickListener(v -> {
            if (getActivity() != null) {
                // Navigate to index 0 of the activity's viewpager
                ((com.example.englishflow.MainActivity) getActivity()).setCurrentTab(0);
                // Or better yet, call a method in MainActivity if it has a way to change page
                // But simplified for now:
                getParentFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        });
    }
}
