package com.example.englishflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.dao.LocalUserDao;
import com.example.englishflow.database.entity.LocalUserEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LocalAuthStore {

    private static final String PREFS = "englishflow_auth_prefs";
    private static final String KEY_CURRENT_EMAIL = "current_user_email";

    private final SharedPreferences preferences;
    private final LocalUserDao localUserDao;

    public LocalAuthStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        localUserDao = EnglishFlowDatabase.getInstance(appContext).localUserDao();
    }

    public boolean hasActiveSession() {
        return getCurrentUser() != null;
    }

    @Nullable
    public LocalUserEntity getCurrentUser() {
        String email = getCurrentUserEmail();
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        LocalUserEntity user = localUserDao.findByEmail(email);
        if (user == null) {
            clearSession();
        }
        return user;
    }

    public boolean emailExists(@NonNull String email) {
        return localUserDao.findByEmail(email.toLowerCase().trim()) != null;
    }

    public boolean register(@NonNull String displayName, @NonNull String email, @NonNull String rawPassword) {
        String normalizedEmail = email.toLowerCase().trim();
        if (emailExists(normalizedEmail)) {
            return false;
        }

        long now = System.currentTimeMillis();
        LocalUserEntity user = new LocalUserEntity(
                normalizedEmail,
                displayName.trim(),
                hashPassword(rawPassword),
                now,
                now
        );

        localUserDao.insert(user);
        saveSession(normalizedEmail);
        return true;
    }

    public boolean login(@NonNull String email, @NonNull String rawPassword) {
        String normalizedEmail = email.toLowerCase().trim();
        LocalUserEntity user = localUserDao.findByEmail(normalizedEmail);
        if (user == null) {
            return false;
        }

        String inputHash = hashPassword(rawPassword);
        if (!inputHash.equals(user.passwordHash)) {
            return false;
        }

        saveSession(normalizedEmail);
        return true;
    }

    public void logout() {
        clearSession();
    }

    public void updateCurrentDisplayName(@NonNull String displayName) {
        String email = getCurrentUserEmail();
        if (email == null || email.trim().isEmpty()) {
            return;
        }
        localUserDao.updateDisplayName(email, displayName.trim(), System.currentTimeMillis());
    }

    @Nullable
    public String getCurrentDisplayName() {
        LocalUserEntity currentUser = getCurrentUser();
        return currentUser != null ? currentUser.displayName : null;
    }

    private void saveSession(@NonNull String email) {
        preferences.edit().putString(KEY_CURRENT_EMAIL, email).apply();
    }

    private void clearSession() {
        preferences.edit().remove(KEY_CURRENT_EMAIL).apply();
    }

    @Nullable
    private String getCurrentUserEmail() {
        return preferences.getString(KEY_CURRENT_EMAIL, null);
    }

    private String hashPassword(@NonNull String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
