package com.example.englishflow.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.LoginActivity;
import com.example.englishflow.MainActivity;
import com.example.englishflow.R;
import com.example.englishflow.admin.fragments.AdminPagerAdapter;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboardActivity extends AppCompatActivity {

    private final FirebaseUserStore userStore = new FirebaseUserStore();
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goLoginAndFinish();
            return;
        }

        applyWindowInsets();
        setupNavigation();
        setupActions();
        verifyAdminAccess();
    }

    private void applyWindowInsets() {
        View header = findViewById(R.id.adminHeader);
        if (header != null) {
            final int initialTop = header.getPaddingTop();
            final int initialBottom = header.getPaddingBottom();
            final int initialLeft = header.getPaddingLeft();
            final int initialRight = header.getPaddingRight();
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(initialLeft, initialTop + bars.top, initialRight, initialBottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(header);
        }

        View nav = findViewById(R.id.adminBottomNav);
        if (nav != null) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) nav.getLayoutParams();
            final int initialBottomMargin = params.bottomMargin;
            ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                layoutParams.bottomMargin = initialBottomMargin + bars.bottom;
                v.setLayoutParams(layoutParams);
                return insets;
            });
            ViewCompat.requestApplyInsets(nav);
        }
    }

    private void setupNavigation() {
        viewPager = findViewById(R.id.adminViewPager);
        bottomNav = findViewById(R.id.adminBottomNav);

        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);
        viewPager.setUserInputEnabled(false); // Disable swiping to maintain clean navigation

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.admin_menu_dashboard) viewPager.setCurrentItem(0, false);
            else if (id == R.id.admin_menu_users) viewPager.setCurrentItem(1, false);
            else if (id == R.id.admin_menu_content) viewPager.setCurrentItem(2, false);
            else if (id == R.id.admin_menu_settings) viewPager.setCurrentItem(3, false);
            return true;
        });
    }

    private void setupActions() {
        MaterialButton btnBack = findViewById(R.id.adminBtnBackToApp);
        MaterialButton btnLogout = findViewById(R.id.adminBtnLogout);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                goLoginAndFinish();
            });
        }
    }

    private void verifyAdminAccess() {
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Predictively check email first for immediate UI
        if (com.example.englishflow.data.FirebaseSeeder.isAdminEmail(email)) {
            return; // Admin access granted by email
        }

        // Otherwise fetch from Firestore
        userStore.fetchProfile(uid, profile -> {
            if (profile == null || !profile.isAdmin()) {
                Toast.makeText(this, "Bạn không có quyền truy cập Admin", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });
    }

    private void goLoginAndFinish() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
