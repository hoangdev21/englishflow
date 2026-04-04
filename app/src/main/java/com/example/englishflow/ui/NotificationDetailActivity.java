package com.example.englishflow.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";
    public static final String EXTRA_TITLE = "extra_notification_title";
    public static final String EXTRA_MESSAGE = "extra_notification_message";
    public static final String EXTRA_CREATED_AT = "extra_notification_created_at";
    public static final String EXTRA_CREATED_BY_NAME = "extra_notification_created_by_name";
    public static final String EXTRA_TARGET_TYPE = "extra_notification_target_type";
    public static final String EXTRA_TARGET_EMAIL = "extra_notification_target_email";

    private static final SimpleDateFormat DETAIL_TIME_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    private TextView detailTime;
    private TextView detailSender;
    private TextView detailMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        bindViews();
        setupInsets();
        setupActions();
        bindIntentData();
    }

    private void bindViews() {
        detailTime = findViewById(R.id.txtNotificationDetailTime);
        detailSender = findViewById(R.id.txtNotificationDetailSender);
        detailMessage = findViewById(R.id.txtNotificationDetailMessage);
    }

    private void setupInsets() {
        View topBar = findViewById(R.id.notificationDetailTopBar);
        View scroll = findViewById(R.id.notificationDetailScroll);

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
            topBar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                applyContentTopSpacing(scroll, v.getHeight() + extraTopSpacing);
            });
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
        View backButton = findViewById(R.id.btnBackNotificationDetail);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void bindIntentData() {
        String message = nonEmpty(getIntent().getStringExtra(EXTRA_MESSAGE), "Bạn có thông báo mới.");
        long createdAt = Math.max(0L, getIntent().getLongExtra(EXTRA_CREATED_AT, 0L));

        if (detailMessage != null) {
            detailMessage.setText(message);
        }
        if (detailTime != null) {
            detailTime.setText(formatCreatedAt(createdAt));
        }
        if (detailSender != null) {
            detailSender.setText("ADMIN");
        }
    }

    @NonNull
    private String formatCreatedAt(long createdAt) {
        if (createdAt <= 0L) {
            return "--";
        }
        return DETAIL_TIME_FORMAT.format(new Date(createdAt));
    }

    @NonNull
    private String nonEmpty(String value, @NonNull String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
