package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

        String title = getArguments().getString(ARG_TITLE);
        String description = getArguments().getString(ARG_DESCRIPTION);
        int position = getArguments().getInt(ARG_POSITION);

        ImageView gifView = view.findViewById(R.id.onboardingGifView);
        TextView titleView = view.findViewById(R.id.onboardingTitle);
        TextView descriptionView = view.findViewById(R.id.onboardingDescription);

        titleView.setText(title);
        descriptionView.setText(description);

        // Mảng ID các tệp GIF trong thư mục res/raw
        // Hãy đảm bảo các tệp có tên tương ứng hoặc đổi lại cho đúng ở đây
        int[] gifResources = {
            R.raw.onboarding_step1,
            R.raw.onboarding_step2,
            R.raw.onboarding_step3,
            R.raw.onboarding_step4
        };

        if (position < gifResources.length) {
            // Sử dụng Glide để load GIF cực kỳ mượt mà từ file raw
            com.bumptech.glide.Glide.with(this)
                .asGif()
                .load(gifResources[position])
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE) // File nội bộ không cần cache đĩa
                .into(gifView);
        }

        // Stagger animation based on position
        long delay = position * 100L;
        view.setAlpha(0f);
        view.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(delay)
            .start();
    }
}

