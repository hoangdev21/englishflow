package com.example.englishflow.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.englishflow.ui.fragments.OnboardingPageFragment;

public class OnboardingPagerAdapter extends FragmentStateAdapter {

    private static final int PAGE_COUNT = 4;
    private final String[] titles = {
        "Học từ vựng theo chủ đề",
        "Scan ảnh để học từ",
        "Chat AI luyện nói",
        "Bắt đầu hành trình"
    };
    private final String[] descriptions = {
        "Học từ vựng được sắp xếp theo các chủ đề thực tế như ẩm thực, du lịch, công việc...",
        "Chụp ảnh hoặc chọn từ thư viện, AI sẽ giúp bạn học từ vựng liên quan.",
        "Trò chuyện với AI để luyện nói, cải thiện phát âm và tự tin giao tiếp.",
        "Sẵn sàng nâng trình tiếng Anh hàng ngày? Hãy bắt đầu ngay!"
    };
    private final int[] emojis = {
        0x1F4DA, // 📚
        0x1F4F7, // 📷
        0x1F4AC, // 💬
        0x1F389  // 🎉
    };

    public OnboardingPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return OnboardingPageFragment.newInstance(
            String.valueOf(Character.toChars(emojis[position])[0]),
            titles[position],
            descriptions[position],
            position
        );
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }
}
