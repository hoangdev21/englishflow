package com.example.englishflow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.englishflow.data.LocalAuthStore;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private LocalAuthStore localAuthStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        localAuthStore = new LocalAuthStore(getApplicationContext());

        try {
            ImageView logoView = findViewById(R.id.splashLogo);
            View titleView = findViewById(R.id.splashTitle);
            View subtitleView = findViewById(R.id.splashSubtitle);
            ImageView waveBack = findViewById(R.id.waveBack);
            ImageView waveFront = findViewById(R.id.waveFront);

            if (logoView == null || titleView == null || subtitleView == null) {
                navigateToNextScreen();
                return;
            }

            // Initial states
            logoView.setAlpha(0f);
            logoView.setTranslationY(100f);
            titleView.setAlpha(0f);
            titleView.setScaleX(0.9f);
            titleView.setScaleY(0.9f);
            subtitleView.setAlpha(0f);

            // Animate waves
            animateWave(waveBack, 3000, -200f);
            animateWave(waveFront, 2000, -400f);

            // Dolphin Jump Animation
            logoView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(1200)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Subtle breathing effect for the dolphin
                            ObjectAnimator breather = ObjectAnimator.ofFloat(logoView, "scaleX", 1.1f, 1.05f);
                            breather.setDuration(1000);
                            breather.setRepeatMode(ValueAnimator.REVERSE);
                            breather.setRepeatCount(ValueAnimator.INFINITE);
                            breather.start();
                        }
                    })
                    .start();

            // Text Animations
            titleView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(500)
                    .setDuration(800)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();

            subtitleView.animate()
                    .alpha(1f)
                    .setStartDelay(800)
                    .setDuration(800)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Stay on splash for a bit then navigate
                            subtitleView.postDelayed(() -> navigateToNextScreen(), 1200);
                        }
                    })
                    .start();

        } catch (Exception e) {
            e.printStackTrace();
            navigateToNextScreen();
        }
    }

    private void animateWave(ImageView wave, int duration, float translationX) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(wave, "translationX", 0f, translationX);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.start();
        
        // Add vertical floating to wave
        ObjectAnimator floater = ObjectAnimator.ofFloat(wave, "translationY", 0f, 20f);
        floater.setDuration(duration / 2);
        floater.setInterpolator(new AccelerateDecelerateInterpolator());
        floater.setRepeatCount(ValueAnimator.INFINITE);
        floater.setRepeatMode(ValueAnimator.REVERSE);
        floater.start();
    }

    private void navigateToNextScreen() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);

        Intent intent;
        if (isFirstLaunch) {
            intent = new Intent(this, OnboardingActivity.class);
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
        } else if (localAuthStore.hasActiveSession()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }

        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
