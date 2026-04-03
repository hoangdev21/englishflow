package com.example.englishflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.englishflow.R;

public class AppSettingsStore {

    private static final String PREFS = "englishflow_settings";

    private static final String KEY_PROFILE_EMAIL = "profile_email";
    private static final String KEY_PROFILE_AVATAR = "profile_avatar";
    private static final String KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes";
    private static final String KEY_VOICE_SPEED_MODE = "voice_speed_mode";

    public static final int DAILY_GOAL_RELAX = 5;
    public static final int DAILY_GOAL_FOCUSED = 15;
    public static final int DAILY_GOAL_TRY_HARD = 30;

    public static final String VOICE_MODE_NORMAL = "normal";
    public static final String VOICE_MODE_SLOW = "slow";

    public static final String AVATAR_DEFAULT = "default";
    public static final String AVATAR_DOLPHIN = "dolphin";
    public static final String AVATAR_GRADUATE = "graduate";

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

    private final SharedPreferences preferences;

    public AppSettingsStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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
        return preferences.getString(KEY_PROFILE_AVATAR, AVATAR_DEFAULT);
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
}
