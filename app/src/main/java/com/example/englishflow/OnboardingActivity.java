package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.ui.adapters.OnboardingPagerAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private MaterialButton skipButton;
    private MaterialButton nextButton;
    
    // Custom Stepper Views
    private android.widget.TextView s1, s2, s3, s4;
    private android.view.View l1, l2, l3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        try {
            viewPager = findViewById(R.id.onboardingViewPager);
            skipButton = findViewById(R.id.btnSkip);
            nextButton = findViewById(R.id.btnNext);

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
            OnboardingPagerAdapter adapter = new OnboardingPagerAdapter(this);
            viewPager.setAdapter(adapter);

            // Skip button
            skipButton.setOnClickListener(v -> startMainActivity());

            // Next button
            nextButton.setOnClickListener(v -> {
                if (viewPager.getCurrentItem() < adapter.getItemCount() - 1) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
                } else {
                    startMainActivity();
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
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            startMainActivity();
        }
    }

    private void updateStepper(int position) {
        // Reset all steps to inactive
        s1.setBackgroundResource(R.drawable.bg_step_inactive); s1.setTextColor(getColor(R.color.ef_text_tertiary));
        s2.setBackgroundResource(R.drawable.bg_step_inactive); s2.setTextColor(getColor(R.color.ef_text_tertiary));
        s3.setBackgroundResource(R.drawable.bg_step_inactive); s3.setTextColor(getColor(R.color.ef_text_tertiary));
        s4.setBackgroundResource(R.drawable.bg_step_inactive); s4.setTextColor(getColor(R.color.ef_text_tertiary));
        
        l1.setBackgroundColor(getColor(R.color.ef_outline));
        l2.setBackgroundColor(getColor(R.color.ef_outline));
        l3.setBackgroundColor(getColor(R.color.ef_outline));

        // Highlight active and previous steps
        if (position >= 0) {
            s1.setBackgroundResource(R.drawable.bg_step_active); s1.setTextColor(getColor(R.color.white));
        }
        if (position >= 1) {
            l1.setBackgroundColor(getColor(R.color.ef_primary));
            s2.setBackgroundResource(R.drawable.bg_step_active); s2.setTextColor(getColor(R.color.white));
        }
        if (position >= 2) {
            l2.setBackgroundColor(getColor(R.color.ef_primary));
            s3.setBackgroundResource(R.drawable.bg_step_active); s3.setTextColor(getColor(R.color.white));
        }
        if (position >= 3) {
            l3.setBackgroundColor(getColor(R.color.ef_primary));
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
