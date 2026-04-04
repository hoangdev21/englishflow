package com.example.englishflow.data;

public class CloudUserProfile {
    private static final long ONLINE_WINDOW_MS = 2 * 60 * 1000L;

    public String uid = "";
    public String email = "";
    public String displayName = "";
    public String photoUrl = "";
    public String role = FirebaseUserStore.ROLE_USER;
    public String status = FirebaseUserStore.STATUS_ACTIVE;
    public boolean isOnline = false;

    public int totalXp = 0;
    public int xpToday = 0;
    public int learnedWords = 0;
    public int currentStreak = 0;
    public int bestStreak = 0;
    public int totalWordsScanned = 0;
    public int totalStudyMinutes = 0;

    public long createdAt = 0L;
    public long updatedAt = 0L;
    public long lastActiveAt = 0L;
    public long lastLoginAt = 0L;
    public long lastStudyAt = 0L;
    public long deletedAt = 0L;

    public boolean isAdmin() {
        return FirebaseUserStore.ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isLocked() {
        return FirebaseUserStore.STATUS_LOCKED.equalsIgnoreCase(status)
                || FirebaseUserStore.STATUS_DELETED.equalsIgnoreCase(status);
    }

    public boolean isDeleted() {
        return FirebaseUserStore.STATUS_DELETED.equalsIgnoreCase(status);
    }

    public long getLastSeenAt() {
        return Math.max(lastActiveAt, Math.max(lastStudyAt, Math.max(lastLoginAt, updatedAt)));
    }

    public boolean isActiveNow(long now) {
        long lastSeen = getLastSeenAt();
        return isOnline && lastSeen > 0L && (now - lastSeen) <= ONLINE_WINDOW_MS;
    }
}
