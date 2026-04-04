package com.example.englishflow.admin;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.R;
import com.example.englishflow.data.CloudUserProfile;
import com.example.englishflow.data.FirebaseUserStore;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.Locale;

public class AdminUserDetailActivity extends AppCompatActivity {

    public static final String EXTRA_USER_UID = "extra_admin_user_uid";

    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("#,###");

    private final FirebaseUserStore userStore = new FirebaseUserStore();

    private String userUid = "";
    private CloudUserProfile currentProfile;
    private CloudUserProfile adminProfile;

    private ShapeableImageView avatarImage;
    private TextView avatarFallback;
    private TextView nameView;
    private TextView emailView;
    private View presenceDot;
    private TextView presenceView;
    private Chip roleChip;
    private Chip statusChip;
    private TextView xpView;
    private TextView streakView;
    private TextView wordsView;
    private TextInputEditText inputName;
    private TextInputEditText inputXp;
    private MaterialButton btnToggleRole;
    private MaterialButton btnToggleStatus;
    private MaterialButton btnResetPassword;
    private MaterialButton btnDeleteUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_user_detail);

        userUid = safe(getIntent().getStringExtra(EXTRA_USER_UID));
        if (userUid.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupInsets();
        setupActions();
        fetchAdminProfile();
        fetchUserProfile();
    }

    private void fetchAdminProfile() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userStore.fetchProfile(user.getUid(), profile -> adminProfile = profile);
        }
    }

    private void bindViews() {
        avatarImage = findViewById(R.id.adminDetailAvatarImage);
        avatarFallback = findViewById(R.id.adminDetailAvatarFallback);
        nameView = findViewById(R.id.adminDetailName);
        emailView = findViewById(R.id.adminDetailEmail);
        presenceDot = findViewById(R.id.adminDetailPresenceDot);
        presenceView = findViewById(R.id.adminDetailPresence);
        roleChip = findViewById(R.id.adminDetailRoleChip);
        statusChip = findViewById(R.id.adminDetailStatusChip);
        xpView = findViewById(R.id.adminDetailXp);
        streakView = findViewById(R.id.adminDetailStreak);
        wordsView = findViewById(R.id.adminDetailWords);
        inputName = findViewById(R.id.inputAdminDetailName);
        inputXp = findViewById(R.id.inputAdminDetailXp);
        btnToggleRole = findViewById(R.id.btnAdminDetailToggleRole);
        btnToggleStatus = findViewById(R.id.btnAdminDetailToggleStatus);
        btnResetPassword = findViewById(R.id.btnAdminDetailResetPassword);
        btnDeleteUser = findViewById(R.id.btnAdminDetailDeleteUser);
    }

    private void setupInsets() {
        View topBar = findViewById(R.id.adminUserDetailTopBar);
        View scroll = findViewById(R.id.adminUserDetailScroll);

        if (topBar != null) {
            final int initialTop = topBar.getPaddingTop();
            ViewCompat.setOnApplyWindowInsetsListener(topBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), initialTop + systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
            ViewCompat.requestApplyInsets(topBar);
        }

        if (scroll != null) {
            final int initialBottom = scroll.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), initialBottom + systemBars.bottom);
                return insets;
            });
            ViewCompat.requestApplyInsets(scroll);
        }

        if (topBar != null && scroll != null) {
            final int extraTopSpacing = Math.round(18f * getResources().getDisplayMetrics().density);
            topBar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    applyContentTopSpacing(scroll, v.getHeight() + extraTopSpacing)
            );
            topBar.post(() -> applyContentTopSpacing(scroll, topBar.getHeight() + extraTopSpacing));
        }
    }

    private void applyContentTopSpacing(@NonNull View contentView, int topMargin) {
        ViewGroup.LayoutParams params = contentView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) params;
        if (marginLayoutParams.topMargin == topMargin) {
            return;
        }
        marginLayoutParams.topMargin = topMargin;
        contentView.setLayoutParams(marginLayoutParams);
    }

    private void setupActions() {
        View backButton = findViewById(R.id.btnBackAdminUserDetail);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        MaterialButton btnSaveName = findViewById(R.id.btnAdminDetailSaveName);
        MaterialButton btnGrantXp = findViewById(R.id.btnAdminDetailGrantXp);

        if (btnSaveName != null) {
            btnSaveName.setOnClickListener(v -> saveDisplayName());
        }
        if (btnGrantXp != null) {
            btnGrantXp.setOnClickListener(v -> grantXp());
        }
        if (btnToggleRole != null) {
            btnToggleRole.setOnClickListener(v -> toggleRole());
        }
        if (btnToggleStatus != null) {
            btnToggleStatus.setOnClickListener(v -> toggleStatus());
        }
        if (btnResetPassword != null) {
            btnResetPassword.setOnClickListener(v -> resetPassword());
        }
        if (btnDeleteUser != null) {
            btnDeleteUser.setOnClickListener(v -> deleteUser());
        }
    }

    private void fetchUserProfile() {
        userStore.fetchProfile(userUid, profile -> {
            if (profile == null) {
                Toast.makeText(this, "Không tìm thấy user", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            currentProfile = profile;
            bindProfile(profile);
        });
    }

    private void bindProfile(@NonNull CloudUserProfile profile) {
        long now = System.currentTimeMillis();

        if (nameView != null) {
            nameView.setText(profile.displayName);
        }
        if (emailView != null) {
            emailView.setText(profile.email);
        }
        if (xpView != null) {
            xpView.setText("XP: " + SCORE_FORMAT.format(profile.totalXp));
        }
        if (streakView != null) {
            streakView.setText("Streak: " + profile.currentStreak);
        }
        if (wordsView != null) {
            wordsView.setText("Words: " + profile.learnedWords);
        }
        if (inputName != null && !inputName.isFocused()) {
            inputName.setText(profile.displayName);
        }

        bindAvatar(profile);
        bindPresence(profile, now);
        bindRoleChip(profile);
        bindStatusChip(profile);
        updateActionButtons(profile);
    }

    private void bindAvatar(@NonNull CloudUserProfile profile) {
        String initial = "U";
        if (!TextUtils.isEmpty(profile.displayName)) {
            initial = profile.displayName.substring(0, 1).toUpperCase(Locale.getDefault());
        }
        if (avatarFallback != null) {
            avatarFallback.setText(initial);
        }

        if (avatarImage == null || avatarFallback == null) {
            return;
        }

        if (TextUtils.isEmpty(profile.photoUrl)) {
            avatarImage.setVisibility(View.GONE);
            avatarFallback.setVisibility(View.VISIBLE);
            return;
        }

        avatarImage.setVisibility(View.VISIBLE);
        avatarFallback.setVisibility(View.GONE);
        Glide.with(avatarImage)
                .load(profile.photoUrl)
                .circleCrop()
                .into(avatarImage);
    }

    private void bindPresence(@NonNull CloudUserProfile profile, long now) {
        if (presenceView == null || presenceDot == null) {
            return;
        }

        int dotColor;
        String text;
        if (profile.isDeleted()) {
            dotColor = ContextCompat.getColor(this, R.color.ef_error);
            text = "Đã xóa bởi admin";
        } else if (profile.isActiveNow(now)) {
            dotColor = ContextCompat.getColor(this, R.color.ef_success);
            text = "Đang hoạt động";
        } else {
            long lastSeen = profile.getLastSeenAt();
            if (lastSeen <= 0L) {
                dotColor = ContextCompat.getColor(this, R.color.ef_text_tertiary);
                text = "Chưa có dữ liệu truy cập";
            } else {
                dotColor = ContextCompat.getColor(this, R.color.ef_warning_text);
                text = formatLastSeen(lastSeen, now);
            }
        }

        presenceDot.setBackgroundTintList(ColorStateList.valueOf(dotColor));
        presenceView.setText(text);
    }

    private void bindRoleChip(@NonNull CloudUserProfile profile) {
        if (roleChip == null) {
            return;
        }
        if (profile.isAdmin()) {
            roleChip.setText("ADMIN");
            roleChip.setChipBackgroundColorResource(R.color.ef_card_blue);
            roleChip.setTextColor(ContextCompat.getColor(this, R.color.ef_card_blue_text));
        } else {
            roleChip.setText("USER");
            roleChip.setChipBackgroundColorResource(R.color.ef_primary_container);
            roleChip.setTextColor(ContextCompat.getColor(this, R.color.ef_primary));
        }
    }

    private void bindStatusChip(@NonNull CloudUserProfile profile) {
        if (statusChip == null) {
            return;
        }
        if (profile.isDeleted()) {
            statusChip.setText("DELETED");
            statusChip.setChipBackgroundColorResource(R.color.ef_card_rose);
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.ef_card_rose_text));
            return;
        }
        if (profile.isLocked()) {
            statusChip.setText("LOCKED");
            statusChip.setChipBackgroundColorResource(R.color.ef_warning_bg);
            statusChip.setTextColor(ContextCompat.getColor(this, R.color.ef_warning_text));
            return;
        }
        statusChip.setText("ACTIVE");
        statusChip.setChipBackgroundColorResource(R.color.ef_primary_container);
        statusChip.setTextColor(ContextCompat.getColor(this, R.color.ef_primary));
    }

    private void updateActionButtons(@NonNull CloudUserProfile profile) {
        if (btnToggleRole != null) {
            btnToggleRole.setText(profile.isAdmin() ? "Hạ quyền User" : "Nâng quyền Admin");
        }

        if (btnToggleStatus != null) {
            if (profile.isDeleted()) {
                btnToggleStatus.setText("Kích hoạt lại tài khoản");
                btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ef_primary_container)));
                btnToggleStatus.setTextColor(ContextCompat.getColor(this, R.color.ef_primary));
            } else if (profile.isLocked()) {
                btnToggleStatus.setText("Mở khóa tài khoản");
                btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ef_primary_container)));
                btnToggleStatus.setTextColor(ContextCompat.getColor(this, R.color.ef_primary));
            } else {
                btnToggleStatus.setText("Khóa tài khoản");
                btnToggleStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ef_card_rose)));
                btnToggleStatus.setTextColor(ContextCompat.getColor(this, R.color.ef_card_rose_text));
            }
        }

        if (btnDeleteUser != null) {
            btnDeleteUser.setEnabled(!profile.isDeleted());
            btnDeleteUser.setAlpha(profile.isDeleted() ? 0.55f : 1f);
        }
    }

    private void saveDisplayName() {
        if (currentProfile == null || inputName == null) {
            return;
        }
        String nextName = safe(inputName.getText() == null ? "" : inputName.getText().toString());
        userStore.updateDisplayNameByAdmin(currentProfile.uid, nextName, (success, message) -> {
            Toast.makeText(
                    this,
                    success ? "Đã cập nhật tên" : nonEmpty(message, "Không thể cập nhật tên"),
                    Toast.LENGTH_SHORT
            ).show();
            if (success) {
                fetchUserProfile();
            }
        });
    }

    private void grantXp() {
        if (currentProfile == null || inputXp == null) {
            return;
        }
        String raw = safe(inputXp.getText() == null ? "" : inputXp.getText().toString());
        int delta;
        try {
            delta = Integer.parseInt(raw);
        } catch (Exception ignored) {
            delta = 0;
        }

        if (delta <= 0) {
            Toast.makeText(this, "Nhập số XP lớn hơn 0", Toast.LENGTH_SHORT).show();
            return;
        }

        final int xpToGrant = delta;
        String adminUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        String adminName = adminProfile != null ? adminProfile.displayName : "Admin";

        userStore.rewardXpByAdmin(adminUid, adminName, currentProfile.uid, delta, (success, message) -> {
            Toast.makeText(
                    this,
                    success ? "Đã cấp " + xpToGrant + " XP" : nonEmpty(message, "Không thể cấp XP"),
                    Toast.LENGTH_SHORT
            ).show();
            if (success) {
                inputXp.setText("");
                fetchUserProfile();
            }
        });
    }

    private void toggleRole() {
        if (currentProfile == null) {
            return;
        }
        String nextRole = currentProfile.isAdmin() ? FirebaseUserStore.ROLE_USER : FirebaseUserStore.ROLE_ADMIN;
        String message = currentProfile.isAdmin()
                ? "Hạ quyền tài khoản này xuống USER?"
                : "Nâng quyền tài khoản này lên ADMIN?";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Đổi quyền")
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        userStore.updateRole(currentProfile.uid, nextRole, (success, errorMessage) -> {
                            Toast.makeText(
                                    this,
                                    success ? "Đã cập nhật quyền" : nonEmpty(errorMessage, "Không thể đổi quyền"),
                                    Toast.LENGTH_SHORT
                            ).show();
                            if (success) {
                                fetchUserProfile();
                            }
                        })
                )
                .show();
    }

    private void toggleStatus() {
        if (currentProfile == null) {
            return;
        }

        String nextStatus = currentProfile.isLocked()
                ? FirebaseUserStore.STATUS_ACTIVE
                : FirebaseUserStore.STATUS_LOCKED;
        String message = currentProfile.isLocked()
                ? "Mở khóa/kích hoạt lại tài khoản này?"
                : "Khóa tài khoản này để ngăn đăng nhập?";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Cập nhật trạng thái")
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        userStore.updateStatus(currentProfile.uid, nextStatus, (success, errorMessage) -> {
                            Toast.makeText(
                                    this,
                                    success ? "Đã cập nhật trạng thái" : nonEmpty(errorMessage, "Không thể cập nhật trạng thái"),
                                    Toast.LENGTH_SHORT
                            ).show();
                            if (success) {
                                fetchUserProfile();
                            }
                        })
                )
                .show();
    }

    private void resetPassword() {
        if (currentProfile == null) {
            return;
        }
        if (safe(currentProfile.email).isEmpty()) {
            Toast.makeText(this, "User chưa có email hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Đặt lại mật khẩu")
                .setMessage("Gửi email đặt lại mật khẩu tới " + currentProfile.email + "?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi email", (dialog, which) ->
                        userStore.sendPasswordResetForUser(currentProfile.email, (success, errorMessage) ->
                                Toast.makeText(
                                        this,
                                        success ? "Đã gửi email đặt lại mật khẩu" : nonEmpty(errorMessage, "Không thể gửi email"),
                                        Toast.LENGTH_SHORT
                                ).show()
                        )
                )
                .show();
    }

    private void deleteUser() {
        if (currentProfile == null || currentProfile.isDeleted()) {
            Toast.makeText(this, "Tài khoản này đã được xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa người dùng")
                .setMessage("User sẽ bị đánh dấu xóa và ngừng truy cập ứng dụng. Xác nhận?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) ->
                        userStore.softDeleteUser(currentProfile.uid, (success, errorMessage) -> {
                            Toast.makeText(
                                    this,
                                    success ? "Đã đánh dấu xóa user" : nonEmpty(errorMessage, "Không thể xóa user"),
                                    Toast.LENGTH_SHORT
                            ).show();
                            if (success) {
                                fetchUserProfile();
                            }
                        })
                )
                .show();
    }

    @NonNull
    private String formatLastSeen(long lastSeenAt, long now) {
        long diff = Math.max(0L, now - lastSeenAt);
        long minutes = diff / (60L * 1000L);
        if (minutes < 1L) {
            return "Vừa truy cập";
        }
        if (minutes < 60L) {
            return "Truy cập " + minutes + " phút trước";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return "Truy cập " + hours + " giờ trước";
        }
        long days = hours / 24L;
        return "Truy cập " + days + " ngày trước";
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    private String nonEmpty(String value, @NonNull String fallback) {
        String candidate = safe(value);
        return candidate.isEmpty() ? fallback : candidate;
    }
}