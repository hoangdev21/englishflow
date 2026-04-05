package com.example.englishflow.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FirebaseUserStore {

    private static final String TAG = "FirebaseUserStore";

    public interface SimpleCallback {
        void onComplete(boolean success, @Nullable String message);
    }

    public interface ProfileCallback {
        void onResult(@Nullable CloudUserProfile profile);
    }

    public interface UsersCallback {
        void onResult(@NonNull List<CloudUserProfile> users);
    }

    public interface UsersSubscription {
        void remove();
    }

    public interface AdminStatsCallback {
        void onResult(@NonNull AdminStats stats);
    }

    public interface AccessLogsCallback {
        void onResult(@NonNull List<AccessLogEntry> entries);
    }

    public interface AdminNotificationsCallback {
        void onResult(@NonNull List<AdminNotificationEntry> entries);
    }

    public interface LeaderboardCallback {
        void onResult(@NonNull List<LeaderboardItem> items);
    }

    public interface NotificationSubscription {
        void remove();
    }

    public static class AccessLogEntry {
        public String uid = "";
        public String email = "";
        public String displayName = "";
        public long loginAt = 0L;
        public String provider = "";
    }

    public static class AdminStats {
        public int totalUsers;
        public int dailyActiveUsers;
        public int averageXp;
        public int totalDailyXp;
        public int totalAdmins;
    }

    public static class AdminNotificationEntry {
        public String id = "";
        public String title = "";
        public String message = "";
        public String targetType = "all";
        public String targetUid = "";
        public String targetEmail = "";
        public String createdByUid = "";
        public String createdByName = "";
        public long createdAt = 0L;
    }

    public static final String ROLE_USER = "user";
    public static final String ROLE_ADMIN = "admin";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_LOCKED = "locked";
    public static final String STATUS_DELETED = "deleted";

    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_ACCESS_LOGS = "access_logs";
    private static final String COLLECTION_ADMIN_NOTIFICATIONS = "admin_notifications";

    private static final String NOTIFICATION_TARGET_ALL = "all";
    private static final String NOTIFICATION_TARGET_USER = "user";

    private final FirebaseFirestore firestore;

    public FirebaseUserStore() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void getOrCreateProfile(
            @NonNull FirebaseUser user,
            @Nullable String fallbackDisplayName,
            @Nullable String fallbackPhotoUrl,
            @NonNull ProfileCallback callback
    ) {
        String uid = user.getUid();
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        CloudUserProfile profile = parseProfile(snapshot);
                        callback.onResult(profile);
                        return;
                    }

                    String email = safeString(user.getEmail());
                    String displayName = nonEmpty(
                            safeString(fallbackDisplayName),
                            safeString(user.getDisplayName()),
                            emailFromAddress(email),
                            "Learner"
                    );
                    String photoUrl = nonEmpty(
                            safeString(fallbackPhotoUrl),
                            user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : ""
                    );

                    Map<String, Object> payload = baseProfilePayload(uid, email, displayName, photoUrl);
                    firestore.collection(COLLECTION_USERS)
                            .document(uid)
                            .set(payload, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                CloudUserProfile created = new CloudUserProfile();
                                created.uid = uid;
                                created.email = email;
                                created.displayName = displayName;
                                created.photoUrl = photoUrl;
                                created.role = ROLE_USER;
                                created.status = STATUS_ACTIVE;
                                callback.onResult(created);
                            })
                            .addOnFailureListener(e -> callback.onResult(null));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    public void fetchProfile(@NonNull String uid, @NonNull ProfileCallback callback) {
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> callback.onResult(snapshot.exists() ? parseProfile(snapshot) : null))
                .addOnFailureListener(e -> callback.onResult(null));
    }

    public void updateDisplayName(@NonNull String uid, @NonNull String displayName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    public void updateDisplayNameByAdmin(
            @NonNull String uid,
            @NonNull String displayName,
            @NonNull SimpleCallback callback
    ) {
        String safeDisplayName = safeString(displayName);
        if (safeDisplayName.isEmpty()) {
            callback.onComplete(false, "Tên hiển thị không được để trống");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", safeDisplayName);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void updateAvatarUrl(@NonNull String uid, @NonNull String photoUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("photoUrl", photoUrl);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    public void syncUserProgress(
            @NonNull String uid,
            @NonNull String displayName,
            int totalXp,
            int xpToday,
            int learnedWords,
            int currentStreak,
            int bestStreak,
            int totalWordsScanned,
            int totalStudyMinutes,
            long localLastStudyAtMs
    ) {
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(firestore.collection(COLLECTION_USERS).document(uid));
            long serverTotalXp = 0L;
            if (snapshot.exists()) {
                Long val = snapshot.getLong("totalXp");
                if (val != null) serverTotalXp = val;
            }

            long now = System.currentTimeMillis();
            String todayDayKey = getDayKey(now);
            String serverDayKey = safeString(snapshot.get("xpTodayDayKey"));
            int serverXpToday = todayDayKey.equals(serverDayKey) ? safeInt(snapshot.get("xpToday")) : 0;
            long serverUpdatedAtMs = safeLong(snapshot.get("updatedAtMs"));
            int serverCurrentStreak = safeInt(snapshot.get("currentStreak"));
            int serverBestStreak = safeInt(snapshot.get("bestStreak"));
            long serverLastStudyAtMs = safeLong(snapshot.get("lastStudyAt"));

            long normalizedLocalLastStudyAtMs = Math.max(0L, localLastStudyAtMs);

            boolean clientHasTodayActivity = !isDifferentDay(normalizedLocalLastStudyAtMs, now);
            int clientXpToday = clientHasTodayActivity ? Math.max(0, xpToday) : 0;
            int mergedXpToday;
            if (!todayDayKey.equals(serverDayKey)) {
                // Server day changed or not initialized yet: adopt today's local value.
                mergedXpToday = clientXpToday;
            } else if (clientHasTodayActivity && normalizedLocalLastStudyAtMs > serverUpdatedAtMs) {
                // Local progress is newer than cloud snapshot, keep the higher value.
                mergedXpToday = Math.max(serverXpToday, clientXpToday);
            } else {
                // Cloud is newer or equal: avoid stale local overwrite.
                mergedXpToday = serverXpToday;
            }

            int mergedCurrentStreak;
            if (normalizedLocalLastStudyAtMs > serverLastStudyAtMs) {
                mergedCurrentStreak = Math.max(0, currentStreak);
            } else if (serverLastStudyAtMs > normalizedLocalLastStudyAtMs) {
                mergedCurrentStreak = Math.max(0, serverCurrentStreak);
            } else {
                mergedCurrentStreak = Math.max(Math.max(0, currentStreak), Math.max(0, serverCurrentStreak));
            }

            int mergedBestStreak = Math.max(Math.max(0, bestStreak), Math.max(0, serverBestStreak));

            Map<String, Object> updates = new HashMap<>();
            updates.put("displayName", displayName);
            
            // Only update totalXp if client value is higher (prevents overwriting admin grants)
            if (totalXp > serverTotalXp) {
                updates.put("totalXp", totalXp);
            }

            updates.put("xpToday", mergedXpToday);
            updates.put("xpTodayDayKey", todayDayKey);
            
            updates.put("learnedWords", Math.max(0, learnedWords));
            updates.put("currentStreak", mergedCurrentStreak);
            updates.put("bestStreak", mergedBestStreak);
            updates.put("totalWordsScanned", Math.max(0, totalWordsScanned));
            updates.put("totalStudyMinutes", Math.max(0, totalStudyMinutes));
            if (normalizedLocalLastStudyAtMs > serverLastStudyAtMs && normalizedLocalLastStudyAtMs > 0L) {
                updates.put("lastStudyAt", new Timestamp(new java.util.Date(normalizedLocalLastStudyAtMs)));
            }
            updates.put("updatedAt", FieldValue.serverTimestamp());
            updates.put("updatedAtMs", now);

            transaction.set(firestore.collection(COLLECTION_USERS).document(uid), updates, SetOptions.merge());
            return null;
        }).addOnFailureListener(e -> Log.e(TAG, "syncUserProgress transaction failed", e));
    }

    public void updateRole(@NonNull String uid, @NonNull String role, @NonNull SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", role);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void updateStatus(@NonNull String uid, @NonNull String status, @NonNull SimpleCallback callback) {
        String safeStatus = nonEmpty(status, STATUS_ACTIVE).toLowerCase(Locale.US);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", safeStatus);
        if (STATUS_LOCKED.equals(safeStatus) || STATUS_DELETED.equals(safeStatus)) {
            updates.put("isOnline", false);
        }
        if (STATUS_DELETED.equals(safeStatus)) {
            updates.put("deletedAt", FieldValue.serverTimestamp());
        }
        updates.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void softDeleteUser(@NonNull String uid, @NonNull SimpleCallback callback) {
        updateStatus(uid, STATUS_DELETED, callback);
    }

    public void sendPasswordResetForUser(@NonNull String email, @NonNull SimpleCallback callback) {
        String safeEmail = safeString(email);
        if (safeEmail.isEmpty()) {
            callback.onComplete(false, "Email không hợp lệ");
            return;
        }
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(safeEmail)
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void updatePresence(@NonNull String uid, boolean isOnline) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", isOnline);
        updates.put("lastActiveAt", FieldValue.serverTimestamp());
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    public void rewardXp(@NonNull String uid, int delta, @NonNull SimpleCallback callback) {
        int reward = Math.max(0, delta);
        if (reward == 0) {
            callback.onComplete(true, null);
            return;
        }

        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(firestore.collection(COLLECTION_USERS).document(uid));
            long now = System.currentTimeMillis();
            String todayDayKey = getDayKey(now);
            String serverDayKey = safeString(snapshot.get("xpTodayDayKey"));
            int serverXpToday = todayDayKey.equals(serverDayKey) ? safeInt(snapshot.get("xpToday")) : 0;

            Map<String, Object> updates = new HashMap<>();
            updates.put("totalXp", FieldValue.increment(reward));
            updates.put("xpToday", serverXpToday + reward);
            updates.put("xpTodayDayKey", todayDayKey);
            updates.put("updatedAtMs", now);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(firestore.collection(COLLECTION_USERS).document(uid), updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void rewardXpByAdmin(
            @NonNull String adminUid,
            @NonNull String adminName,
            @NonNull String targetUid,
            int delta,
            @NonNull SimpleCallback callback
    ) {
        int reward = Math.max(0, delta);
        if (reward <= 0) {
            callback.onComplete(false, "Số điểm XP cấp phải lớn hơn 0");
            return;
        }

        // 1. Increment XP atomically with a dedicated day key (prevents stale old-day XP carryover)
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(firestore.collection(COLLECTION_USERS).document(targetUid));
            long now = System.currentTimeMillis();
            String todayDayKey = getDayKey(now);
            String serverDayKey = safeString(snapshot.get("xpTodayDayKey"));
            int serverXpToday = todayDayKey.equals(serverDayKey) ? safeInt(snapshot.get("xpToday")) : 0;

            Map<String, Object> updates = new HashMap<>();
            updates.put("totalXp", FieldValue.increment(reward));
            updates.put("xpToday", serverXpToday + reward);
            updates.put("xpTodayDayKey", todayDayKey);
            updates.put("updatedAtMs", now);
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.set(firestore.collection(COLLECTION_USERS).document(targetUid), updates, SetOptions.merge());
            return null;
        }).addOnSuccessListener(unused -> {
            // 2. Create notification entry for the user
            writeAdminNotification(
                    adminUid,
                    adminName,
                    "Chúc mừng! Bạn đã được nhận điểm thưởng",
                    "Admin " + adminName + " đã cấp cho bạn " + reward + " XP vào tài khoản.",
                    NOTIFICATION_TARGET_USER,
                    targetUid,
                    "",
                    callback
            );
        }).addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    public void fetchUsers(@NonNull UsersCallback callback) {
        firestore.collection(COLLECTION_USERS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(500)
                .get()
                .addOnSuccessListener(query -> callback.onResult(parseUsers(query)))
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    @NonNull
    public UsersSubscription observeUsers(@NonNull UsersCallback callback) {
        ListenerRegistration registration = firestore.collection(COLLECTION_USERS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(500)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "observeUsers failed", error);
                        callback.onResult(new ArrayList<>());
                        return;
                    }
                    callback.onResult(parseUsers(snapshot));
                });

        return () -> {
            if (registration != null) {
                registration.remove();
            }
        };
    }

    public void fetchAdminStats(@NonNull AdminStatsCallback callback) {
        fetchUsers(users -> {
            AdminStats stats = new AdminStats();
            if (users.isEmpty()) {
                callback.onResult(stats);
                return;
            }

            stats.totalUsers = users.size();
            int sumXp = 0;
            int totalDailyXp = 0;
            long startOfDay = startOfDayMillis(System.currentTimeMillis());
            for (CloudUserProfile profile : users) {
                sumXp += Math.max(0, profile.totalXp);
                totalDailyXp += Math.max(0, profile.xpToday);
                long lastSeen = Math.max(profile.lastLoginAt, profile.lastActiveAt);
                if (lastSeen >= startOfDay) {
                    stats.dailyActiveUsers++;
                }
                if (ROLE_ADMIN.equalsIgnoreCase(profile.role)) {
                    stats.totalAdmins++;
                }
            }
            stats.averageXp = Math.round((float) sumXp / Math.max(1, users.size()));
            stats.totalDailyXp = totalDailyXp;
            callback.onResult(stats);
        });
    }

    public void sendAdminNotification(
            @NonNull String adminUid,
            @NonNull String adminName,
            @NonNull String title,
            @NonNull String message,
            @Nullable String targetEmail,
            @NonNull SimpleCallback callback
    ) {
        String safeTitle = safeString(title);
        String safeMessage = safeString(message);
        String safeTargetEmail = safeString(targetEmail).toLowerCase(Locale.US);

        if (safeTitle.isEmpty() || safeMessage.isEmpty()) {
            callback.onComplete(false, "Tiêu đề và nội dung không được để trống");
            return;
        }

        if (safeTargetEmail.isEmpty()) {
            writeAdminNotification(
                    adminUid,
                    adminName,
                    safeTitle,
                    safeMessage,
                    NOTIFICATION_TARGET_ALL,
                    "",
                    "",
                    callback
            );
            return;
        }

        fetchUsers(users -> {
            CloudUserProfile target = null;
            for (CloudUserProfile profile : users) {
                if (safeTargetEmail.equalsIgnoreCase(safeString(profile.email))) {
                    target = profile;
                    break;
                }
            }

            if (target == null) {
                callback.onComplete(false, "Không tìm thấy người dùng với email này");
                return;
            }

            writeAdminNotification(
                    adminUid,
                    adminName,
                    safeTitle,
                    safeMessage,
                    NOTIFICATION_TARGET_USER,
                    target.uid,
                    safeTargetEmail,
                    callback
            );
        });
    }

    public void fetchRecentAdminNotifications(int limit, @NonNull AdminNotificationsCallback callback) {
        int safeLimit = Math.max(5, Math.min(100, limit));
        firestore.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .addOnSuccessListener(query -> {
                    List<AdminNotificationEntry> entries = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        entries.add(parseAdminNotification(snapshot));
                    }
                    callback.onResult(entries);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    public void fetchUserNotifications(@NonNull String uid, int limit, @NonNull AdminNotificationsCallback callback) {
        int safeLimit = Math.max(5, Math.min(100, limit));

        fetchNotificationsByTarget("targetType", NOTIFICATION_TARGET_ALL, safeLimit, allEntries ->
                fetchNotificationsByTarget("targetUid", uid, safeLimit, userEntries -> {
                    List<AdminNotificationEntry> merged = new ArrayList<>(allEntries);
                    merged.addAll(userEntries);
                    callback.onResult(sortAndDistinctNotifications(merged, safeLimit));
                })
        );
    }

    @NonNull
    public NotificationSubscription observeUserNotifications(
            @NonNull String uid,
            int limit,
            @NonNull AdminNotificationsCallback callback
    ) {
        int safeLimit = Math.max(5, Math.min(100, limit));

        List<AdminNotificationEntry> allEntries = new ArrayList<>();
        List<AdminNotificationEntry> userEntries = new ArrayList<>();
        Object lock = new Object();

        ListenerRegistration allRegistration = observeNotificationsByTarget(
                "targetType",
                NOTIFICATION_TARGET_ALL,
                safeLimit,
                lock,
                allEntries,
                userEntries,
                callback
        );

        ListenerRegistration userRegistration = observeNotificationsByTarget(
                "targetUid",
                uid,
                safeLimit,
                lock,
                userEntries,
                allEntries,
                callback
        );

        return () -> {
            if (allRegistration != null) {
                allRegistration.remove();
            }
            if (userRegistration != null) {
                userRegistration.remove();
            }
        };
    }

    private void fetchNotificationsByTarget(
            @NonNull String field,
            @NonNull String value,
            int safeLimit,
            @NonNull AdminNotificationsCallback callback
    ) {
        Query baseQuery = firestore.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .whereEqualTo(field, value);

        baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .addOnSuccessListener(query -> callback.onResult(parseNotifications(query)))
                .addOnFailureListener(error -> {
                    if (!shouldFallbackToUnorderedQuery(error)) {
                        Log.w(TAG, "Notification query failed for " + field + "=" + value, error);
                        callback.onResult(new ArrayList<>());
                        return;
                    }

                    // Composite index can be missing in production; fallback keeps notifications visible.
                    baseQuery.get()
                            .addOnSuccessListener(fallbackQuery -> callback.onResult(parseNotifications(fallbackQuery)))
                            .addOnFailureListener(fallbackError -> {
                                Log.w(TAG, "Notification fallback query failed for " + field + "=" + value, fallbackError);
                                callback.onResult(new ArrayList<>());
                            });
                });
    }

    @NonNull
    private ListenerRegistration observeNotificationsByTarget(
            @NonNull String field,
            @NonNull String value,
            int safeLimit,
            @NonNull Object lock,
            @NonNull List<AdminNotificationEntry> targetEntries,
            @NonNull List<AdminNotificationEntry> siblingEntries,
            @NonNull AdminNotificationsCallback callback
    ) {
        Query baseQuery = firestore.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .whereEqualTo(field, value);

        Query indexedQuery = baseQuery.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(safeLimit);

        final ListenerRegistration[] currentRegistration = new ListenerRegistration[1];
        final boolean[] fallbackActive = new boolean[]{false};

        currentRegistration[0] = indexedQuery.addSnapshotListener((snapshot, error) -> {
            if (error != null && shouldFallbackToUnorderedQuery(error) && !fallbackActive[0]) {
                fallbackActive[0] = true;
                ListenerRegistration registration = currentRegistration[0];
                if (registration != null) {
                    registration.remove();
                }

                currentRegistration[0] = baseQuery.addSnapshotListener((fallbackSnapshot, fallbackError) ->
                        updateObservedNotifications(
                                lock,
                                targetEntries,
                                siblingEntries,
                                fallbackSnapshot,
                                fallbackError,
                                safeLimit,
                                callback,
                                field + ":fallback"
                        )
                );
                return;
            }

            updateObservedNotifications(
                    lock,
                    targetEntries,
                    siblingEntries,
                    snapshot,
                    error,
                    safeLimit,
                    callback,
                    field + (fallbackActive[0] ? ":fallback" : ":indexed")
            );
        });

        return () -> {
            ListenerRegistration registration = currentRegistration[0];
            if (registration != null) {
                registration.remove();
            }
        };
    }

    private void updateObservedNotifications(
            @NonNull Object lock,
            @NonNull List<AdminNotificationEntry> targetEntries,
            @NonNull List<AdminNotificationEntry> siblingEntries,
            @Nullable QuerySnapshot snapshot,
            @Nullable FirebaseFirestoreException error,
            int safeLimit,
            @NonNull AdminNotificationsCallback callback,
            @NonNull String source
    ) {
        synchronized (lock) {
            targetEntries.clear();
            if (error != null) {
                Log.w(TAG, "Notification observer error from " + source, error);
            } else if (snapshot != null) {
                targetEntries.addAll(parseNotifications(snapshot));
            }

            List<AdminNotificationEntry> merged = new ArrayList<>(targetEntries);
            merged.addAll(siblingEntries);
            callback.onResult(sortAndDistinctNotifications(merged, safeLimit));
        }
    }

    @NonNull
    private List<AdminNotificationEntry> parseNotifications(@Nullable QuerySnapshot snapshot) {
        List<AdminNotificationEntry> entries = new ArrayList<>();
        if (snapshot == null) {
            return entries;
        }

        for (DocumentSnapshot document : snapshot.getDocuments()) {
            entries.add(parseAdminNotification(document));
        }
        return entries;
    }

    private boolean shouldFallbackToUnorderedQuery(@Nullable Exception error) {
        if (!(error instanceof FirebaseFirestoreException)) {
            return false;
        }
        FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
        return firestoreError.getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION;
    }

    public void fetchLeaderboard(@NonNull String period, @NonNull LeaderboardCallback callback) {
        String scoreField = "today".equalsIgnoreCase(period) ? "xpToday" : "totalXp";

        firestore.collection(COLLECTION_USERS)
                .orderBy(scoreField, Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(query -> {
                    List<LeaderboardItem> items = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        CloudUserProfile profile = parseProfile(snapshot);
                        if (STATUS_LOCKED.equalsIgnoreCase(profile.status)) {
                            continue;
                        }
                        int score = "today".equalsIgnoreCase(period) ? profile.xpToday : profile.totalXp;
                        items.add(new LeaderboardItem(profile.displayName, profile.email, score, 0, profile.photoUrl));
                    }

                    Collections.sort(items, (a, b) -> Integer.compare(b.score, a.score));
                    for (int i = 0; i < items.size(); i++) {
                        items.get(i).rank = i + 1;
                    }
                    callback.onResult(items);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    public void recordAccessLog(@NonNull CloudUserProfile profile, @NonNull String provider) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", profile.uid);
        payload.put("email", profile.email);
        payload.put("displayName", profile.displayName);
        payload.put("provider", provider);
        payload.put("loginAt", FieldValue.serverTimestamp());

        Map<String, Object> profilePatch = new HashMap<>();
        profilePatch.put("lastLoginAt", FieldValue.serverTimestamp());
        profilePatch.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_ACCESS_LOGS)
                .add(payload);
        firestore.collection(COLLECTION_USERS)
                .document(profile.uid)
                .set(profilePatch, SetOptions.merge());
    }

    public void fetchRecentAccessLogs(int limit, @NonNull AccessLogsCallback callback) {
        int safeLimit = Math.max(5, Math.min(100, limit));
        firestore.collection(COLLECTION_ACCESS_LOGS)
                .orderBy("loginAt", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .addOnSuccessListener(query -> {
                    List<AccessLogEntry> entries = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        AccessLogEntry entry = new AccessLogEntry();
                        entry.uid = safeString(snapshot.getString("uid"));
                        entry.email = safeString(snapshot.getString("email"));
                        entry.displayName = safeString(snapshot.getString("displayName"));
                        entry.provider = safeString(snapshot.getString("provider"));
                        entry.loginAt = timestampToMillis(snapshot.getTimestamp("loginAt"));
                        entries.add(entry);
                    }
                    callback.onResult(entries);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    @NonNull
    private List<CloudUserProfile> parseUsers(@Nullable QuerySnapshot query) {
        List<CloudUserProfile> users = new ArrayList<>();
        if (query == null) {
            return users;
        }
        for (DocumentSnapshot doc : query.getDocuments()) {
            users.add(parseProfile(doc));
        }
        return users;
    }

    private void writeAdminNotification(
            @NonNull String adminUid,
            @NonNull String adminName,
            @NonNull String title,
            @NonNull String message,
            @NonNull String targetType,
            @NonNull String targetUid,
            @NonNull String targetEmail,
            @NonNull SimpleCallback callback
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("message", message);
        payload.put("targetType", targetType);
        payload.put("targetUid", safeString(targetUid));
        payload.put("targetEmail", safeString(targetEmail));
        payload.put("createdByUid", safeString(adminUid));
        payload.put("createdByName", nonEmpty(adminName, "Admin"));
        payload.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .add(payload)
                .addOnSuccessListener(unused -> callback.onComplete(true, null))
                .addOnFailureListener(e -> callback.onComplete(false, e.getMessage()));
    }

    private AdminNotificationEntry parseAdminNotification(@NonNull DocumentSnapshot snapshot) {
        AdminNotificationEntry entry = new AdminNotificationEntry();
        entry.id = nonEmpty(snapshot.getId());
        entry.title = safeString(snapshot.getString("title"));
        entry.message = safeString(snapshot.getString("message"));
        entry.targetType = nonEmpty(snapshot.getString("targetType"), NOTIFICATION_TARGET_ALL);
        entry.targetUid = safeString(snapshot.getString("targetUid"));
        entry.targetEmail = safeString(snapshot.getString("targetEmail"));
        entry.createdByUid = safeString(snapshot.getString("createdByUid"));
        entry.createdByName = safeString(snapshot.getString("createdByName"));
        entry.createdAt = timestampToMillis(snapshot.getTimestamp("createdAt"));
        return entry;
    }

    private List<AdminNotificationEntry> sortAndDistinctNotifications(
            @NonNull List<AdminNotificationEntry> source,
            int limit
    ) {
        Map<String, AdminNotificationEntry> dedup = new LinkedHashMap<>();
        for (AdminNotificationEntry item : source) {
            if (item == null) {
                continue;
            }
            String key = nonEmpty(item.id);
            if (key.isEmpty()) {
                continue;
            }
            if (!dedup.containsKey(key)) {
                dedup.put(key, item);
            }
        }

        List<AdminNotificationEntry> merged = new ArrayList<>(dedup.values());
        Collections.sort(merged, (left, right) -> Long.compare(right.createdAt, left.createdAt));
        if (merged.size() > limit) {
            return new ArrayList<>(merged.subList(0, limit));
        }
        return merged;
    }

    private Map<String, Object> baseProfilePayload(
            @NonNull String uid,
            @NonNull String email,
            @NonNull String displayName,
            @NonNull String photoUrl
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", uid);
        data.put("email", email);
        data.put("displayName", displayName);
        data.put("photoUrl", photoUrl);
        data.put("role", ROLE_USER);
        data.put("status", STATUS_ACTIVE);
        data.put("isOnline", false);
        data.put("totalXp", 0);
        data.put("xpToday", 0);
        data.put("xpTodayDayKey", getDayKey(System.currentTimeMillis()));
        data.put("learnedWords", 0);
        data.put("currentStreak", 0);
        data.put("bestStreak", 0);
        data.put("totalWordsScanned", 0);
        data.put("totalStudyMinutes", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("lastActiveAt", FieldValue.serverTimestamp());
        data.put("lastLoginAt", FieldValue.serverTimestamp());
        return data;
    }

    private CloudUserProfile parseProfile(DocumentSnapshot snapshot) {
        CloudUserProfile profile = new CloudUserProfile();
        profile.uid = nonEmpty(snapshot.getString("uid"), snapshot.getId());
        profile.email = safeString(snapshot.getString("email"));
        profile.displayName = nonEmpty(
                snapshot.getString("displayName"),
                emailFromAddress(profile.email),
                "Learner"
        );
        profile.photoUrl = safeString(snapshot.getString("photoUrl"));
        profile.role = nonEmpty(snapshot.getString("role"), ROLE_USER).toLowerCase();
        profile.status = nonEmpty(snapshot.getString("status"), STATUS_ACTIVE).toLowerCase();
        profile.isOnline = asBoolean(snapshot.get("isOnline"));

        profile.totalXp = asInt(snapshot.get("totalXp"));
        String todayDayKey = getDayKey(System.currentTimeMillis());
        String xpTodayDayKey = safeString(snapshot.get("xpTodayDayKey"));
        boolean xpTodayIsCurrentDay;
        if (!xpTodayDayKey.isEmpty()) {
            xpTodayIsCurrentDay = todayDayKey.equals(xpTodayDayKey);
        } else {
            long updatedAtMs = safeLong(snapshot.get("updatedAtMs"));
            xpTodayIsCurrentDay = !isDifferentDay(updatedAtMs, System.currentTimeMillis());
        }
        profile.xpToday = xpTodayIsCurrentDay ? asInt(snapshot.get("xpToday")) : 0;
        profile.learnedWords = asInt(snapshot.get("learnedWords"));
        profile.currentStreak = asInt(snapshot.get("currentStreak"));
        profile.bestStreak = asInt(snapshot.get("bestStreak"));
        profile.totalWordsScanned = asInt(snapshot.get("totalWordsScanned"));
        profile.totalStudyMinutes = asInt(snapshot.get("totalStudyMinutes"));

        profile.createdAt = fieldToMillis(snapshot, "createdAt");
        profile.updatedAt = fieldToMillis(snapshot, "updatedAt");
        profile.lastActiveAt = fieldToMillis(snapshot, "lastActiveAt");
        profile.lastLoginAt = fieldToMillis(snapshot, "lastLoginAt");
        profile.lastStudyAt = fieldToMillis(snapshot, "lastStudyAt");
        profile.deletedAt = fieldToMillis(snapshot, "deletedAt");
        return profile;
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return false;
    }

    private long fieldToMillis(@NonNull DocumentSnapshot snapshot, @NonNull String field) {
        Timestamp timestamp = snapshot.getTimestamp(field);
        if (timestamp != null) {
            return timestampToMillis(timestamp);
        }
        Object raw = snapshot.get(field);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        return 0L;
    }

    private long timestampToMillis(@Nullable Timestamp timestamp) {
        return timestamp == null ? 0L : timestamp.toDate().getTime();
    }

    private long startOfDayMillis(long now) {
        long oneDayMs = TimeUnit.DAYS.toMillis(1);
        return (now / oneDayMs) * oneDayMs;
    }

    private String safeString(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private String emailFromAddress(@Nullable String email) {
        String safeEmail = safeString(email);
        int atIndex = safeEmail.indexOf('@');
        if (atIndex <= 0) {
            return "";
        }
        return safeEmail.substring(0, atIndex);
    }

    private String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String candidate = safeString(value);
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    private boolean isDifferentDay(long oldTime, long newTime) {
        if (oldTime <= 0) return true;
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(oldTime);
        cal2.setTimeInMillis(newTime);
        return cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
               cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR);
    }

    @NonNull
    private String getDayKey(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
    }

    private int safeInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            String s = safeString(value != null ? value.toString() : "");
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long safeLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) value).toDate().getTime();
        }
        try {
            String s = safeString(value != null ? value.toString() : "");
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String safeString(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }
}

