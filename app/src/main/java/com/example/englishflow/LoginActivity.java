package com.example.englishflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.englishflow.admin.AdminDashboardActivity;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private AppRepository repository;
    private FirebaseAuth firebaseAuth;
    private FirebaseUserStore firebaseUserStore;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private boolean googleConfigured;

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton loginButton;
    private MaterialButton googleSignInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.loginScrollView), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = AppRepository.getInstance(getApplicationContext());
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUserStore = new FirebaseUserStore();
        setupGoogleSignIn();

        if (firebaseAuth.getCurrentUser() != null) {
            routeAuthenticatedUser(firebaseAuth.getCurrentUser(), null, "session");
            return;
        }

        emailInput = findViewById(R.id.loginEmailInput);
        passwordInput = findViewById(R.id.loginPasswordInput);
        loginButton = findViewById(R.id.btnLogin);
        googleSignInButton = findViewById(R.id.btnGoogleSignIn);

        TextView registerLink = findViewById(R.id.btnGoToRegister);
        TextView forgotPasswordLink = findViewById(R.id.btnForgotPassword);

        loginButton.setOnClickListener(v -> login());
        if (googleSignInButton != null) {
            googleSignInButton.setOnClickListener(v -> launchGoogleSignIn());
        }

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
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> routeAuthenticatedUser(result.getUser(), null, "password"))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showAuthError(e);
                });
    }

    private void sendPasswordResetEmail() {
        String email = valueOf(emailInput);
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, R.string.auth_enter_email_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> Toast.makeText(this, R.string.auth_reset_sent, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, R.string.auth_reset_failed, Toast.LENGTH_SHORT).show());
    }

    private void setLoading(boolean loading) {
        if (loginButton != null) {
            loginButton.setEnabled(!loading);
            loginButton.setText(loading ? R.string.auth_logging_in : R.string.auth_login);
        }
        if (googleSignInButton != null) {
            googleSignInButton.setEnabled(!loading);
            googleSignInButton.setAlpha(loading ? 0.7f : 1f);
        }
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void setupGoogleSignIn() {
        String webClientId = resolveWebClientId();
        googleConfigured = !TextUtils.isEmpty(webClientId);

        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();
        if (googleConfigured) {
            builder.requestIdToken(webClientId);
        }

        googleSignInClient = GoogleSignIn.getClient(this, builder.build());
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() == null) {
                        setLoading(false);
                        Toast.makeText(this, R.string.auth_google_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    handleGoogleSignInResult(result.getData());
                }
        );
    }

    private void launchGoogleSignIn() {
        if (!googleConfigured) {
            Toast.makeText(this, R.string.auth_google_not_configured, Toast.LENGTH_LONG).show();
            return;
        }
        setLoading(true);
        googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || TextUtils.isEmpty(account.getIdToken())) {
                setLoading(false);
                Toast.makeText(this, R.string.auth_google_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> routeAuthenticatedUser(result.getUser(), account.getDisplayName(), "google"))
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        showAuthError(e);
                    });
        } catch (ApiException e) {
            setLoading(false);
            Toast.makeText(this, R.string.auth_google_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void routeAuthenticatedUser(FirebaseUser user, String fallbackDisplayName, String provider) {
        if (user == null) {
            setLoading(false);
            Toast.makeText(this, R.string.auth_login_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        firebaseUserStore.getOrCreateProfile(user, fallbackDisplayName, photoUrl, profile -> {
            setLoading(false);
            if (profile == null) {
                Toast.makeText(this, R.string.auth_network_error, Toast.LENGTH_SHORT).show();
                return;
            }

            if (profile.isLocked()) {
                firebaseAuth.signOut();
                Toast.makeText(this, R.string.auth_account_locked, Toast.LENGTH_LONG).show();
                return;
            }

            repository.setUserName(profile.displayName);
            firebaseUserStore.recordAccessLog(profile, provider);
            if (profile.isAdmin() || com.example.englishflow.data.FirebaseSeeder.isAdminEmail(profile.email)) {
                startAdminDashboard();
            } else {
                startMainActivity();
            }
        });
    }

    private String resolveWebClientId() {
        int resId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (resId == 0) {
            return "";
        }
        String value = getString(resId);
        return value == null ? "" : value.trim();
    }

    private void showAuthError(Exception e) {
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                Toast.makeText(this, R.string.auth_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_INVALID_CREDENTIAL".equals(code)
                    || "ERROR_WRONG_PASSWORD".equals(code)
                    || "ERROR_USER_NOT_FOUND".equals(code)) {
                Toast.makeText(this, R.string.auth_invalid_credentials, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
                Toast.makeText(this, R.string.auth_too_many_requests, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_NETWORK_REQUEST_FAILED".equals(code)) {
                Toast.makeText(this, R.string.auth_network_error, Toast.LENGTH_SHORT).show();
                return;
            }
            if ("ERROR_USER_DISABLED".equals(code)) {
                Toast.makeText(this, R.string.auth_account_locked, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, R.string.auth_login_failed, Toast.LENGTH_SHORT).show();
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
