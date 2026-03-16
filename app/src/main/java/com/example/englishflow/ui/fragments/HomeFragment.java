package com.example.englishflow.ui.fragments;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.WordEntry;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private AppRepository repository;
    private TextToSpeech textToSpeech;
    private TextView reminderText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize repository safely
        try {
            repository = AppRepository.getInstance(requireContext());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi khởi tạo ứng dụng", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Set up all views with safe defaults first
            setupBasicViews(view);
            
            // Load data in background thread
            loadDataAsync(view);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi tải dữ liệu trang chủ", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBasicViews(View view) {
        try {
            // Premium header - always works
            TextView greetingText = view.findViewById(R.id.txtGreeting);
            TextView greetingEmoji = view.findViewById(R.id.greetingEmoji);
            TextView currentTimeText = view.findViewById(R.id.currentTime);
            TextView dayOfWeekText = view.findViewById(R.id.dayOfWeek);
            
            if (greetingText != null) greetingText.setText("Xin chào");
            if (greetingEmoji != null) greetingEmoji.setText("👋");
            if (currentTimeText != null) currentTimeText.setText("00:00");
            if (dayOfWeekText != null) dayOfWeekText.setText("Hôm nay");
            
            // Default stats
            setDefaultStats(view);
            
            // Set up quick action buttons
            setupQuickActionButtons(view);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupQuickActionButtons(View view) {
        try {
            MaterialButton quickLearnBtn = view.findViewById(R.id.btnQuickLearn);
            MaterialButton quickScanBtn = view.findViewById(R.id.btnQuickScan);
            MaterialButton quickChatBtn = view.findViewById(R.id.btnQuickChat);
            MaterialButton quickDictionaryBtn = view.findViewById(R.id.btnQuickDictionary);
            
            if (quickLearnBtn != null) quickLearnBtn.setOnClickListener(v -> navigateToTab(1));
            if (quickScanBtn != null) quickScanBtn.setOnClickListener(v -> navigateToTab(2));
            if (quickChatBtn != null) quickChatBtn.setOnClickListener(v -> navigateToTab(3));
            if (quickDictionaryBtn != null) quickDictionaryBtn.setOnClickListener(v -> navigateToTab(4));
            
            // Continue Learning button
            MaterialButton continueBtn = view.findViewById(R.id.btnContinue);
            if (continueBtn != null) continueBtn.setOnClickListener(v -> navigateToTab(1));
            
            // Word of the Day buttons
            MaterialButton pronounceBtn = view.findViewById(R.id.btnPronounceWord);
            MaterialButton saveWordBtn = view.findViewById(R.id.btnSaveWord);
            
            textToSpeech = new TextToSpeech(requireContext(), status -> {
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.US);
                }
            });
            
            if (pronounceBtn != null) {
                pronounceBtn.setOnClickListener(v -> {
                    if (textToSpeech != null) {
                        textToSpeech.speak("hello", TextToSpeech.QUEUE_FLUSH, null, "home-word");
                    }
                });
            }
            
            if (saveWordBtn != null) {
                saveWordBtn.setOnClickListener(v -> {
                    Toast.makeText(requireContext(), "Đã lưu từ vào từ điển cá nhân", Toast.LENGTH_SHORT).show();
                });
            }
            
            // Set Reminder button
            reminderText = view.findViewById(R.id.txtReminder);
            MaterialButton setReminderBtn = view.findViewById(R.id.btnSetReminder);
            if (setReminderBtn != null && reminderText != null) {
                setReminderBtn.setOnClickListener(v -> {
                    int currentHour = repository.getReminderHour();
                    int currentMinute = repository.getReminderMinute();
                    TimePickerDialog dialog = new TimePickerDialog(requireContext(), (timePicker, selectedHour, selectedMinute) -> {
                        repository.setReminderTime(selectedHour, selectedMinute);
                        renderReminderText();
                    }, currentHour, currentMinute, true);
                    dialog.show();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setDefaultStats(View view) {
        try {
            TextView learnedCountText = view.findViewById(R.id.txtLearnedCount);
            TextView bestStreakText = view.findViewById(R.id.txtBestStreakCount);
            TextView currentStreakText = view.findViewById(R.id.txtCurrentStreak);
            TextView scannedCountText = view.findViewById(R.id.txtScannedCount);
            TextView xpText = view.findViewById(R.id.txtXp);
            TextView xpPercentageText = view.findViewById(R.id.txtXpPercentage);
            TextView weeklyTotalText = view.findViewById(R.id.txtWeeklyTotal);
            TextView unlearnedCountText = view.findViewById(R.id.txtUnlearnedCount);
            TextView cefrLevelText = view.findViewById(R.id.txtCefrLevel);
            
            if (learnedCountText != null) learnedCountText.setText("0");
            if (bestStreakText != null) bestStreakText.setText("0");
            if (currentStreakText != null) currentStreakText.setText("0");
            if (scannedCountText != null) scannedCountText.setText("0");
            if (xpText != null) xpText.setText("XP hôm nay: 0/120");
            if (xpPercentageText != null) xpPercentageText.setText("0%");
            if (weeklyTotalText != null) weeklyTotalText.setText("0 phút");
            if (unlearnedCountText != null) unlearnedCountText.setText("3000 từ");
            if (cefrLevelText != null) cefrLevelText.setText("A1 - Sơ cấp");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadDataAsync(View view) {
        // Load data in background to prevent main thread blocking
        new Thread(() -> {
            try {
                loadRealData(view);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadRealData(View view) {
        try {
            if (!isAdded()) return;
            
            // Get all data from repository
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            
            String emoji = getTimeEmoji(hour);
            String greeting = buildGreeting(repository.getUserName());
            
            int learnedWords = repository.getLearnedWords();
            int streak = repository.getStreakDays();
            int bestStreak = repository.getBestStreak();
            int scanned = repository.getScannedImages();
            int xpToday = repository.getXpToday();
            int xpGoal = repository.getXpGoal();
            List<Integer> weeklyMinutes = repository.getWeeklyStudyMinutes();
            int weeklyTotal = weeklyMinutes != null ? weeklyMinutes.stream().mapToInt(Integer::intValue).sum() : 0;
            String cefrLevel = repository.getCefrLevel();
            
            // Update UI on main thread
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> updateUIWithData(view, emoji, greeting, hour, minute, 
                    learnedWords, streak, bestStreak, scanned, xpToday, xpGoal, weeklyTotal, cefrLevel));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUIWithData(View view, String emoji, String greeting, int hour, int minute,
                                   int learnedWords, int streak, int bestStreak, int scanned,
                                   int xpToday, int xpGoal, int weeklyTotal, String cefrLevel) {
        try {
            TextView greetingText = view.findViewById(R.id.txtGreeting);
            TextView greetingEmoji = view.findViewById(R.id.greetingEmoji);
            TextView currentTimeText = view.findViewById(R.id.currentTime);
            TextView dayOfWeekText = view.findViewById(R.id.dayOfWeek);
            TextView learnedCountText = view.findViewById(R.id.txtLearnedCount);
            TextView bestStreakText = view.findViewById(R.id.txtBestStreakCount);
            TextView currentStreakText = view.findViewById(R.id.txtCurrentStreak);
            TextView scannedCountText = view.findViewById(R.id.txtScannedCount);
            TextView xpText = view.findViewById(R.id.txtXp);
            TextView xpPercentageText = view.findViewById(R.id.txtXpPercentage);
            LinearProgressIndicator xpProgress = view.findViewById(R.id.xpProgress);
            TextView weeklyTotalText = view.findViewById(R.id.txtWeeklyTotal);
            TextView cefrLevelText = view.findViewById(R.id.txtCefrLevel);
            
            if (greetingText != null) greetingText.setText(greeting);
            if (greetingEmoji != null) greetingEmoji.setText(emoji);
            if (currentTimeText != null) currentTimeText.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute));
            
            String[] dayNames = {"Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"};
            if (dayOfWeekText != null) dayOfWeekText.setText(dayNames[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]);
            
            if (learnedCountText != null) learnedCountText.setText(String.valueOf(learnedWords));
            if (bestStreakText != null) bestStreakText.setText(String.valueOf(bestStreak));
            if (currentStreakText != null) currentStreakText.setText(String.valueOf(streak));
            if (scannedCountText != null) scannedCountText.setText(String.valueOf(scanned));
            
            if (xpText != null) xpText.setText("XP hôm nay: " + xpToday + "/" + xpGoal);
            int xpPercent = (int) (((float) xpToday / (float) xpGoal) * 100f);
            if (xpPercentageText != null) xpPercentageText.setText(Math.min(xpPercent, 100) + "%");
            if (xpProgress != null) xpProgress.setProgressCompat(Math.min(xpPercent, 100), true);
            
            if (weeklyTotalText != null) weeklyTotalText.setText(weeklyTotal + " phút");
            if (cefrLevelText != null) cefrLevelText.setText(cefrLevel + " - " + cefrLevelToFullName(cefrLevel));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    private String buildGreeting(String name) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Chào buổi sáng";
        } else if (hour < 18) {
            greeting = "Chào buổi chiều";
        } else {
            greeting = "Chào buổi tối";
        }
        return greeting + ", " + name + "!";
    }

    private String getTimeEmoji(int hour) {
        if (hour < 6) return "🌙"; // Midnight
        if (hour < 12) return "🌅"; // Morning
        if (hour < 18) return "☀️"; // Afternoon
        return "🌙"; // Evening
    }

    private void renderReminderText() {
        try {
            if (reminderText != null) {
                reminderText.setText(String.format(Locale.getDefault(), "%02d:%02d", 
                    repository.getReminderHour(), repository.getReminderMinute()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void navigateToTab(int tabIndex) {
        if (getActivity() != null) {
            ViewPager2 viewPager = getActivity().findViewById(R.id.viewPager);
            if (viewPager != null) {
                viewPager.setCurrentItem(tabIndex, true);
            }
        }
    }

    private String cefrLevelToFullName(String level) {
        switch (level) {
            case "A1": return "Sơ cấp 1";
            case "A2": return "Sơ cấp 2";
            case "B1": return "Trung cấp";
            case "B2": return "Trung cấp +";
            case "C1": return "Cao cấp";
            case "C2": return "Thạo nhất";
            default: return "Chưa xác định";
        }
    }

    private String getNextLevel(String currentLevel) {
        switch (currentLevel) {
            case "A1": return "A2";
            case "A2": return "B1";
            case "B1": return "B2";
            case "B2": return "C1";
            case "C1": return "C2";
            default: return "C2";
        }
    }

    private int calculateProgressToNextLevel(String level, int learnedWords) {
        switch (level) {
            case "A1": return Math.max(0, 80 - learnedWords);
            case "A2": return Math.max(0, 160 - learnedWords);
            case "B1": return Math.max(0, 260 - learnedWords);
            case "B2": return Math.max(0, 380 - learnedWords);
            case "C1": return Math.max(0, 520 - learnedWords);
            default: return 0;
        }
    }
}
