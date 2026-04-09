package com.example.englishflow;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.admin.AdminDashboardActivity;
import com.example.englishflow.data.FirebaseUserStore;
import com.example.englishflow.ui.MainPagerAdapter;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private boolean isKeyboardVisible;
    private int currentNavPosition = -1;

    public void setCurrentTab(int index) {
        smoothScrollTo(index);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        new FirebaseUserStore().fetchProfile(uid, profile -> {
            if (profile == null) {
                return;
            }
            if (profile.isLocked()) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new android.content.Intent(this, LoginActivity.class));
                finish();
                return;
            }
            if (profile.isAdmin() || com.example.englishflow.data.FirebaseSeeder.isAdminEmail(profile.email)) {
                startActivity(new android.content.Intent(this, AdminDashboardActivity.class));
                finish();
            }
        });
        try {
            setContentView(R.layout.activity_main);

            viewPager = findViewById(R.id.viewPager);
            if (viewPager == null) {
                throw new RuntimeException("ViewPager not found");
            }

            MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
            viewPager.setAdapter(pagerAdapter);
            // Keep one extra page on each side warm to reduce recreation stutter when switching tabs.
            viewPager.setOffscreenPageLimit(2);

            // ════ Custom 3D Nav Setup ════
            android.view.View btnHome = findViewById(R.id.nav_home_btn);
            android.view.View btnLearn = findViewById(R.id.nav_learn_btn);
            android.view.View btnChat = findViewById(R.id.nav_chat_btn);
            android.view.View btnProfile = findViewById(R.id.nav_profile_btn);

            btnHome.setOnClickListener(v -> smoothScrollTo(0));
            btnLearn.setOnClickListener(v -> smoothScrollTo(1));
            btnChat.setOnClickListener(v -> smoothScrollTo(3));
            btnProfile.setOnClickListener(v -> smoothScrollTo(4));

            findViewById(R.id.fabScan).setOnClickListener(v -> {
                smoothScrollTo(2);
            });

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    updateNavUI(position);
                }
            });

            // Initial UI state
            updateNavUI(0);

            // ══ Keyboard Visibility Handling ══
            handleKeyboardVisibility();

            // ══ Window Insets Handling (Edge-to-Edge) ══
            View bottomAppBar = findViewById(R.id.bottomAppBar);
            if (bottomAppBar != null) {
                ViewCompat.setOnApplyWindowInsetsListener(bottomAppBar, (nav, navInsets) -> {
                    Insets navSystemBars = navInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    nav.setPadding(0, 0, 0, navSystemBars.bottom);
                    return navInsets;
                });
            }

            View root = findViewById(R.id.mainRoot);
            if (root != null) {
                ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                    Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                    boolean keyboardNowVisible = imeInsets.bottom > 0;
                    if (keyboardNowVisible != isKeyboardVisible) {
                        isKeyboardVisible = keyboardNowVisible;
                        applyKeyboardState(isKeyboardVisible);
                    }
                    return windowInsets;
                });
                ViewCompat.requestApplyInsets(root);
            }

        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }

    private void handleKeyboardVisibility() {
        applyKeyboardState(false);
    }

    private void applyKeyboardState(boolean keyboardVisible) {
        final android.view.View navBar = findViewById(R.id.bottomAppBar);
        final android.view.View fab = findViewById(R.id.fabScan);
        if (navBar == null || fab == null) {
            return;
        }

        int targetVisibility = keyboardVisible ? android.view.View.GONE : android.view.View.VISIBLE;
        if (navBar.getVisibility() != targetVisibility) {
            navBar.setVisibility(targetVisibility);
        }
        if (fab.getVisibility() != targetVisibility) {
            fab.setVisibility(targetVisibility);
        }
    }

    private void smoothScrollTo(int target) {
        if (viewPager == null) return;
        int current = viewPager.getCurrentItem();
        if (current == target) {
            return;
        }
        if (Math.abs(target - current) <= 1) {
            viewPager.setCurrentItem(target, true);
        } else {
            // Jump directly to the target page without scrolling through intermediate pages
            viewPager.setCurrentItem(target, false);
        }
    }

    private void updateNavUI(int position) {
        if (position == currentNavPosition) {
            return;
        }
        currentNavPosition = position;

        com.google.android.material.card.MaterialCardView[] buttons = {
                findViewById(R.id.nav_home_btn),
                findViewById(R.id.nav_learn_btn),
                null, // FAB
                findViewById(R.id.nav_chat_btn),
                findViewById(R.id.nav_profile_btn)
        };
        android.view.View[] icons = {
                findViewById(R.id.nav_home_icon),
                findViewById(R.id.nav_learn_icon),
                null,
                findViewById(R.id.nav_chat_icon),
                findViewById(R.id.nav_profile_icon)
        };

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;
            boolean isActive = (i == position);
            buttons[i].setSelected(isActive);
            buttons[i].animate().cancel();
            buttons[i].setTranslationY(isActive ? -3f * density : 0f);
            buttons[i].setCardElevation(isActive ? 4f * density : 0f);
            if (icons[i] != null) {
                icons[i].animate().cancel();
                icons[i].setScaleX(isActive ? 1.12f : 1.0f);
                icons[i].setScaleY(isActive ? 1.12f : 1.0f);
            }
        }
        
        android.view.View fab = findViewById(R.id.fabScan);
        if (fab != null) {
            boolean scanSelected = position == 2;
            fab.animate().cancel();
            fab.setScaleX(scanSelected ? 1.12f : 1.0f);
            fab.setScaleY(scanSelected ? 1.12f : 1.0f);
            fab.setTranslationY(scanSelected ? -4f * density : 0f);
        }
    }
}