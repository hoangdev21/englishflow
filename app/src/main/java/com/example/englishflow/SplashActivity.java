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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.englishflow.admin.AdminDashboardActivity;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FirebaseUserStore;
import com.example.englishflow.reminder.StudyReminderScheduler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private FirebaseAuth firebaseAuth;
    private FirebaseUserStore firebaseUserStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUserStore = new FirebaseUserStore();
        if (BuildConfig.DEBUG) {
            com.example.englishflow.data.FirebaseSeeder.createDefaultAdminAccount();
        }
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
                    .setDuration(950)
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
                    .setStartDelay(280)
                    .setDuration(600)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            subtitleView.animate()
                    .alpha(0.8f)
                    .translationY(0f)
                    .setStartDelay(520)
                    .setDuration(550)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            subtitleView.postDelayed(() -> navigateToNextScreen(), 350);
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

        if (isFirstLaunch) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
            return;
        }

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
            return;
        }

        String photoUrl = currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "";
        firebaseUserStore.getOrCreateProfile(currentUser, currentUser.getDisplayName(), photoUrl, profile -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            Intent nextIntent;
            if (profile == null) {
                firebaseAuth.signOut();
                nextIntent = new Intent(this, LoginActivity.class);
            } else if (profile.isLocked()) {
                firebaseAuth.signOut();
                Toast.makeText(this, R.string.auth_account_locked, Toast.LENGTH_LONG).show();
                nextIntent = new Intent(this, LoginActivity.class);
            } else {
                boolean forceAdmin = com.example.englishflow.data.FirebaseSeeder.isAdminEmail(profile.email);
                com.example.englishflow.data.FirebaseSeeder.seedAdminIfNeeded(profile.uid, profile.email);
                AppRepository.getInstance(getApplicationContext()).setUserName(profile.displayName);
                
                nextIntent = (profile.isAdmin() || forceAdmin)
                        ? new Intent(this, AdminDashboardActivity.class)
                        : new Intent(this, MainActivity.class);
            }

            startActivity(nextIntent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }
}
