package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.LocalAuthStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private LocalAuthStore localAuthStore;
    private AppRepository repository;

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        localAuthStore = new LocalAuthStore(getApplicationContext());
        repository = AppRepository.getInstance(getApplicationContext());

        if (localAuthStore.hasActiveSession()) {
            String displayName = localAuthStore.getCurrentDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                repository.setUserName(displayName);
            }
            startMainActivity();
            return;
        }

        emailInput = findViewById(R.id.loginEmailInput);
        passwordInput = findViewById(R.id.loginPasswordInput);
        loginButton = findViewById(R.id.btnLogin);

        TextView registerLink = findViewById(R.id.btnGoToRegister);
        TextView forgotPasswordLink = findViewById(R.id.btnForgotPassword);

        loginButton.setOnClickListener(v -> login());

        registerLink.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });

        forgotPasswordLink.setOnClickListener(v -> sendPasswordResetEmail());
    }

    private void login() {
        String email = valueOf(emailInput);
        String password = valueOf(passwordInput);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.auth_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        boolean success = localAuthStore.login(email, password);
        setLoading(false);
        if (success) {
            String displayName = localAuthStore.getCurrentDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                repository.setUserName(displayName);
            }
            startMainActivity();
        } else {
            Toast.makeText(this, R.string.auth_invalid_credentials, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPasswordResetEmail() {
        String email = valueOf(emailInput);
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, R.string.auth_enter_email_first, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.auth_reset_not_supported_local, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? R.string.auth_logging_in : R.string.auth_login);
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
