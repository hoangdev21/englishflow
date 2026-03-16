package com.example.englishflow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final long SPLASH_DURATION = 2500; // 2.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        try {
            // Get logo view
            View logoView = findViewById(R.id.splashLogo);
            View titleView = findViewById(R.id.splashTitle);
            View subtitleView = findViewById(R.id.splashSubtitle);

            if (logoView == null || titleView == null || subtitleView == null) {
                navigateToNextScreen();
                return;
            }

            // Fade in animation
            logoView.setAlpha(0f);
            titleView.setAlpha(0f);
            subtitleView.setAlpha(0f);

            // Animate logo fade in and scale
            ObjectAnimator logoFadeIn = ObjectAnimator.ofFloat(logoView, "alpha", 0f, 1f);
            logoFadeIn.setDuration(800);
            logoFadeIn.setInterpolator(new DecelerateInterpolator());

            ObjectAnimator logoScale = ObjectAnimator.ofFloat(logoView, "scaleX", 0.8f, 1f);
            ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logoView, "scaleY", 0.8f, 1f);
            logoScale.setDuration(800);
            logoScaleY.setDuration(800);
            logoScale.setInterpolator(new DecelerateInterpolator());
            logoScaleY.setInterpolator(new DecelerateInterpolator());

            // Animate text fade in
            ObjectAnimator titleFadeIn = ObjectAnimator.ofFloat(titleView, "alpha", 0f, 1f);
            titleFadeIn.setDuration(600);
            titleFadeIn.setStartDelay(400);
            titleFadeIn.setInterpolator(new DecelerateInterpolator());

            ObjectAnimator subtitleFadeIn = ObjectAnimator.ofFloat(subtitleView, "alpha", 0f, 1f);
            subtitleFadeIn.setDuration(600);
            subtitleFadeIn.setStartDelay(600);
            subtitleFadeIn.setInterpolator(new DecelerateInterpolator());

            // Start animations
            logoFadeIn.start();
            logoScale.start();
            logoScaleY.start();
            titleFadeIn.start();
            subtitleFadeIn.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    navigateToNextScreen();
                }
            });
            subtitleFadeIn.start();
        } catch (Exception e) {
            e.printStackTrace();
            navigateToNextScreen();
        }
    }

    private void navigateToNextScreen() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);

        Intent intent;
        if (isFirstLaunch) {
            // First time: show onboarding
            intent = new Intent(this, OnboardingActivity.class);
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        } else {
            // Regular launch: go to main
            intent = new Intent(this, MainActivity.class);
        }

        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
