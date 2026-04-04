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

import com.example.englishflow.admin.AdminDashboardActivity;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private AppRepository repository;
    private FirebaseAuth firebaseAuth;
    private FirebaseUserStore firebaseUserStore;

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

        repository = AppRepository.getInstance(getApplicationContext());
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUserStore = new FirebaseUserStore();

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

        setLoading(true);
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> onRegisterSuccess(result.getUser(), displayName))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showRegisterError(e);
                });
    }

    private void onRegisterSuccess(FirebaseUser user, String displayName) {
        if (user == null) {
            setLoading(false);
            Toast.makeText(this, R.string.auth_register_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        UserProfileChangeRequest profileRequest = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build();

        user.updateProfile(profileRequest)
                .addOnCompleteListener(task -> {
                    firebaseUserStore.getOrCreateProfile(user, displayName, "", profile -> {
                        setLoading(false);
                        if (profile == null) {
                            Toast.makeText(this, R.string.auth_register_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (profile.isLocked()) {
                            firebaseAuth.signOut();
                            Toast.makeText(this, R.string.auth_account_locked, Toast.LENGTH_LONG).show();
                            return;
                        }

                        repository.setUserName(profile.displayName);
                        firebaseUserStore.recordAccessLog(profile, "password");
                        Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show();
                        if (profile.isAdmin()) {
                            startAdminDashboard();
                        } else {
                            startMainActivity();
                        }
                    });
                });
    }

    private void setLoading(boolean loading) {
        if (registerButton != null) {
            registerButton.setEnabled(!loading);
            registerButton.setText(loading ? R.string.auth_registering : R.string.auth_register);
        }
    }

    private void showRegisterError(Exception e) {
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)) {
                Toast.makeText(this, R.string.auth_email_already_in_use, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_WEAK_PASSWORD".equals(code)) {
                Toast.makeText(this, R.string.auth_password_short, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                Toast.makeText(this, R.string.auth_register_method_not_enabled, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
                Toast.makeText(this, R.string.auth_network_error, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, R.string.auth_register_failed, Toast.LENGTH_SHORT).show();
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void startAdminDashboard() {
        Intent intent = new Intent(this, AdminDashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
