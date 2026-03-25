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
import com.example.englishflow.reminder.StudyReminderScheduler;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private LocalAuthStore localAuthStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        localAuthStore = new LocalAuthStore(getApplicationContext());
        StudyReminderScheduler.rescheduleFromPreferences(getApplicationContext());

        try {
            ImageView logoView = findViewById(R.id.splashLogo);
            View titleView = findViewById(R.id.splashTitle);
            View subtitleView = findViewById(R.id.splashSubtitle);
            View logoGlow = findViewById(R.id.logoGlow);
            
            ImageView waveDeep = findViewById(R.id.waveDeep);
            ImageView waveMid = findViewById(R.id.waveMid);
            ImageView waveFront = findViewById(R.id.waveFront);

            if (logoView == null || titleView == null || subtitleView == null) {
                navigateToNextScreen();
                return;
            }

            // Initial states
            logoView.setAlpha(0f);
            logoView.setTranslationY(60f);
            logoView.setScaleX(0.8f);
            logoView.setScaleY(0.8f);
            
            titleView.setAlpha(0f);
            titleView.setTranslationY(30f);
            subtitleView.setAlpha(0f);
            subtitleView.setTranslationY(20f);

            // Animate realistic waves layers with different speeds
            animateWave(waveDeep, 7000, -100f, 10f);   // Very slow, far away
            animateWave(waveMid, 5000, -250f, 15f);    // Medium depth
            animateWave(waveFront, 3500, -400f, 25f);  // Faster, prominent

            // Logo Glow Pulse
            if (logoGlow != null) {
                logoGlow.animate().alpha(1f).setDuration(2000).start();
                ObjectAnimator glowPulser = ObjectAnimator.ofFloat(logoGlow, "scaleX", 0.9f, 1.2f);
                glowPulser.setDuration(3000);
                glowPulser.setRepeatMode(ValueAnimator.REVERSE);
                glowPulser.setRepeatCount(ValueAnimator.INFINITE);
                glowPulser.setInterpolator(new AccelerateDecelerateInterpolator());
                glowPulser.start();
                ObjectAnimator glowPulserY = ObjectAnimator.ofFloat(logoGlow, "scaleY", 0.9f, 1.2f);
                glowPulserY.setDuration(3000);
                glowPulserY.setRepeatMode(ValueAnimator.REVERSE);
                glowPulserY.setRepeatCount(ValueAnimator.INFINITE);
                glowPulserY.start();
            }

            // High-end Logo Entrance Animation
            logoView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(1500)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Subtle breathing effect for the dolphin logo
                            ObjectAnimator breather = ObjectAnimator.ofFloat(logoView, "scaleX", 1.0f, 1.05f);
                            breather.setDuration(1500);
                            breather.setRepeatMode(ValueAnimator.REVERSE);
                            breather.setRepeatCount(ValueAnimator.INFINITE);
                            breather.setInterpolator(new AccelerateDecelerateInterpolator());
                            breather.start();
                            
                            ObjectAnimator breatherY = ObjectAnimator.ofFloat(logoView, "scaleY", 1.0f, 1.05f);
                            breatherY.setDuration(1500);
                            breatherY.setRepeatMode(ValueAnimator.REVERSE);
                            breatherY.setRepeatCount(ValueAnimator.INFINITE);
                            breatherY.start();
                        }
                    })
                    .start();

            // Refined Text Animations
            titleView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(600)
                    .setDuration(1000)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            subtitleView.animate()
                    .alpha(0.8f)
                    .translationY(0f)
                    .setStartDelay(1000)
                    .setDuration(1000)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Longer wait for the user to enjoy the ocean atmosphere
                            subtitleView.postDelayed(() -> navigateToNextScreen(), 1500);
                        }
                    })
                    .start();

        } catch (Exception e) {
            e.printStackTrace();
            navigateToNextScreen();
        }
    }

    private void animateWave(ImageView wave, int duration, float translationX, float floatY) {
        if (wave == null) return;
        
        // Horizontal scroll
        ObjectAnimator animator = ObjectAnimator.ofFloat(wave, "translationX", 0f, translationX);
        animator.setDuration(duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.start();
        
        // Vertical float (gentle bobbing)
        ObjectAnimator floater = ObjectAnimator.ofFloat(wave, "translationY", 0f, floatY);
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
