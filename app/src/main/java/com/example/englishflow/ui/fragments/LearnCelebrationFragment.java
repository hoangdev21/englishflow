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
        TextView confettiText = view.findViewById(R.id.confettiText);
        confettiText.setScaleX(0.8f);
        confettiText.setScaleY(0.8f);
        confettiText.setAlpha(0.1f);
        confettiText.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(450).start();
    }
}
