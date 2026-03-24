package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private FirebaseUserStore firebaseUserStore;
    private AppRepository repository;

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUserStore = new FirebaseUserStore();
        repository = AppRepository.getInstance(getApplicationContext());

        if (firebaseAuth.getCurrentUser() != null) {
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

        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        firebaseUserStore.fetchDisplayName(firebaseAuth.getCurrentUser().getUid(), displayName -> {
                            if (displayName != null && !displayName.isEmpty()) {
                                repository.setUserName(displayName);
                            }
                            startMainActivity();
                        });
                    } else {
                        Toast.makeText(this, R.string.auth_login_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendPasswordResetEmail() {
        String email = valueOf(emailInput);
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, R.string.auth_enter_email_first, Toast.LENGTH_SHORT).show();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> Toast.makeText(this, R.string.auth_reset_sent, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, R.string.auth_reset_failed, Toast.LENGTH_SHORT).show());
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
