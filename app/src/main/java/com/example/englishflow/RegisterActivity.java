package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.LocalAuthStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private LocalAuthStore localAuthStore;
    private AppRepository repository;

    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private MaterialButton registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerScrollView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        localAuthStore = new LocalAuthStore(getApplicationContext());
        repository = AppRepository.getInstance(getApplicationContext());

        nameInput = findViewById(R.id.registerNameInput);
        emailInput = findViewById(R.id.registerEmailInput);
        passwordInput = findViewById(R.id.registerPasswordInput);
        confirmPasswordInput = findViewById(R.id.registerConfirmPasswordInput);
        registerButton = findViewById(R.id.btnRegister);

        TextView loginLink = findViewById(R.id.btnGoToLogin);

        registerButton.setOnClickListener(v -> registerAccount());
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerAccount() {
        String displayName = valueOf(nameInput);
        String email = valueOf(emailInput);
        String password = valueOf(passwordInput);
        String confirmPassword = valueOf(confirmPasswordInput);

        if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, R.string.auth_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, R.string.auth_password_short, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, R.string.auth_password_not_match, Toast.LENGTH_SHORT).show();
            return;
        }

        if (localAuthStore.emailExists(email)) {
            Toast.makeText(this, R.string.auth_email_already_in_use, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        boolean created = localAuthStore.register(displayName, email, password);
        setLoading(false);
        if (created) {
            repository.setUserName(displayName);
            Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show();
            startMainActivity();
        } else {
            Toast.makeText(this, R.string.auth_register_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setText(loading ? R.string.auth_registering : R.string.auth_register);
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
