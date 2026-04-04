package com.example.englishflow.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.LocalAuthStore;
import com.example.englishflow.database.entity.LocalUserEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.core.content.ContextCompat;

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

        if (inputDisplayName != null) {
            inputDisplayName.setText(fallbackName);
        }
        if (inputProfileEmail != null) {
            inputProfileEmail.setText(settingsStore.getProfileEmail(fallbackEmail));
        }

        selectedAvatarKey = settingsStore.getAvatarKey();
        if (avatarPreview != null) {
            Glide.with(this)
                 .load(AppSettingsStore.avatarResFromKey(selectedAvatarKey))
                 .into(avatarPreview);
        }

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
        View backButton = findViewById(R.id.btnBackSettings);
        View chooseAvatarButton = findViewById(R.id.btnChooseAvatar);
        View saveProfileButton = findViewById(R.id.btnSaveProfile);

        if (backButton != null) backButton.setOnClickListener(v -> finish());
        if (chooseAvatarButton != null) {
            chooseAvatarButton.setOnClickListener(v -> {
                showAvatarPicker();
            });
        }
        if (avatarPreview != null) {
            avatarPreview.setOnClickListener(v -> showAvatarPicker());
        }
        if (saveProfileButton != null) saveProfileButton.setOnClickListener(v -> saveProfileInfo());

        // Goal selection
        MaterialButton btnRelax = findViewById(R.id.btnGoalRelax);
        MaterialButton btnFocused = findViewById(R.id.btnGoalFocused);
        MaterialButton btnTryHard = findViewById(R.id.btnGoalTryHard);

        View.OnClickListener goalListener = v -> {
            int id = v.getId();
            if (btnRelax != null) btnRelax.setChecked(id == R.id.btnGoalRelax);
            if (btnFocused != null) btnFocused.setChecked(id == R.id.btnGoalFocused);
            if (btnTryHard != null) btnTryHard.setChecked(id == R.id.btnGoalTryHard);

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
            if (btnVoiceSlow != null) btnVoiceSlow.setChecked(id == R.id.btnVoiceSlow);
            if (btnVoiceNormal != null) btnVoiceNormal.setChecked(id == R.id.btnVoiceNormal);

            if (id == R.id.btnVoiceSlow) {
                settingsStore.setVoiceSpeedMode(AppSettingsStore.VOICE_MODE_SLOW);
            } else {
                settingsStore.setVoiceSpeedMode(AppSettingsStore.VOICE_MODE_NORMAL);
            }
        };

        if (btnVoiceSlow != null) btnVoiceSlow.setOnClickListener(voiceListener);
        if (btnVoiceNormal != null) btnVoiceNormal.setOnClickListener(voiceListener);

        View feedbackButton = findViewById(R.id.btnFeedback);
        View rateButton = findViewById(R.id.btnRateApp);
        View termsButton = findViewById(R.id.btnTerms);
        View privacyButton = findViewById(R.id.btnPrivacy);

        if (feedbackButton != null) feedbackButton.setOnClickListener(v -> sendFeedbackEmail());
        if (rateButton != null) rateButton.setOnClickListener(v -> openAppRating());
        if (termsButton != null) termsButton.setOnClickListener(v -> openUrl(TERMS_URL));
        if (privacyButton != null) privacyButton.setOnClickListener(v -> openUrl(PRIVACY_URL));
    }

    private void showAvatarPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.dialog_avatar_picker, null);
        RecyclerView rv = v.findViewById(R.id.rvAvatarGrid);

        if (rv == null) {
            Toast.makeText(this, "Không thể mở danh sách ảnh đại diện", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dùng 3 cột để ảnh hiển thị to rõ hơn
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setHasFixedSize(true);

        String[] keys = new String[]{
                AppSettingsStore.AVATAR_DEFAULT, AppSettingsStore.AVATAR_DOLPHIN, AppSettingsStore.AVATAR_GRADUATE,
                AppSettingsStore.AVATAR_CA, AppSettingsStore.AVATAR_CAHEOBA, AppSettingsStore.AVATAR_CAHOACHA,
                AppSettingsStore.AVATAR_CAHEOCON, AppSettingsStore.AVATAR_CAHEOCONN, AppSettingsStore.AVATAR_CAHEOONG,
                AppSettingsStore.AVATAR_CAHEOSOSINH, AppSettingsStore.AVATAR_CHO, AppSettingsStore.AVATAR_GA,
                AppSettingsStore.AVATAR_HO, AppSettingsStore.AVATAR_HUOU, AppSettingsStore.AVATAR_KHI,
                AppSettingsStore.AVATAR_KYSI, AppSettingsStore.AVATAR_NATA, AppSettingsStore.AVATAR_NGOKHONG,
                AppSettingsStore.AVATAR_RAN, AppSettingsStore.AVATAR_RONG
        };

        rv.setAdapter(new RecyclerView.Adapter<AvatarViewHolder>() {
            @NonNull
            @Override
            public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View item = getLayoutInflater().inflate(R.layout.item_avatar_grid, parent, false);
                return new AvatarViewHolder(item);
            }

            @Override
            public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
                String key = keys[position];
                
                // Dùng Glide để load ảnh siêu nhỏ (Thumbnail) giúp máy ảo không bị treo
                Glide.with(holder.itemView.getContext())
                     .load(AppSettingsStore.avatarResFromKey(key))
                     .override(150, 150) 
                     .centerCrop()
                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                     .into(holder.img);

                if (key.equals(selectedAvatarKey)) {
                    holder.img.setStrokeWidth(6f);
                    holder.img.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ef_primary)));
                } else {
                    holder.img.setStrokeWidth(0f);
                }

                holder.itemView.setOnClickListener(view -> {
                    selectedAvatarKey = key;
                    settingsStore.setAvatarKey(key);
                    if (avatarPreview != null) {
                        Glide.with(SettingsActivity.this)
                             .load(AppSettingsStore.avatarResFromKey(key))
                             .into(avatarPreview);
                    }
                    dialog.dismiss();
                    Toast.makeText(SettingsActivity.this, "Đã đổi ảnh đại diện", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public int getItemCount() {
                return keys.length;
            }
        });

        dialog.setContentView(v);
        dialog.show();
    }

    static class AvatarViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView img;

        AvatarViewHolder(View v) {
            super(v);
            img = v.findViewById(R.id.imgAvatarItem);
        }
    }

    private void saveProfileInfo() {
        String name = inputDisplayName != null && inputDisplayName.getText() != null
                ? inputDisplayName.getText().toString().trim() : "";
        String email = inputProfileEmail != null && inputProfileEmail.getText() != null
                ? inputProfileEmail.getText().toString().trim() : "";

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
