package com.example.englishflow.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.LocalAuthStore;
import com.example.englishflow.database.entity.LocalUserEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends AppCompatActivity {

    private static final String TERMS_URL = "https://example.com/terms";
    private static final String PRIVACY_URL = "https://example.com/privacy";

    private LocalAuthStore localAuthStore;
    private AppRepository repository;
    private AppSettingsStore settingsStore;

    private ImageView avatarPreview;
    private EditText inputDisplayName;
    private EditText inputProfileEmail;

    private String selectedAvatarKey = AppSettingsStore.AVATAR_DEFAULT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        localAuthStore = new LocalAuthStore(this);
        repository = AppRepository.getInstance(this);
        settingsStore = new AppSettingsStore(this);

        bindViews();
        setupInsets();
        bindCurrentSettings();
        setupActions();
    }

    private void bindViews() {
        avatarPreview = findViewById(R.id.settingsAvatarPreview);
        inputDisplayName = findViewById(R.id.inputDisplayName);
        inputProfileEmail = findViewById(R.id.inputProfileEmail);
    }

    private void setupInsets() {
        View topBar = findViewById(R.id.settingsTopBar);
        if (topBar != null) {
            final int initialTop = topBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), initialTop + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            ViewCompat.requestApplyInsets(topBar);
        }

        View scroll = findViewById(R.id.settingsScroll);
        if (scroll != null) {
            final int initialBottom = scroll.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottom + systemBars.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(scroll);
        }
    }

    private void bindCurrentSettings() {
        LocalUserEntity currentUser = localAuthStore.getCurrentUser();
        String fallbackName = currentUser != null ? currentUser.displayName : repository.getUserName();
        String fallbackEmail = currentUser != null ? currentUser.email : "";

        inputDisplayName.setText(fallbackName);
        inputProfileEmail.setText(settingsStore.getProfileEmail(fallbackEmail));

        selectedAvatarKey = settingsStore.getAvatarKey();
        avatarPreview.setImageResource(AppSettingsStore.avatarResFromKey(selectedAvatarKey));

        // Daily Goal
        int goal = settingsStore.getDailyGoalMinutes();
        MaterialButton btnRelax = findViewById(R.id.btnGoalRelax);
        MaterialButton btnFocused = findViewById(R.id.btnGoalFocused);
        MaterialButton btnTryHard = findViewById(R.id.btnGoalTryHard);

        if (btnRelax != null && btnFocused != null && btnTryHard != null) {
            btnRelax.setChecked(goal == AppSettingsStore.DAILY_GOAL_RELAX);
            btnFocused.setChecked(goal == AppSettingsStore.DAILY_GOAL_FOCUSED);
            btnTryHard.setChecked(goal == AppSettingsStore.DAILY_GOAL_TRY_HARD);
        }

        // Voice Speed
        String voiceSpeed = settingsStore.getVoiceSpeedMode();
        MaterialButton btnSlow = findViewById(R.id.btnVoiceSlow);
        MaterialButton btnNormal = findViewById(R.id.btnVoiceNormal);

        if (btnSlow != null && btnNormal != null) {
            btnSlow.setChecked(AppSettingsStore.VOICE_MODE_SLOW.equals(voiceSpeed));
            btnNormal.setChecked(AppSettingsStore.VOICE_MODE_NORMAL.equals(voiceSpeed));
        }
    }

    private void setupActions() {
        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());
        findViewById(R.id.btnChooseAvatar).setOnClickListener(v -> showAvatarPicker());
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfileInfo());

        // Goal selection
        MaterialButton btnRelax = findViewById(R.id.btnGoalRelax);
        MaterialButton btnFocused = findViewById(R.id.btnGoalFocused);
        MaterialButton btnTryHard = findViewById(R.id.btnGoalTryHard);

        View.OnClickListener goalListener = v -> {
            int id = v.getId();
            btnRelax.setChecked(id == R.id.btnGoalRelax);
            btnFocused.setChecked(id == R.id.btnGoalFocused);
            btnTryHard.setChecked(id == R.id.btnGoalTryHard);

            if (id == R.id.btnGoalRelax) {
                settingsStore.setDailyGoalMinutes(AppSettingsStore.DAILY_GOAL_RELAX);
            } else if (id == R.id.btnGoalTryHard) {
                settingsStore.setDailyGoalMinutes(AppSettingsStore.DAILY_GOAL_TRY_HARD);
            } else {
                settingsStore.setDailyGoalMinutes(AppSettingsStore.DAILY_GOAL_FOCUSED);
            }
        };

        if (btnRelax != null) btnRelax.setOnClickListener(goalListener);
        if (btnFocused != null) btnFocused.setOnClickListener(goalListener);
        if (btnTryHard != null) btnTryHard.setOnClickListener(goalListener);

        // Voice speed selection
        MaterialButton btnVoiceSlow = findViewById(R.id.btnVoiceSlow);
        MaterialButton btnVoiceNormal = findViewById(R.id.btnVoiceNormal);

        View.OnClickListener voiceListener = v -> {
            int id = v.getId();
            btnVoiceSlow.setChecked(id == R.id.btnVoiceSlow);
            btnVoiceNormal.setChecked(id == R.id.btnVoiceNormal);

            if (id == R.id.btnVoiceSlow) {
                settingsStore.setVoiceSpeedMode(AppSettingsStore.VOICE_MODE_SLOW);
            } else {
                settingsStore.setVoiceSpeedMode(AppSettingsStore.VOICE_MODE_NORMAL);
            }
        };

        if (btnVoiceSlow != null) btnVoiceSlow.setOnClickListener(voiceListener);
        if (btnVoiceNormal != null) btnVoiceNormal.setOnClickListener(voiceListener);

        findViewById(R.id.btnFeedback).setOnClickListener(v -> sendFeedbackEmail());
        findViewById(R.id.btnRateApp).setOnClickListener(v -> openAppRating());
        findViewById(R.id.btnTerms).setOnClickListener(v -> openUrl(TERMS_URL));
        findViewById(R.id.btnPrivacy).setOnClickListener(v -> openUrl(PRIVACY_URL));
    }

    private void showAvatarPicker() {
        String[] labels = new String[] {"Mặc định", "Cá heo", "Tốt nghiệp"};
        String[] keys = new String[] {
                AppSettingsStore.AVATAR_DEFAULT,
                AppSettingsStore.AVATAR_DOLPHIN,
                AppSettingsStore.AVATAR_GRADUATE
        };

        int checkedIndex = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(selectedAvatarKey)) {
                checkedIndex = i;
                break;
            }
        }

        final int[] selectedIndex = {checkedIndex};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Chọn ảnh đại diện")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton("Áp dụng", (dialog, which) -> {
                    selectedAvatarKey = keys[selectedIndex[0]];
                    settingsStore.setAvatarKey(selectedAvatarKey);
                    avatarPreview.setImageResource(AppSettingsStore.avatarResFromKey(selectedAvatarKey));
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void saveProfileInfo() {
        String name = inputDisplayName.getText() != null ? inputDisplayName.getText().toString().trim() : "";
        String email = inputProfileEmail.getText() != null ? inputProfileEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Tên không được để trống", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!TextUtils.isEmpty(email) && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        repository.setUserName(name);
        localAuthStore.updateCurrentDisplayName(name);
        settingsStore.setProfileEmail(email);
        settingsStore.setAvatarKey(selectedAvatarKey);

        Toast.makeText(this, "Đã lưu cài đặt hồ sơ", Toast.LENGTH_SHORT).show();
    }

    private void sendFeedbackEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@englishflow.app"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "[EnglishFlow] Phản hồi / Báo lỗi");
        intent.putExtra(Intent.EXTRA_TEXT, "Mô tả vấn đề hoặc góp ý của bạn tại đây...");

        try {
            startActivity(Intent.createChooser(intent, "Chọn ứng dụng email"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Không tìm thấy ứng dụng email", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppRating() {
        String packageName = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
        } catch (ActivityNotFoundException e) {
            openUrl("https://play.google.com/store/apps/details?id=" + packageName);
        }
    }

    private void openUrl(@NonNull String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
        }
    }
}
