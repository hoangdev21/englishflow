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
    private TabLayout tabLayout;
    private MaterialButton skipButton;
    private MaterialButton nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        try {
            viewPager = findViewById(R.id.onboardingViewPager);
            tabLayout = findViewById(R.id.onboardingTabs);
            skipButton = findViewById(R.id.btnSkip);
            nextButton = findViewById(R.id.btnNext);

            if (viewPager == null || tabLayout == null || skipButton == null || nextButton == null) {
                startMainActivity();
                return;
            }

            // Setup ViewPager with adapter
            OnboardingPagerAdapter adapter = new OnboardingPagerAdapter(this);
            viewPager.setAdapter(adapter);

            // Connect TabLayout with ViewPager
            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

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

    private void startMainActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
