package com.example.englishflow.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
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
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SettingsActivity extends AppCompatActivity {

    private static final String TERMS_URL = "https://example.com/terms";
    private static final String PRIVACY_URL = "https://example.com/privacy";
    private static final int REQUEST_SETTINGS_NOTIFICATION_PERMISSION = 9201;

    private FirebaseUserStore firebaseUserStore;
    private AppRepository repository;
    private AppSettingsStore settingsStore;

    private ImageView avatarPreview;
    private EditText inputDisplayName;
    private EditText inputProfileEmail;
    private SwitchMaterial switchAdminNotifications;

    private String selectedAvatarKey = AppSettingsStore.AVATAR_DEFAULT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        firebaseUserStore = new FirebaseUserStore();
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
        switchAdminNotifications = findViewById(R.id.switchAdminNotifications);
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
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String fallbackName = currentUser != null && !TextUtils.isEmpty(currentUser.getDisplayName())
            ? currentUser.getDisplayName()
            : repository.getUserName();
        String fallbackEmail = currentUser != null && currentUser.getEmail() != null
            ? currentUser.getEmail()
            : "";

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

        if (switchAdminNotifications != null) {
            switchAdminNotifications.setChecked(settingsStore.isAdminNotificationsEnabled());
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

        if (switchAdminNotifications != null) {
            switchAdminNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                settingsStore.setAdminNotificationsEnabled(isChecked);
                if (isChecked) {
                    ensureNotificationPermissionForSettings();
                }
            });
        }

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
        TextView xpBalanceText = v.findViewById(R.id.tvAvatarXpBalance);

        if (rv == null) {
            Toast.makeText(this, "Không thể mở danh sách ảnh đại diện", Toast.LENGTH_SHORT).show();
            return;
        }

        if (xpBalanceText != null) {
            xpBalanceText.setText(getString(R.string.avatar_picker_balance_loading));
        }

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        rv.setHasFixedSize(true);

        String[] keys = AppSettingsStore.getAvatarCatalogKeys();
        final int[] currentXp = {0};

        RecyclerView.Adapter<AvatarViewHolder> adapter = new RecyclerView.Adapter<AvatarViewHolder>() {
            @NonNull
            @Override
            public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View item = getLayoutInflater().inflate(R.layout.item_avatar_grid, parent, false);
                return new AvatarViewHolder(item);
            }

            @Override
            public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
                String key = keys[position];
                int price = AppSettingsStore.avatarPriceFromKey(key);
                boolean unlocked = settingsStore.isAvatarUnlocked(key);
                boolean selected = key.equals(selectedAvatarKey);

                Glide.with(holder.itemView.getContext())
                     .load(AppSettingsStore.avatarResFromKey(key))
                     .override(180, 180)
                     .centerCrop()
                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                     .into(holder.img);

                if (selected) {
                    holder.img.setStrokeWidth(6f);
                    holder.img.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ef_primary)));
                    holder.priceBadge.setBackgroundResource(R.drawable.bg_avatar_price_chip_selected);
                    holder.priceBadge.setText(getString(R.string.avatar_price_in_use));
                } else if (unlocked) {
                    holder.img.setStrokeWidth(2f);
                    holder.img.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ef_outline)));
                    holder.priceBadge.setBackgroundResource(R.drawable.bg_avatar_price_chip_unlocked);
                    if (price <= 0) {
                        holder.priceBadge.setText(getString(R.string.avatar_price_free));
                    } else {
                        holder.priceBadge.setText(getString(R.string.avatar_price_unlocked));
                    }
                } else {
                    holder.img.setStrokeWidth(1.5f);
                    holder.img.setStrokeColor(ColorStateList.valueOf(getColor(R.color.ef_outline)));
                    holder.priceBadge.setBackgroundResource(R.drawable.bg_avatar_price_chip_locked);
                    holder.priceBadge.setText(getString(R.string.avatar_price_xp_format, price));
                }
                holder.lockIcon.setVisibility(unlocked ? View.GONE : View.VISIBLE);

                holder.itemView.setOnClickListener(view -> {
                    if (unlocked) {
                        applyAvatarSelection(key);
                        dialog.dismiss();
                        Toast.makeText(SettingsActivity.this, getString(R.string.avatar_select_success), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (currentXp[0] < price) {
                        Toast.makeText(
                                SettingsActivity.this,
                                getString(R.string.avatar_buy_insufficient_xp, price, currentXp[0]),
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_unlock_avatar, null);
                    AlertDialog unlockDialog = new MaterialAlertDialogBuilder(SettingsActivity.this, R.style.TransparentDialogTheme)
                            .setView(dialogView)
                            .setCancelable(true)
                            .create();

                    ImageView previewImg = dialogView.findViewById(R.id.dialogAvatarPreview);
                    TextView titleTxt = dialogView.findViewById(R.id.dialogTitle);
                    TextView msgTxt = dialogView.findViewById(R.id.dialogMessage);
                    MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
                    MaterialButton btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm);

                    if (previewImg != null) {
                        Glide.with(SettingsActivity.this)
                             .load(AppSettingsStore.avatarResFromKey(key))
                             .into(previewImg);
                    }
                    if (msgTxt != null) {
                        msgTxt.setText(getString(R.string.avatar_buy_dialog_message, price, currentXp[0]));
                    }

                    btnCancel.setOnClickListener(v3 -> unlockDialog.dismiss());
                    btnConfirm.setOnClickListener(v3 -> {
                        unlockDialog.dismiss();
                        repository.spendXpAsync(price, spendResult -> {
                            if (!spendResult.success) {
                                String fallback = getString(R.string.avatar_buy_failed_generic);
                                String message = spendResult.message == null || spendResult.message.trim().isEmpty()
                                        ? fallback
                                        : spendResult.message;
                                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
                                return;
                            }

                            currentXp[0] = spendResult.remainingXp;
                            settingsStore.unlockAvatar(key);
                            updateAvatarXpBalanceText(xpBalanceText, currentXp[0]);
                            applyAvatarSelection(key);
                            notifyDataSetChanged();
                            dialog.dismiss();
                            Toast.makeText(SettingsActivity.this, getString(R.string.avatar_buy_success), Toast.LENGTH_SHORT).show();
                        });
                    });

                    unlockDialog.show();
                });
            }

            @Override
            public int getItemCount() {
                return keys.length;
            }
        };

        rv.setAdapter(adapter);

        repository.getDashboardSnapshotAsync(snapshot -> {
            currentXp[0] = Math.max(0, snapshot.userProgress.totalXpEarned);
            updateAvatarXpBalanceText(xpBalanceText, currentXp[0]);
            adapter.notifyDataSetChanged();
        });

        dialog.setContentView(v);
        dialog.show();
    }

    private void updateAvatarXpBalanceText(TextView xpBalanceText, int xp) {
        if (xpBalanceText != null) {
            xpBalanceText.setText(getString(R.string.avatar_picker_balance_format, Math.max(0, xp)));
        }
    }

    private void applyAvatarSelection(@NonNull String avatarKey) {
        selectedAvatarKey = avatarKey;
        settingsStore.setAvatarKey(avatarKey);
        if (avatarPreview != null) {
            Glide.with(this)
                    .load(AppSettingsStore.avatarResFromKey(avatarKey))
                    .into(avatarPreview);
        }
    }

    static class AvatarViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView img;
        ImageView lockIcon;
        TextView priceBadge;

        AvatarViewHolder(View v) {
            super(v);
            img = v.findViewById(R.id.imgAvatarItem);
            lockIcon = v.findViewById(R.id.imgAvatarLock);
            priceBadge = v.findViewById(R.id.tvAvatarPrice);
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
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build();
            currentUser.updateProfile(request);
            firebaseUserStore.updateDisplayName(currentUser.getUid(), name);
        }
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

    private void ensureNotificationPermissionForSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_SETTINGS_NOTIFICATION_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SETTINGS_NOTIFICATION_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                settingsStore.setAdminNotificationsEnabled(false);
                if (switchAdminNotifications != null) {
                    switchAdminNotifications.setChecked(false);
                }
                Toast.makeText(this, "Bạn cần cấp quyền để nhận thông báo mới", Toast.LENGTH_LONG).show();
            }
        }
    }
}
