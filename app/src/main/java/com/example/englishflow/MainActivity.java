package com.example.englishflow;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.ui.MainPagerAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);

            viewPager = findViewById(R.id.viewPager);
            bottomNavigationView = findViewById(R.id.bottomNav);

            if (viewPager == null || bottomNavigationView == null) {
                throw new RuntimeException("Layout views not found");
            }

            MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
            viewPager.setAdapter(pagerAdapter);
            viewPager.setOffscreenPageLimit(5);

            bottomNavigationView.setOnItemSelectedListener(item -> {
                if (item.getItemId() == R.id.nav_home) {
                    viewPager.setCurrentItem(0, true);
                    return true;
                }
                if (item.getItemId() == R.id.nav_learn) {
                    viewPager.setCurrentItem(1, true);
                    return true;
                }
                if (item.getItemId() == R.id.nav_scan) {
                    viewPager.setCurrentItem(2, true);
                    return true;
                }
                if (item.getItemId() == R.id.nav_chat) {
                    viewPager.setCurrentItem(3, true);
                    return true;
                }
                if (item.getItemId() == R.id.nav_profile) {
                    viewPager.setCurrentItem(4, true);
                    return true;
                }
                return false;
            });

            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    try {
                        switch (position) {
                            case 0:
                                bottomNavigationView.setSelectedItemId(R.id.nav_home);
                                break;
                            case 1:
                                bottomNavigationView.setSelectedItemId(R.id.nav_learn);
                                break;
                            case 2:
                                bottomNavigationView.setSelectedItemId(R.id.nav_scan);
                                break;
                            case 3:
                                bottomNavigationView.setSelectedItemId(R.id.nav_chat);
                                break;
                            case 4:
                                bottomNavigationView.setSelectedItemId(R.id.nav_profile);
                                break;
                            default:
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }
}