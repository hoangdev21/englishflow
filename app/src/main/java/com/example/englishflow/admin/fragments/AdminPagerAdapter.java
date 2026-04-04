package com.example.englishflow.admin.fragments;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class AdminPagerAdapter extends FragmentStateAdapter {

    public AdminPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new AdminOverviewFragment();
            case 1: return new AdminUsersFragment();
            case 2: return new AdminContentFragment();
            case 3: 
            default: return new AdminSettingsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
