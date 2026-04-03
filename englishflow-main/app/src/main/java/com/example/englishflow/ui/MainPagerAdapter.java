package com.example.englishflow.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.englishflow.ui.fragments.ChatFragment;
import com.example.englishflow.ui.fragments.HomeFragment;
import com.example.englishflow.ui.fragments.LearnFragment;
import com.example.englishflow.ui.fragments.ProfileFragment;
import com.example.englishflow.ui.fragments.ScanFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new HomeFragment();
            case 1:
                return new LearnFragment();
            case 2:
                return new ScanFragment();
            case 3:
                return new ChatFragment();
            case 4:
            default:
                return new ProfileFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5;
    }
}
