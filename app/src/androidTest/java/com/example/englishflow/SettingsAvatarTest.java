package com.example.englishflow;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.englishflow.ui.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsAvatarTest {

    @Test
    public void testAvatarPickerOpens() {
        // Khởi chạy SettingsActivity
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), SettingsActivity.class);
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(intent)) {
            
            // 1. Kiểm tra màn hình Settings đã hiện
            onView(withText("Cài đặt")).check(matches(isDisplayed()));

            // 2. Click vào nút "Đổi ảnh đại diện"
            // Dùng ID của nút bấm trong layout: btnChooseAvatar
            onView(withId(R.id.btnChooseAvatar)).perform(click());

            // 3. Kiểm tra xem Modal (BottomSheet) có hiện ra không
            // Chúng ta kiểm tra tiêu đề "Chọn ảnh đại diện" bên trong modal
            onView(withText("Chọn ảnh đại diện")).check(matches(isDisplayed()));
            
            // 4. Kiểm tra Grid ảnh có được render không
            onView(withId(R.id.rvAvatarGrid)).check(matches(isDisplayed()));
        }
    }
}
