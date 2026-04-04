package com.example.englishflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.englishflow.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AppSettingsStore {

    private static final String PREFS = "englishflow_settings";

    private static final String KEY_PROFILE_EMAIL = "profile_email";
    private static final String KEY_PROFILE_AVATAR = "profile_avatar";
    private static final String KEY_UNLOCKED_AVATARS = "unlocked_avatars";
    private static final String KEY_AVATAR_ECONOMY_MIGRATED = "avatar_economy_migrated";
    private static final String KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes";
    private static final String KEY_VOICE_SPEED_MODE = "voice_speed_mode";
    private static final String KEY_ADMIN_NOTIFICATIONS_ENABLED = "admin_notifications_enabled";
    private static final String KEY_ADMIN_NOTIFICATIONS_LAST_READ_AT = "admin_notifications_last_read_at";
    private static final String KEY_ADMIN_NOTIFICATIONS_LAST_ALERTED_AT = "admin_notifications_last_alerted_at";

    public static final int DAILY_GOAL_RELAX = 5;
    public static final int DAILY_GOAL_FOCUSED = 15;
    public static final int DAILY_GOAL_TRY_HARD = 30;

    public static final String VOICE_MODE_NORMAL = "normal";
    public static final String VOICE_MODE_SLOW = "slow";

    public static final String AVATAR_DEFAULT = "default";
    public static final String AVATAR_DOLPHIN = "dolphin";
    public static final String AVATAR_GRADUATE = "graduate";
    public static final String AVATAR_CLOUD = "cloud";

    // New Avatars
    public static final String AVATAR_CA = "ca";
    public static final String AVATAR_CAHEOBA = "caheoba";
    public static final String AVATAR_CAHOACHA = "cahoacha";
    public static final String AVATAR_CAHEOCON = "caheocon";
    public static final String AVATAR_CAHEOCONN = "caheoconn";
    public static final String AVATAR_CAHEOONG = "caheoong";
    public static final String AVATAR_CAHEOSOSINH = "caheososinh";
    public static final String AVATAR_CHO = "cho";
    public static final String AVATAR_GA = "ga";
    public static final String AVATAR_HO = "ho";
    public static final String AVATAR_HUOU = "huou";
    public static final String AVATAR_KHI = "khi";
    public static final String AVATAR_KYSI = "kysi";
    public static final String AVATAR_NATA = "nata";
    public static final String AVATAR_NGOKHONG = "ngokhong";
    public static final String AVATAR_RAN = "ran";
    public static final String AVATAR_RONG = "rong";

    private static final String[] AVATAR_CATALOG_KEYS = new String[]{
            AVATAR_DEFAULT, AVATAR_DOLPHIN, AVATAR_GRADUATE,
            AVATAR_CA, AVATAR_CAHEOBA, AVATAR_CAHOACHA,
            AVATAR_CAHEOCON, AVATAR_CAHEOCONN, AVATAR_CAHEOONG,
            AVATAR_CAHEOSOSINH, AVATAR_CHO, AVATAR_GA,
            AVATAR_HO, AVATAR_HUOU, AVATAR_KHI,
            AVATAR_KYSI, AVATAR_NATA, AVATAR_NGOKHONG,
            AVATAR_RAN, AVATAR_RONG
    };

    private static final String[] FREE_AVATAR_KEYS = new String[]{
            AVATAR_DEFAULT,
            AVATAR_DOLPHIN,
            AVATAR_GRADUATE,
            AVATAR_CA,
            AVATAR_CAHEOBA
    };

    private final SharedPreferences preferences;

    public AppSettingsStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureAvatarUnlockStateInitialized();
    }

    public void setProfileEmail(@NonNull String email) {
        preferences.edit().putString(KEY_PROFILE_EMAIL, email.trim()).apply();
    }

    @NonNull
    public String getProfileEmail(@NonNull String fallbackEmail) {
        return preferences.getString(KEY_PROFILE_EMAIL, fallbackEmail);
    }

    public void setAvatarKey(@NonNull String avatarKey) {
        preferences.edit().putString(KEY_PROFILE_AVATAR, avatarKey).apply();
    }

    @NonNull
    public String getAvatarKey() {
        return preferences.getString(KEY_PROFILE_AVATAR, AVATAR_CLOUD);
    }

    @DrawableRes
    public int getAvatarResId() {
        return avatarResFromKey(getAvatarKey());
    }

    @DrawableRes
    public static int avatarResFromKey(@NonNull String avatarKey) {
        switch (avatarKey) {
            case AVATAR_DOLPHIN:
                return R.drawable.english_flow;
            case AVATAR_GRADUATE:
                return R.drawable.graduation;
            case AVATAR_CA:
                return R.drawable.ca;
            case AVATAR_CAHEOBA:
                return R.drawable.caheoba;
            case AVATAR_CAHOACHA:
                return R.drawable.caheocha;
            case AVATAR_CAHEOCON:
                return R.drawable.caheocon;
            case AVATAR_CAHEOCONN:
                return R.drawable.caheoconn;
            case AVATAR_CAHEOONG:
                return R.drawable.caheoong;
            case AVATAR_CAHEOSOSINH:
                return R.drawable.caheososinh;
            case AVATAR_CHO:
                return R.drawable.cho;
            case AVATAR_GA:
                return R.drawable.ga;
            case AVATAR_HO:
                return R.drawable.ho;
            case AVATAR_HUOU:
                return R.drawable.huou;
            case AVATAR_KHI:
                return R.drawable.khi;
            case AVATAR_KYSI:
                return R.drawable.kysi;
            case AVATAR_NATA:
                return R.drawable.nata;
            case AVATAR_NGOKHONG:
                return R.drawable.ngokhong;
            case AVATAR_RAN:
                return R.drawable.ran;
            case AVATAR_RONG:
                return R.drawable.rong;
            case AVATAR_DEFAULT:
            default:
                return R.drawable.user_avatar;
        }
    }

    public static String[] getAvatarCatalogKeys() {
        return Arrays.copyOf(AVATAR_CATALOG_KEYS, AVATAR_CATALOG_KEYS.length);
    }

    public static int avatarPriceFromKey(@NonNull String avatarKey) {
        switch (avatarKey) {
            case AVATAR_DEFAULT:
            case AVATAR_DOLPHIN:
            case AVATAR_GRADUATE:
            case AVATAR_CA:
            case AVATAR_CAHEOBA:
            case AVATAR_CLOUD:
                return 0;
            case AVATAR_CAHOACHA:
                return 700;
            case AVATAR_CAHEOCON:
                return 800;
            case AVATAR_CAHEOCONN:
                return 900;
            case AVATAR_CAHEOONG:
                return 1000;
            case AVATAR_CAHEOSOSINH:
                return 1100;
            case AVATAR_CHO:
                return 1200;
            case AVATAR_GA:
                return 1300;
            case AVATAR_HO:
                return 1400;
            case AVATAR_HUOU:
                return 1500;
            case AVATAR_KHI:
                return 1700;
            case AVATAR_KYSI:
                return 1900;
            case AVATAR_NATA:
                return 2100;
            case AVATAR_NGOKHONG:
                return 2300;
            case AVATAR_RAN:
                return 2400;
            case AVATAR_RONG:
                return 2500;
            default:
                return 0;
        }
    }

    public static boolean isFreeAvatar(@NonNull String avatarKey) {
        return avatarPriceFromKey(avatarKey) <= 0;
    }

    public boolean isAvatarUnlocked(@NonNull String avatarKey) {
        if (isFreeAvatar(avatarKey)) {
            return true;
        }
        Set<String> unlocked = preferences.getStringSet(KEY_UNLOCKED_AVATARS, null);
        return unlocked != null && unlocked.contains(avatarKey);
    }

    public void unlockAvatar(@NonNull String avatarKey) {
        if (isFreeAvatar(avatarKey)) {
            return;
        }
        Set<String> unlocked = getUnlockedAvatarSet();
        if (unlocked.add(avatarKey)) {
            preferences.edit().putStringSet(KEY_UNLOCKED_AVATARS, unlocked).apply();
        }
    }

    private void ensureAvatarUnlockStateInitialized() {
        boolean migrated = preferences.getBoolean(KEY_AVATAR_ECONOMY_MIGRATED, false);
        if (migrated) {
            Set<String> unlocked = preferences.getStringSet(KEY_UNLOCKED_AVATARS, null);
            if (unlocked == null) {
                preferences.edit().putStringSet(KEY_UNLOCKED_AVATARS, getDefaultUnlockedAvatarSet()).apply();
            }
            return;
        }

        Set<String> unlocked = getDefaultUnlockedAvatarSet();
        String selected = getAvatarKey();
        if (!selected.trim().isEmpty()) {
            unlocked.add(selected);
        }

        preferences.edit()
                .putStringSet(KEY_UNLOCKED_AVATARS, unlocked)
                .putBoolean(KEY_AVATAR_ECONOMY_MIGRATED, true)
                .apply();
    }

    @NonNull
    private Set<String> getDefaultUnlockedAvatarSet() {
        return new HashSet<>(Arrays.asList(FREE_AVATAR_KEYS));
    }

    @NonNull
    private Set<String> getUnlockedAvatarSet() {
        Set<String> stored = preferences.getStringSet(KEY_UNLOCKED_AVATARS, null);
        Set<String> unlocked = stored == null ? getDefaultUnlockedAvatarSet() : new HashSet<>(stored);
        unlocked.addAll(getDefaultUnlockedAvatarSet());
        return unlocked;
    }

    public void setDailyGoalMinutes(int minutes) {
        int safeValue = minutes;
        if (minutes != DAILY_GOAL_RELAX && minutes != DAILY_GOAL_FOCUSED && minutes != DAILY_GOAL_TRY_HARD) {
            safeValue = DAILY_GOAL_FOCUSED;
        }
        preferences.edit().putInt(KEY_DAILY_GOAL_MINUTES, safeValue).apply();
    }

    public int getDailyGoalMinutes() {
        return preferences.getInt(KEY_DAILY_GOAL_MINUTES, DAILY_GOAL_FOCUSED);
    }

    public void setVoiceSpeedMode(@NonNull String mode) {
        String safeMode = VOICE_MODE_NORMAL;
        if (VOICE_MODE_SLOW.equals(mode) || VOICE_MODE_NORMAL.equals(mode)) {
            safeMode = mode;
        }
        preferences.edit().putString(KEY_VOICE_SPEED_MODE, safeMode).apply();
    }

    @NonNull
    public String getVoiceSpeedMode() {
        return preferences.getString(KEY_VOICE_SPEED_MODE, VOICE_MODE_NORMAL);
    }

    public float getVoiceSpeechRate() {
        return VOICE_MODE_SLOW.equals(getVoiceSpeedMode()) ? 0.8f : 1.0f;
    }

    public void setAdminNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ADMIN_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isAdminNotificationsEnabled() {
        return preferences.getBoolean(KEY_ADMIN_NOTIFICATIONS_ENABLED, true);
    }

    public void setLastAdminNotificationReadAt(long timestampMillis) {
        preferences.edit().putLong(
                KEY_ADMIN_NOTIFICATIONS_LAST_READ_AT,
                Math.max(0L, timestampMillis)
        ).apply();
    }

    public long getLastAdminNotificationReadAt() {
        return Math.max(0L, preferences.getLong(KEY_ADMIN_NOTIFICATIONS_LAST_READ_AT, 0L));
    }

    public void setLastAdminNotificationAlertedAt(long timestampMillis) {
        preferences.edit().putLong(
                KEY_ADMIN_NOTIFICATIONS_LAST_ALERTED_AT,
                Math.max(0L, timestampMillis)
        ).apply();
    }

    public long getLastAdminNotificationAlertedAt() {
        return Math.max(0L, preferences.getLong(KEY_ADMIN_NOTIFICATIONS_LAST_ALERTED_AT, 0L));
    }
}
