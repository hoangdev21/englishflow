package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.ui.adapters.OnboardingPagerAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private com.google.android.material.button.MaterialButton skipButton;
    private com.google.android.material.button.MaterialButton nextButton;
    
    // Custom Stepper Views
    private android.widget.TextView s1, s2, s3, s4;
    private android.view.View l1, l2, l3;

    private FrameLayout fallingTextContainer;
    private nl.dionsegijn.konfetti.xml.KonfettiView konfettiView;
    private final java.util.Random random = new java.util.Random();
    private final String[] fallingTexts = {"Xin chào", "Hello", "Welcome", "EnglishFlow", "Cheers", "🎉", "📚", "💬", "✨"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        try {
            viewPager = findViewById(R.id.onboardingViewPager);
            skipButton = findViewById(R.id.btnSkip);
            nextButton = findViewById(R.id.btnNext);
            fallingTextContainer = findViewById(R.id.fallingTextContainer);
            konfettiView = findViewById(R.id.konfettiView);

            // Stepper views
            s1 = findViewById(R.id.step1);
            s2 = findViewById(R.id.step2);
            s3 = findViewById(R.id.step3);
            s4 = findViewById(R.id.step4);
            l1 = findViewById(R.id.line1);
            l2 = findViewById(R.id.line2);
            l3 = findViewById(R.id.line3);

            if (viewPager == null || skipButton == null || nextButton == null) {
                startMainActivity();
                return;
            }

            // Setup ViewPager with adapter
            com.example.englishflow.ui.adapters.OnboardingPagerAdapter adapter = new com.example.englishflow.ui.adapters.OnboardingPagerAdapter(this);
            viewPager.setAdapter(adapter);
            viewPager.setOffscreenPageLimit(3); // Giữ tất cả các trang để tránh load lại gây lag

            // Skip button
            skipButton.setOnClickListener(v -> startMainActivity());

            // Next button
            nextButton.setOnClickListener(v -> {
                if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
                } else {
                    celebrateAndFinish();
                }
            });

            // Update button text based on page
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateStepper(position);
                    if (position == adapter.getItemCount() - 1) {
                        nextButton.setText("Bắt đầu");
                    } else {
                        nextButton.setText("Tiếp tục");
                        if (position > 0) {
                            showSimpleCelebration();
                        }
                    }
                }
            });

            // Start falling text
            startFallingTextLoop();

        } catch (Exception e) {
            e.printStackTrace();
            startMainActivity();
        }
    }

    private void startFallingTextLoop() {
        if (fallingTextContainer == null) return;
        fallingTextContainer.postDelayed(new Runnable() {
            @Override
            public void run() {
                spawnFallingText();
                if (!isFinishing()) {
                    fallingTextContainer.postDelayed(this, 2000); // 2 giây một lần để mượt hơn
                }
            }
        }, 500);
    }

    private void spawnFallingText() {
        if (fallingTextContainer == null) return;
        
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(fallingTexts[random.nextInt(fallingTexts.length)]);
        textView.setTextSize(14 + random.nextInt(10));
        textView.setTextColor(getColor(R.color.ef_primary));
        textView.setAlpha(0.4f);
        
        int startX = random.nextInt(Math.max(1, fallingTextContainer.getWidth() - 200));
        textView.setX(startX);
        textView.setY(-200);
        
        fallingTextContainer.addView(textView);
        
        textView.animate()
            .translationY(fallingTextContainer.getHeight() + 200)
            .setDuration(4000 + random.nextInt(3000))
            .setInterpolator(new android.view.animation.LinearInterpolator())
            .withEndAction(() -> fallingTextContainer.removeView(textView))
            .start();
    }

    private void showSimpleCelebration() {
        if (konfettiView == null) return;
        
        nl.dionsegijn.konfetti.core.emitter.EmitterConfig emitterConfig = new nl.dionsegijn.konfetti.core.emitter.Emitter(30L, java.util.concurrent.TimeUnit.MILLISECONDS).max(30);
        nl.dionsegijn.konfetti.core.Party party = new nl.dionsegijn.konfetti.core.PartyFactory(emitterConfig)
            .spread(180)
            .colors(java.util.Arrays.asList(0x0D9373, 0xFF6B5A, 0x5BB8FF, 0xFFA726))
            .setSpeedBetween(10f, 30f)
            .position(new nl.dionsegijn.konfetti.core.Position.Relative(0.5, -0.1))
            .build();
        konfettiView.start(java.util.Collections.singletonList(party));
    }

    private void celebrateAndFinish() {
        if (konfettiView == null) {
            startMainActivity();
            return;
        }

        nl.dionsegijn.konfetti.core.emitter.EmitterConfig emitterConfig = new nl.dionsegijn.konfetti.core.emitter.Emitter(1L, java.util.concurrent.TimeUnit.SECONDS).perSecond(50);
        nl.dionsegijn.konfetti.core.Party party = new nl.dionsegijn.konfetti.core.PartyFactory(emitterConfig)
            .angle(270)
            .spread(120)
            .colors(java.util.Arrays.asList(0x0D9373, 0xFF6B5A, 0x5BB8FF, 0xFFA726))
            .setSpeedBetween(25f, 45f)
            .position(new nl.dionsegijn.konfetti.core.Position.Relative(0.5, 1.0))
            .build();
        konfettiView.start(java.util.Collections.singletonList(party));

        if (fallingTextContainer != null) {
            fallingTextContainer.postDelayed(this::startMainActivity, 1500);
        } else {
            startMainActivity();
        }
    }

    private void updateStepper(int position) {
        if (s1 == null || s2 == null || s3 == null || s4 == null) return;
        
        // Reset all steps to inactive
        s1.setBackgroundResource(R.drawable.bg_step_inactive); s1.setTextColor(getColor(R.color.ef_text_tertiary));
        s2.setBackgroundResource(R.drawable.bg_step_inactive); s2.setTextColor(getColor(R.color.ef_text_tertiary));
        s3.setBackgroundResource(R.drawable.bg_step_inactive); s3.setTextColor(getColor(R.color.ef_text_tertiary));
        s4.setBackgroundResource(R.drawable.bg_step_inactive); s4.setTextColor(getColor(R.color.ef_text_tertiary));
        
        if (l1 != null) l1.setBackgroundColor(getColor(R.color.ef_outline));
        if (l2 != null) l2.setBackgroundColor(getColor(R.color.ef_outline));
        if (l3 != null) l3.setBackgroundColor(getColor(R.color.ef_outline));

        // Highlight active and previous steps
        if (position >= 0) {
            s1.setBackgroundResource(R.drawable.bg_step_active); s1.setTextColor(getColor(R.color.white));
        }
        if (position >= 1) {
            if (l1 != null) l1.setBackgroundColor(getColor(R.color.ef_primary));
            s2.setBackgroundResource(R.drawable.bg_step_active); s2.setTextColor(getColor(R.color.white));
        }
        if (position >= 2) {
            if (l2 != null) l2.setBackgroundColor(getColor(R.color.ef_primary));
            s3.setBackgroundResource(R.drawable.bg_step_active); s3.setTextColor(getColor(R.color.white));
        }
        if (position >= 3) {
            if (l3 != null) l3.setBackgroundColor(getColor(R.color.ef_primary));
            s4.setBackgroundResource(R.drawable.bg_step_active); s4.setTextColor(getColor(R.color.white));
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
