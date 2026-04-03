package com.example.englishflow;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.data.LocalAuthStore;
import com.example.englishflow.ui.MainPagerAdapter;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;

    public void setCurrentTab(int index) {
        smoothScrollTo(index);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (!new LocalAuthStore(getApplicationContext()).hasActiveSession()) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }
        try {
            setContentView(R.layout.activity_main);

            viewPager = findViewById(R.id.viewPager);
            if (viewPager == null) {
                throw new RuntimeException("ViewPager not found");
            }

            MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
            viewPager.setAdapter(pagerAdapter);
            viewPager.setOffscreenPageLimit(5);

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
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot), (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // Keep the bottom nav above the system navigation bar
                ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomAppBar), (nav, navInsets) -> {
                    Insets navSystemBars = navInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    nav.setPadding(0, 0, 0, navSystemBars.bottom);
                    return navInsets;
                });

                return windowInsets;
            });

        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }

    private void handleKeyboardVisibility() {
        final android.view.View rootView = findViewById(R.id.mainRoot);
        final android.view.View navBar = findViewById(R.id.bottomAppBar);
        final android.view.View fab = findViewById(R.id.fabScan);

        if (rootView == null || navBar == null || fab == null) return;

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            // Threshold: if keypad takes up more than 15% of the screen
            if (keypadHeight > screenHeight * 0.15) {
                if (navBar.getVisibility() == android.view.View.VISIBLE) {
                    navBar.setVisibility(android.view.View.GONE);
                    fab.setVisibility(android.view.View.GONE);
                    if (viewPager != null && viewPager.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                        android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
                        params.bottomMargin = 0;
                        viewPager.requestLayout();
                    }
                }
            } else {
                if (navBar.getVisibility() == android.view.View.GONE) {
                    navBar.setVisibility(android.view.View.VISIBLE);
                    fab.setVisibility(android.view.View.VISIBLE);
                    if (viewPager != null && viewPager.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                        android.view.ViewGroup.MarginLayoutParams params = (android.view.ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
                        params.bottomMargin = 0;
                        viewPager.requestLayout();
                    }
                }
            }
        });
    }

    private void smoothScrollTo(int target) {
        if (viewPager == null) return;
        int current = viewPager.getCurrentItem();
        if (Math.abs(target - current) <= 1) {
            viewPager.setCurrentItem(target, true);
        } else {
            // Jump directly to the target page without scrolling through intermediate pages
            viewPager.setCurrentItem(target, false);
        }
    }

    private void updateNavUI(int position) {
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

            if (isActive) {
                // Pop up animation
                buttons[i].animate()
                        .translationY(-4 * density)
                        .setDuration(250)
                        .start();
                buttons[i].setCardElevation(6 * density);
                if (icons[i] != null) {
                    icons[i].animate().scaleX(1.25f).scaleY(1.25f).setDuration(250).start();
                }
            } else {
                // Back to normal
                buttons[i].animate()
                        .translationY(0)
                        .setDuration(200)
                        .start();
                buttons[i].setCardElevation(0);
                if (icons[i] != null) {
                    icons[i].animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                }
            }
        }
        
        // Handle FAB state if needed (always glossy)
        android.view.View fab = findViewById(R.id.fabScan);
        if (fab != null) {
            fab.animate()
                .scaleX(position == 2 ? 1.2f : 1.0f)
                .scaleY(position == 2 ? 1.2f : 1.0f)
                .translationY(position == 2 ? -6 * density : 0)
                .setDuration(300).start();
        }
    }
}