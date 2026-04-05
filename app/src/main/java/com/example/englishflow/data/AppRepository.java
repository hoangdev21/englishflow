package com.example.englishflow.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import android.content.Context;
import com.example.englishflow.R;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.entity.ChatMessageEntity;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.database.entity.FailedLabelLogEntity;
import com.example.englishflow.database.entity.LearnedWordEntity;
import com.example.englishflow.database.entity.SeedPackageStateEntity;
import com.example.englishflow.database.entity.StudySessionEntity;
import com.example.englishflow.database.entity.UserStatsEntity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AppRepository {
    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_SEED_IMPORTED = "seed_vocabulary_imported";
    private static final String KEY_TOPIC_WORD_SCORES_PREFIX = "topic_word_scores_";
    private static final String KEY_TOPIC_LEARNED_WORDS_PREFIX = "topic_learned_words_";
    private static final String KEY_LAST_TOPIC_TITLE = "last_topic_title";
    private static final String KEY_LAST_TOPIC_DOMAIN = "last_topic_domain";
    private static final String KEY_LAST_TOPIC_REMAINING_CARDS = "last_topic_remaining_cards";
    private static final String KEY_COMPLETED_MAP_NODES = "completed_map_nodes";
    private static final String KEY_TOTAL_MAP_NODES = "total_map_nodes";
    private static final String KEY_FILL_BLANK_STATS_TOTAL_SESSIONS = "fill_blank_stats_total_sessions";
    private static final String KEY_FILL_BLANK_STATS_TOTAL_ATTEMPTS = "fill_blank_stats_total_attempts";
    private static final String KEY_FILL_BLANK_STATS_TOTAL_CORRECT = "fill_blank_stats_total_correct";
    private static final String KEY_FILL_BLANK_STATS_TOTAL_WRONG = "fill_blank_stats_total_wrong";
    private static final String KEY_FILL_BLANK_STATS_TOTAL_XP = "fill_blank_stats_total_xp";
    private static final String KEY_FILL_BLANK_STATS_BEST_COMBO = "fill_blank_stats_best_combo";
    private static final String KEY_FILL_BLANK_STATS_LAST_PLAYED_AT = "fill_blank_stats_last_played_at";
    private static final String KEY_FILL_BLANK_STATS_LAST_TOPIC = "fill_blank_stats_last_topic";
    private static final String KEY_FILL_BLANK_COMPLETED_QUESTIONS = "fill_blank_completed_questions";
    private static final String KEY_FIRESTORE_USER_MIGRATION_PREFIX = "firestore_user_migration_";

    private static final String KEY_FIRESTORE_CUSTOM_MIGRATION = "firestore_custom_vocabulary_migration";
    private static final String KEY_DAILY_XP_CLEANUP_MIGRATION_PREFIX = "daily_xp_cleanup_migration_";
    private static final int DAILY_XP_SUSPICIOUS_THRESHOLD = 2000;
    private static final String SEED_FILE_NAME = "vocab_seed_packages.json";
    private static final String LEGACY_SEED_FILE_NAME = "vocab_seed.json";
    private static final String LEGACY_FLASHCARD_EXAMPLE_PLACEHOLDER = "Bản dịch đang được cập nhật...";
    private static final String LEGACY_FLASHCARD_USAGE_PLACEHOLDER = "Thông tin cách dùng đang được biên soạn cho từ này.";
    private static final int FLASHCARD_BACKGROUND_ENRICH_LIMIT = 4;
    private static final int FLASHCARD_TRANSLATION_CONNECT_TIMEOUT_MS = 2500;
    private static final int FLASHCARD_TRANSLATION_READ_TIMEOUT_MS = 3000;
    private static final int FILL_BLANK_BOOTSTRAP_MAX_RETRY = 6;
    private static final long FILL_BLANK_BOOTSTRAP_RETRY_DELAY_MS = 180L;
    private static final long DASHBOARD_CACHE_TTL_MS = 5000L;
    private static final long DOMAINS_CACHE_TTL_MS = 30000L;
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_LEARNED_WORDS = "learned_words";
    private static final String COLLECTION_STUDY_SESSIONS = "study_sessions";
    private static final String COLLECTION_CUSTOM_VOCABULARY = "custom_vocabulary";
    private static final String COLLECTION_ADMIN_NOTIFICATIONS = "admin_notifications";

    private static final Pattern ADMIN_REWARD_XP_PATTERN = Pattern.compile("(\\d+)\\s*XP", Pattern.CASE_INSENSITIVE);

    private static AppRepository instance;

    private final SharedPreferences preferences;
    private final EnglishFlowDatabase database;
    private final FirebaseUserStore firebaseUserStore;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private final Object cacheLock = new Object();
    private final Object realtimeLock = new Object();
    private final Object dashboardRequestLock = new Object();

    public void executeAsync(Runnable task) {
        if (task != null) {
            executorService.execute(task);
        }
    }

    private boolean dashboardRequestRunning = false;
    private final List<DataCallback<DashboardSnapshot>> pendingDashboardCallbacks = new ArrayList<>();

    private ListenerRegistration learnedWordsRegistration;
    private ListenerRegistration studySessionsRegistration;
    private ListenerRegistration customVocabularyRegistration;
    private ListenerRegistration profileRegistration;

    private String boundRealtimeUid = "";

    private final List<WordEntry> realtimeLearnedWords = new ArrayList<>();
    private final List<StudySession> realtimeStudySessions = new ArrayList<>();
    private final List<CustomVocabularyEntity> realtimeCustomVocabulary = new ArrayList<>();

    private boolean realtimeLearnedReady = false;
    private boolean realtimeSessionsReady = false;
    private boolean realtimeCustomReady = false;

    private final MutableLiveData<List<CustomVocabularyEntity>> customVocabularyLiveData = new MutableLiveData<>(new ArrayList<>());

    private DashboardSnapshot cachedDashboardSnapshot;
    private String cachedDashboardEmail = "";
    private long cachedDashboardAt = 0L;

    private List<DomainItem> cachedDomains = new ArrayList<>();
    private String cachedDomainsEmail = "";
    private long cachedDomainsAt = 0L;

    public static class DashboardSnapshot {
        public String userName = "Bạn";
        public String photoUrl = "";
        public UserProgress userProgress = new UserProgress();
        public int wordsLearnedToday = 0;
        public int dailyWordGoal = 25;
        public int xpGoal = 120;
        public int unlearnedWordsCount = 3000;
        public String lastTopicTitle = "Giao tiếp cơ bản";
        public String lastTopicDomain = "Tổng quát";
        public int lastTopicRemainingCount = 10;
        public int completedMapNodes = 0;
        public int totalMapNodes = 6;
        public int chatSessions = 0;
        public int totalWeeklyMinutes = 0;
        public List<Integer> weeklyStudyMinutes = new ArrayList<>();
        public List<DomainItem> domains = new ArrayList<>();
        public List<AchievementItem> achievements = new ArrayList<>();

        public DashboardSnapshot copy() {
            DashboardSnapshot clone = new DashboardSnapshot();
            clone.userName = userName;
            clone.photoUrl = photoUrl;
            clone.userProgress = cloneUserProgress(userProgress);
            clone.wordsLearnedToday = wordsLearnedToday;
            clone.dailyWordGoal = dailyWordGoal;
            clone.xpGoal = xpGoal;
            clone.unlearnedWordsCount = unlearnedWordsCount;
            clone.lastTopicTitle = lastTopicTitle;
            clone.lastTopicDomain = lastTopicDomain;
            clone.lastTopicRemainingCount = lastTopicRemainingCount;
            clone.completedMapNodes = completedMapNodes;
            clone.totalMapNodes = totalMapNodes;
            clone.chatSessions = chatSessions;
            clone.totalWeeklyMinutes = totalWeeklyMinutes;
            clone.weeklyStudyMinutes = new ArrayList<>(weeklyStudyMinutes);
            clone.domains = new ArrayList<>(domains);
            clone.achievements = new ArrayList<>(achievements);
            return clone;
        }
    }

    public static class SpendXpResult {
        public final boolean success;
        public final int remainingXp;
        public final String message;

        public SpendXpResult(boolean success, int remainingXp, String message) {
            this.success = success;
            this.remainingXp = Math.max(0, remainingXp);
            this.message = message == null ? "" : message;
        }
    }

    public static class FillBlankTopicItem {
        public static final int STATUS_NOT_STARTED = 0;
        public static final int STATUS_IN_PROGRESS = 1;
        public static final int STATUS_COMPLETED = 2;

        public final String domain;
        public final String topic;
        public final int learnedWords;
        public final int questionCount;
        public int completedCount = 0;
        public int status = STATUS_NOT_STARTED;

        public FillBlankTopicItem(String domain, String topic, int learnedWords, int questionCount) {
            this.domain = nonNullOrEmpty(domain, "");
            this.topic = nonNullOrEmpty(topic, "");
            this.learnedWords = Math.max(0, learnedWords);
            this.questionCount = Math.max(0, questionCount);
        }

        private static String nonNullOrEmpty(String value, String fallback) {
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value.trim();
        }
    }

    public static class FillBlankQuestionItem {
        public final String domain;
        public final String topic;
        public final String sourceWord;
        public final String sentence;
        public final String maskedSentence;
        public final String sentenceVi;
        public final String meaningVi;
        public final String expectedAnswer;
        public final Set<String> acceptedAnswers;

        public FillBlankQuestionItem(String domain,
                                     String topic,
                                     String sourceWord,
                                     String sentence,
                                     String maskedSentence,
                                     String sentenceVi,
                                     String meaningVi,
                                     String expectedAnswer,
                                     Set<String> acceptedAnswers) {
            this.domain = nonNullOrEmpty(domain, "");
            this.topic = nonNullOrEmpty(topic, "");
            this.sourceWord = nonNullOrEmpty(sourceWord, "");
            this.sentence = nonNullOrEmpty(sentence, "");
            this.maskedSentence = nonNullOrEmpty(maskedSentence, "");
            this.sentenceVi = nonNullOrEmpty(sentenceVi, "");
            this.meaningVi = nonNullOrEmpty(meaningVi, "");
            this.expectedAnswer = nonNullOrEmpty(expectedAnswer, "");
            this.acceptedAnswers = acceptedAnswers != null
                    ? Collections.unmodifiableSet(new LinkedHashSet<>(acceptedAnswers))
                    : Collections.emptySet();
        }

        private static String nonNullOrEmpty(String value, String fallback) {
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value.trim();
        }
    }

    public static class FillBlankStats {
        public int totalSessions = 0;
        public int totalAttempts = 0;
        public int totalCorrect = 0;
        public int totalWrong = 0;
        public int totalXpEarned = 0;
        public int bestCombo = 0;
        public long lastPlayedAt = 0L;
        public String lastTopic = "";

        public FillBlankStats copy() {
            FillBlankStats clone = new FillBlankStats();
            clone.totalSessions = totalSessions;
            clone.totalAttempts = totalAttempts;
            clone.totalCorrect = totalCorrect;
            clone.totalWrong = totalWrong;
            clone.totalXpEarned = totalXpEarned;
            clone.bestCombo = bestCombo;
            clone.lastPlayedAt = lastPlayedAt;
            clone.lastTopic = lastTopic;
            return clone;
        }
    }

    private static class MaskedSentenceResult {
        final String maskedSentence;
        final String expectedAnswer;

        MaskedSentenceResult(String maskedSentence, String expectedAnswer) {
            this.maskedSentence = maskedSentence;
            this.expectedAnswer = expectedAnswer;
        }
    }

    private static UserProgress cloneUserProgress(UserProgress source) {
        UserProgress clone = new UserProgress();
        if (source == null) {
            return clone;
        }
        clone.totalWordsLearned = source.totalWordsLearned;
        clone.totalWordsScanned = source.totalWordsScanned;
        clone.currentStreak = source.currentStreak;
        clone.bestStreak = source.bestStreak;
        clone.totalXpEarned = source.totalXpEarned;
        clone.xpTodayEarned = source.xpTodayEarned;
        clone.lastStudyDate = source.lastStudyDate;
        clone.totalStudyMinutes = source.totalStudyMinutes;
        clone.cefrLevel = source.cefrLevel;
        clone.setStudySessions(source.getStudySessions());
        return clone;
    }

    private void invalidateDashboardCache() {
        synchronized (cacheLock) {
            cachedDashboardSnapshot = null;
            cachedDashboardEmail = "";
            cachedDashboardAt = 0L;
            cachedDomains = new ArrayList<>();
            cachedDomainsEmail = "";
            cachedDomainsAt = 0L;
        }
    }

    private AppRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        database = EnglishFlowDatabase.getInstance(appContext);
        firebaseUserStore = new FirebaseUserStore();
        firestore = FirebaseFirestore.getInstance();
        startRealtimeSyncIfNeeded();
        importSeedVocabularyIfNeeded();
    }

    public static synchronized AppRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AppRepository(context);
        }
        return instance;
    }

    public EnglishFlowDatabase getDatabase() {
        return database;
    }

    public String getCurrentEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            return user.getEmail().trim().toLowerCase(Locale.US);
        }
        return "guest";
    }

    private String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "";
    }

    private void startRealtimeSyncIfNeeded() {
        ensureRealtimeListenersBound();
    }

    private void ensureRealtimeListenersBound() {
        String currentUid = getCurrentUid();

        synchronized (realtimeLock) {
            if (customVocabularyRegistration == null) {
                customVocabularyRegistration = firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                        .orderBy("updatedAt", Query.Direction.DESCENDING)
                        .addSnapshotListener((snapshot, error) -> {
                            if (error != null || snapshot == null) {
                                return;
                            }

                            List<CustomVocabularyEntity> values = new ArrayList<>();
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                                String word = safeString(doc.get("word"));
                                if (word.isEmpty()) {
                                    word = doc.getId();
                                }
                                if (word.isEmpty()) {
                                    continue;
                                }

                                CustomVocabularyEntity entity = new CustomVocabularyEntity(
                                        word,
                                        safeString(doc.get("meaning")),
                                        safeString(doc.get("ipa")),
                                        safeString(doc.get("example")),
                                        safeString(doc.get("exampleVi")),
                                        safeString(doc.get("usage"))
                                );
                                entity.isLocked = safeBoolean(doc.get("isLocked"));
                                entity.source = nonEmpty(safeString(doc.get("source")), "user");
                                entity.domain = nonEmpty(safeString(doc.get("domain")), "general");
                                entity.updatedAt = safeLong(doc.get("updatedAt"));
                                if (entity.updatedAt <= 0) {
                                    entity.updatedAt = System.currentTimeMillis();
                                }
                                values.add(entity);
                            }

                            synchronized (realtimeLock) {
                                realtimeCustomVocabulary.clear();
                                realtimeCustomVocabulary.addAll(values);
                                realtimeCustomReady = true;
                            }

                            customVocabularyLiveData.postValue(new ArrayList<>(values));
                            invalidateDashboardCache();
                        });
            }

            if (currentUid.equals(boundRealtimeUid)) {
                return;
            }

            if (learnedWordsRegistration != null) {
                learnedWordsRegistration.remove();
                learnedWordsRegistration = null;
            }
            if (studySessionsRegistration != null) {
                studySessionsRegistration.remove();
                studySessionsRegistration = null;
            }
            if (profileRegistration != null) {
                profileRegistration.remove();
                profileRegistration = null;
            }

            realtimeLearnedWords.clear();
            realtimeStudySessions.clear();
            realtimeLearnedReady = false;
            realtimeSessionsReady = false;
            boundRealtimeUid = currentUid;

            if (currentUid.isEmpty()) {
                invalidateDashboardCache();
                return;
            }

            learnedWordsRegistration = firestore.collection(COLLECTION_USERS)
                    .document(currentUid)
                    .collection(COLLECTION_LEARNED_WORDS)
                    .orderBy("learnedAt", Query.Direction.DESCENDING)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) {
                            return;
                        }

                        List<WordEntry> values = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            String word = safeString(doc.get("word"));
                            if (word.isEmpty()) {
                                word = doc.getId();
                            }
                            if (word.isEmpty()) {
                                continue;
                            }

                            values.add(new WordEntry(
                                    word,
                                    safeString(doc.get("ipa")),
                                    safeString(doc.get("meaning")),
                                    nonEmpty(safeString(doc.get("wordType")), "noun"),
                                    safeString(doc.get("example")),
                                    safeString(doc.get("exampleVi")),
                                    safeString(doc.get("usage")),
                                    nonEmpty(safeString(doc.get("domain")), "general"),
                                    safeString(doc.get("note"))
                            ));
                        }

                        synchronized (realtimeLock) {
                            realtimeLearnedWords.clear();
                            realtimeLearnedWords.addAll(values);
                            realtimeLearnedReady = true;
                        }

                        invalidateDashboardCache();
                    });

            studySessionsRegistration = firestore.collection(COLLECTION_USERS)
                    .document(currentUid)
                    .collection(COLLECTION_STUDY_SESSIONS)
                    .orderBy("endTime", Query.Direction.DESCENDING)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) {
                            return;
                        }

                        List<StudySession> values = new ArrayList<>();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            long startTime = safeLong(doc.get("startTime"));
                            long endTime = safeLong(doc.get("endTime"));
                            if (endTime <= 0L) {
                                endTime = startTime;
                            }

                            values.add(new StudySession(
                                    startTime,
                                    endTime,
                                    safeInt(doc.get("wordsLearned")),
                                    safeString(doc.get("domain")),
                                    safeString(doc.get("topic")),
                                    safeInt(doc.get("xpEarned"))
                            ));
                        }

                        synchronized (realtimeLock) {
                            realtimeStudySessions.clear();
                            realtimeStudySessions.addAll(values);
                            realtimeSessionsReady = true;
                        }

                        invalidateDashboardCache();
                    });
            
            profileRegistration = firestore.collection(COLLECTION_USERS)
                    .document(currentUid)
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null || !snapshot.exists()) {
                            return;
                        }

                        int cloudTotalXp = safeInt(snapshot.get("totalXp"));
                        int cloudXpToday = safeInt(snapshot.get("xpToday"));
                        String cloudXpDayKey = safeString(snapshot.get("xpTodayDayKey"));
                        long cloudUpdatedAtMs = safeLong(snapshot.get("updatedAtMs"));
                        String todayKey = getDayKey(System.currentTimeMillis());
                        boolean cloudXpIsToday;
                        if (!cloudXpDayKey.isEmpty()) {
                            cloudXpIsToday = todayKey.equals(cloudXpDayKey);
                        } else {
                            cloudXpIsToday = !isDifferentDay(cloudUpdatedAtMs, System.currentTimeMillis());
                        }
                        int normalizedCloudXpToday = cloudXpIsToday ? Math.max(0, cloudXpToday) : 0;
                        int cloudStreak = safeInt(snapshot.get("currentStreak"));
                        int cloudBestStreak = safeInt(snapshot.get("bestStreak"));
                        long cloudLastStudyAtMs = safeLong(snapshot.get("lastStudyAt"));
                        int cloudLearnedWords = safeInt(snapshot.get("learnedWords"));
                        int cloudScannedWords = safeInt(snapshot.get("totalWordsScanned"));
                        int cloudStudyMinutes = safeInt(snapshot.get("totalStudyMinutes"));
                        String cloudCefr = safeString(snapshot.get("cefrLevel"));

                        executorService.execute(() -> {
                            UserStatsEntity stats = getOrCreateUserStats();
                            if (stats != null) {
                                boolean changed = false;
                                if (cloudTotalXp > stats.totalXpEarned) {
                                    stats.totalXpEarned = cloudTotalXp;
                                    changed = true;
                                }
                                if (normalizedCloudXpToday != stats.xpTodayEarned) {
                                    stats.xpTodayEarned = normalizedCloudXpToday;
                                    changed = true;
                                }

                                if (cloudLastStudyAtMs > 0L) {
                                    if (cloudLastStudyAtMs > stats.lastStudyDate) {
                                        stats.lastStudyDate = cloudLastStudyAtMs;
                                        changed = true;

                                        int normalizedCloudStreak = Math.max(0, cloudStreak);
                                        if (stats.currentStreak != normalizedCloudStreak) {
                                            stats.currentStreak = normalizedCloudStreak;
                                            changed = true;
                                        }
                                    } else if (cloudLastStudyAtMs == stats.lastStudyDate
                                            && cloudStreak > stats.currentStreak) {
                                        stats.currentStreak = cloudStreak;
                                        changed = true;
                                    }
                                } else if (cloudStreak > stats.currentStreak) {
                                    stats.currentStreak = cloudStreak;
                                    changed = true;
                                }

                                if (cloudBestStreak > stats.bestStreak) {
                                    stats.bestStreak = cloudBestStreak;
                                    changed = true;
                                }
                                if (cloudLearnedWords > stats.totalWordsLearned) {
                                    stats.totalWordsLearned = cloudLearnedWords;
                                    changed = true;
                                }
                                if (cloudScannedWords > stats.totalWordsScanned) {
                                    stats.totalWordsScanned = cloudScannedWords;
                                    changed = true;
                                }
                                if (cloudStudyMinutes > stats.totalStudyMinutes) {
                                    stats.totalStudyMinutes = cloudStudyMinutes;
                                    changed = true;
                                }

                                int sessionDerivedStreak = computeRecentStreakFromStudySessions(getCurrentEmail());
                                if (sessionDerivedStreak > stats.currentStreak) {
                                    stats.currentStreak = sessionDerivedStreak;
                                    changed = true;
                                }

                                if (!cloudCefr.isEmpty() && !cloudCefr.equals(stats.cefrLevel)) {
                                    stats.cefrLevel = cloudCefr;
                                    changed = true;
                                }

                                if (stats.currentStreak > stats.bestStreak) {
                                    stats.bestStreak = stats.currentStreak;
                                    changed = true;
                                }

                                if (changed) {
                                    database.userStatsDao().update(stats);
                                    invalidateDashboardCache();
                                }
                            }
                        });
                    });

            migrateLegacyRoomDataToFirestoreIfNeeded(currentUid);
            maybeRunOneTimeDailyXpCleanupMigration(currentUid);
        }
    }

    private List<WordEntry> getRealtimeLearnedWordsCopyIfReady() {
        ensureRealtimeListenersBound();
        synchronized (realtimeLock) {
            if (!realtimeLearnedReady) {
                return null;
            }
            return new ArrayList<>(realtimeLearnedWords);
        }
    }

    private List<StudySession> getRealtimeStudySessionsCopyIfReady() {
        ensureRealtimeListenersBound();
        synchronized (realtimeLock) {
            if (!realtimeSessionsReady) {
                return null;
            }
            return new ArrayList<>(realtimeStudySessions);
        }
    }

    private List<CustomVocabularyEntity> getRealtimeCustomVocabularyCopyIfReady() {
        ensureRealtimeListenersBound();
        synchronized (realtimeLock) {
            if (!realtimeCustomReady) {
                return null;
            }
            return new ArrayList<>(realtimeCustomVocabulary);
        }
    }

    private List<CustomVocabularyEntity> getEffectiveCustomVocabulary() {
        List<CustomVocabularyEntity> cloudValues = getRealtimeCustomVocabularyCopyIfReady();
        if (cloudValues != null) {
            List<CustomVocabularyEntity> merged = new ArrayList<>(cloudValues);
            Set<String> knownWords = new HashSet<>();
            for (CustomVocabularyEntity item : cloudValues) {
                knownWords.add(normalizeWord(item.word));
            }

            List<CustomVocabularyEntity> localValues = database.customVocabularyDao().getAll();
            for (CustomVocabularyEntity local : localValues) {
                String key = normalizeWord(local.word);
                if (!knownWords.contains(key)) {
                    merged.add(local);
                }
            }
            return merged;
        }
        return database.customVocabularyDao().getAll();
    }

    private List<CustomVocabularyEntity> getEffectiveCustomVocabularyByDomain(String domain) {
        List<CustomVocabularyEntity> all = getEffectiveCustomVocabulary();
        List<CustomVocabularyEntity> filtered = new ArrayList<>();
        String normalizedDomain = nonEmpty(domain, "general").trim();
        for (CustomVocabularyEntity entity : all) {
            if (normalizedDomain.equalsIgnoreCase(nonEmpty(entity.domain, "general"))) {
                filtered.add(entity);
            }
        }
        return filtered;
    }

    private void migrateLegacyRoomDataToFirestoreIfNeeded(String uid) {
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        String userMigrationKey = KEY_FIRESTORE_USER_MIGRATION_PREFIX + uid;
        boolean userMigrated = preferences.getBoolean(userMigrationKey, false);
        boolean customMigrated = preferences.getBoolean(KEY_FIRESTORE_CUSTOM_MIGRATION, false);
        if (userMigrated && customMigrated) {
            return;
        }

        executorService.execute(() -> {
            String email = getCurrentEmail();

            if (!preferences.getBoolean(userMigrationKey, false)) {
                List<LearnedWordEntity> learnedWords = database.learnedWordDao().getAllWords(email);
                List<StudySessionEntity> sessions = database.studySessionDao().getAllSessions(email);

                com.google.firebase.firestore.WriteBatch userBatch = firestore.batch();
                int writeCount = 0;

                for (LearnedWordEntity word : learnedWords) {
                    String docId = normalizeWord(word.word);
                    if (docId.isEmpty()) {
                        continue;
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("word", nonEmpty(word.word, docId));
                    payload.put("ipa", nonEmpty(word.ipa, ""));
                    payload.put("meaning", nonEmpty(word.meaning, ""));
                    payload.put("wordType", nonEmpty(word.wordType, "noun"));
                    payload.put("example", nonEmpty(word.example, ""));
                    payload.put("exampleVi", nonEmpty(word.exampleVi, ""));
                    payload.put("usage", nonEmpty(word.usage, ""));
                    payload.put("note", nonEmpty(word.note, ""));
                    payload.put("domain", nonEmpty(word.domain, "general"));
                    payload.put("topic", nonEmpty(word.topic, ""));
                    payload.put("learnedAt", word.learnedAt > 0L ? word.learnedAt : System.currentTimeMillis());
                    payload.put("updatedAt", System.currentTimeMillis());

                    userBatch.set(
                            firestore.collection(COLLECTION_USERS)
                                    .document(uid)
                                    .collection(COLLECTION_LEARNED_WORDS)
                                    .document(docId),
                            payload,
                            com.google.firebase.firestore.SetOptions.merge()
                    );
                    writeCount++;
                }

                for (StudySessionEntity session : sessions) {
                    String docId = "session_" + session.startTime + "_" + Math.abs(nonEmpty(session.topic, "topic").hashCode());

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("startTime", session.startTime);
                    payload.put("endTime", session.endTime);
                    payload.put("wordsLearned", session.wordsLearned);
                    payload.put("domain", nonEmpty(session.domain, ""));
                    payload.put("topic", nonEmpty(session.topic, ""));
                    payload.put("xpEarned", session.xpEarned);
                    payload.put("updatedAt", System.currentTimeMillis());

                    userBatch.set(
                            firestore.collection(COLLECTION_USERS)
                                    .document(uid)
                                    .collection(COLLECTION_STUDY_SESSIONS)
                                    .document(docId),
                            payload,
                            com.google.firebase.firestore.SetOptions.merge()
                    );
                    writeCount++;
                }

                if (writeCount == 0) {
                    preferences.edit().putBoolean(userMigrationKey, true).apply();
                } else {
                    userBatch.commit().addOnSuccessListener(unused ->
                            preferences.edit().putBoolean(userMigrationKey, true).apply()
                    );
                }
            }

            if (!preferences.getBoolean(KEY_FIRESTORE_CUSTOM_MIGRATION, false)) {
                List<CustomVocabularyEntity> localCustom = database.customVocabularyDao().getAll();
                com.google.firebase.firestore.WriteBatch customBatch = firestore.batch();
                int customWrites = 0;

                for (CustomVocabularyEntity item : localCustom) {
                    if (item.word == null || item.word.trim().isEmpty()) {
                        continue;
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("word", item.word);
                    payload.put("meaning", nonEmpty(item.meaning, ""));
                    payload.put("ipa", nonEmpty(item.ipa, "-"));
                    payload.put("example", nonEmpty(item.example, ""));
                    payload.put("exampleVi", nonEmpty(item.exampleVi, ""));
                    payload.put("usage", nonEmpty(item.usage, ""));
                    payload.put("isLocked", item.isLocked);
                    payload.put("source", nonEmpty(item.source, "user"));
                    payload.put("domain", nonEmpty(item.domain, "general"));
                    payload.put("updatedAt", item.updatedAt > 0L ? item.updatedAt : System.currentTimeMillis());

                    customBatch.set(
                            firestore.collection(COLLECTION_CUSTOM_VOCABULARY).document(item.word),
                            payload,
                            com.google.firebase.firestore.SetOptions.merge()
                    );
                    customWrites++;
                }

                if (customWrites == 0) {
                    preferences.edit().putBoolean(KEY_FIRESTORE_CUSTOM_MIGRATION, true).apply();
                } else {
                    customBatch.commit().addOnSuccessListener(unused ->
                            preferences.edit().putBoolean(KEY_FIRESTORE_CUSTOM_MIGRATION, true).apply()
                    );
                }
            }
        });
    }

    private void maybeRunOneTimeDailyXpCleanupMigration(String uid) {
        String safeUid = safeString(uid);
        if (safeUid.isEmpty()) {
            return;
        }

        String migrationKey = KEY_DAILY_XP_CLEANUP_MIGRATION_PREFIX + safeUid;
        if (preferences.getBoolean(migrationKey, false)) {
            return;
        }

        firestore.collection(COLLECTION_USERS)
                .document(safeUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    long now = System.currentTimeMillis();
                    int cloudXpToday = Math.max(0, safeInt(snapshot.get("xpToday")));
                    if (cloudXpToday <= 0) {
                        markDailyXpCleanupMigrationDone(migrationKey);
                        return;
                    }

                    String todayKey = getDayKey(now);
                    String cloudDayKey = safeString(snapshot.get("xpTodayDayKey"));
                    boolean cloudXpIsToday;
                    if (!cloudDayKey.isEmpty()) {
                        cloudXpIsToday = todayKey.equals(cloudDayKey);
                    } else {
                        cloudXpIsToday = !isDifferentDay(safeLong(snapshot.get("updatedAtMs")), now);
                    }

                    if (!cloudXpIsToday) {
                        applyDailyXpCleanupToCloudAndLocal(safeUid, 0, migrationKey);
                        return;
                    }

                    if (cloudXpToday < DAILY_XP_SUSPICIOUS_THRESHOLD) {
                        markDailyXpCleanupMigrationDone(migrationKey);
                        return;
                    }

                    String email = getCurrentEmail();
                    int todayStudyXp = computeTodayStudySessionXp(email, now);
                    fetchTodayAdminRewardXpFromNotifications(safeUid, now, rewardXpToday -> {
                        int reconstructedTodayXp = Math.max(0, todayStudyXp + rewardXpToday);
                        int cleanedXpToday = Math.min(cloudXpToday, reconstructedTodayXp);
                        if (cleanedXpToday <= 0) {
                            cleanedXpToday = 0;
                        }
                        applyDailyXpCleanupToCloudAndLocal(safeUid, cleanedXpToday, migrationKey);
                    });
                });
    }

    private void markDailyXpCleanupMigrationDone(String migrationKey) {
        preferences.edit().putBoolean(migrationKey, true).apply();
    }

    private void fetchTodayAdminRewardXpFromNotifications(String uid, long now, DataCallback<Integer> callback) {
        firestore.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .whereEqualTo("targetUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int totalRewardXp = 0;
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            long createdAt = safeLong(doc.get("createdAt"));
                            if (createdAt <= 0L || isDifferentDay(createdAt, now)) {
                                continue;
                            }
                            int rewardXp = parseRewardXpFromNotification(safeString(doc.get("message")));
                            if (rewardXp > 0) {
                                totalRewardXp += rewardXp;
                            }
                        }
                    }

                    if (callback != null) {
                        callback.onResult(Math.max(0, totalRewardXp));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onResult(0);
                    }
                });
    }

    private int parseRewardXpFromNotification(String message) {
        String safeMessage = safeString(message);
        if (safeMessage.isEmpty()) {
            return 0;
        }

        Matcher matcher = ADMIN_REWARD_XP_PATTERN.matcher(safeMessage);
        if (!matcher.find()) {
            return 0;
        }
        return Math.max(0, safeInt(matcher.group(1)));
    }

    private int computeTodayStudySessionXp(String email, long now) {
        List<StudySessionEntity> sessions = database.studySessionDao().getAllSessions(email);
        int totalXp = 0;
        for (StudySessionEntity session : sessions) {
            long activityAt = session.endTime > 0L ? session.endTime : session.startTime;
            if (activityAt <= 0L || isDifferentDay(activityAt, now)) {
                continue;
            }
            totalXp += Math.max(0, session.xpEarned);
        }
        return Math.max(0, totalXp);
    }

    private void applyDailyXpCleanupToCloudAndLocal(String uid, int cleanedXpToday, String migrationKey) {
        int safeXpToday = Math.max(0, cleanedXpToday);
        long now = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("xpToday", safeXpToday);
        updates.put("xpTodayDayKey", getDayKey(now));
        updates.put("updatedAtMs", now);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    markDailyXpCleanupMigrationDone(migrationKey);
                    executorService.execute(() -> {
                        try {
                            UserStatsEntity stats = getOrCreateUserStats();
                            if (stats != null && stats.xpTodayEarned != safeXpToday) {
                                stats.xpTodayEarned = safeXpToday;
                                database.userStatsDao().update(stats);
                            }
                            invalidateDashboardCache();
                        } catch (Exception ignored) {
                        }
                    });
                });
    }

    private String getPrefKey(String baseKey) {
        return getCurrentEmail() + "_" + baseKey;
    }

    public String getUserName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        return preferences.getString(getPrefKey(KEY_NAME), "Bạn");
    }

    public void setUserName(String name) {
        String safeName = name == null ? "" : name.trim();
        preferences.edit().putString(getPrefKey(KEY_NAME), safeName).apply();

        String uid = getCurrentUid();
        if (!uid.isEmpty() && !safeName.isEmpty()) {
            firebaseUserStore.updateDisplayName(uid, safeName);
        }
    }

    public String getPhotoUrl() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            return user.getPhotoUrl().toString();
        }
        return preferences.getString(getPrefKey("user_photo_url"), "");
    }

    public void setPhotoUrl(String url) {
        String safeUrl = url == null ? "" : url.trim();
        preferences.edit().putString(getPrefKey("user_photo_url"), safeUrl).apply();
        String uid = getCurrentUid();
        if (!uid.isEmpty() && !safeUrl.isEmpty()) {
            firebaseUserStore.updateAvatarUrl(uid, safeUrl);
        }
    }

    public int getReminderHour() {
        return preferences.getInt(getPrefKey(KEY_REMINDER_HOUR), 20);
    }

    public int getReminderMinute() {
        return preferences.getInt(getPrefKey(KEY_REMINDER_MINUTE), 0);
    }

    public void setLastTopicProgress(String title, String domain, int remaining) {
        preferences.edit()
                .putString(getPrefKey(KEY_LAST_TOPIC_TITLE), title)
                .putString(getPrefKey(KEY_LAST_TOPIC_DOMAIN), domain)
                .putInt(getPrefKey(KEY_LAST_TOPIC_REMAINING_CARDS), remaining)
                .apply();
        invalidateDashboardCache();
    }

    public String getLastTopicTitle() {
        String saved = preferences.getString(getPrefKey(KEY_LAST_TOPIC_TITLE), null);
        if (saved != null) return saved;

        List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();
        if (cloudSessions != null && !cloudSessions.isEmpty()) {
            String topic = cloudSessions.get(0).getTopicName();
            if (topic != null && !topic.trim().isEmpty()) {
                return topic;
            }
        }
        
        // Fallback to last session from DB
        StudySessionEntity last = database.studySessionDao().getLastSession(getCurrentEmail());
        if (last != null) return last.topic;
        
        return "Giao tiếp cơ bản"; // Default if clean slate
    }

    public String getLastTopicDomain() {
        String saved = preferences.getString(getPrefKey(KEY_LAST_TOPIC_DOMAIN), null);
        if (saved != null) return saved;

        List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();
        if (cloudSessions != null && !cloudSessions.isEmpty()) {
            String domain = cloudSessions.get(0).getDomainName();
            if (domain != null && !domain.trim().isEmpty()) {
                return domain;
            }
        }
        
        StudySessionEntity last = database.studySessionDao().getLastSession(getCurrentEmail());
        if (last != null) return last.domain;
        
        return "Tổng quát";
    }

    public int getLastTopicRemainingCount() {
        int saved = preferences.getInt(getPrefKey(KEY_LAST_TOPIC_REMAINING_CARDS), -1);
        if (saved != -1) return saved;
        
        return 10; // Default count
    }

    public void setReminderTime(int hour, int minute) {
        preferences.edit().putInt(getPrefKey(KEY_REMINDER_HOUR), hour).putInt(getPrefKey(KEY_REMINDER_MINUTE), minute).apply();
    }

    // ===== User Progress & Statistics (REAL DATA) =====
    
    public UserProgress getUserProgress() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            UserProgress progress = new UserProgress();
            int learnedWords = getLearnedWords();
            progress.totalWordsLearned = learnedWords;
            progress.totalWordsScanned = stats != null ? stats.totalWordsScanned : 0;
            progress.currentStreak = stats != null ? stats.currentStreak : 0;
            progress.bestStreak = stats != null ? stats.bestStreak : 0;
            progress.totalXpEarned = stats != null ? stats.totalXpEarned : 0;
            progress.xpTodayEarned = stats != null ? stats.xpTodayEarned : 0;
            progress.totalStudyMinutes = stats != null ? stats.totalStudyMinutes : 0;
            progress.cefrLevel = getCefrLevelForLearned(learnedWords);
            
            return progress;
        } catch (Exception e) {
            e.printStackTrace();
            return new UserProgress();
        }
    }

    private String pendingTopicDomain = null;
    private String pendingTopicTitle = null;

    public void setPendingTopicRequest(String domain, String title) {
        this.pendingTopicDomain = domain;
        this.pendingTopicTitle = title;
    }

    public boolean hasPendingTopicRequest() {
        return pendingTopicDomain != null && pendingTopicTitle != null;
    }

    public String getPendingTopicDomain() { return pendingTopicDomain; }
    public String getPendingTopicTitle() { return pendingTopicTitle; }

    public void clearPendingTopicRequest() {
        this.pendingTopicDomain = null;
        this.pendingTopicTitle = null;
    }

    public void getUserProgressAsync(DataCallback<UserProgress> callback) {
        executorService.execute(() -> {
            UserProgress progress = getUserProgress();
            mainHandler.post(() -> callback.onResult(progress));
        });
    }

    public DashboardSnapshot getDashboardSnapshot() {
        String email = getCurrentEmail();
        long now = System.currentTimeMillis();

        synchronized (cacheLock) {
            if (cachedDashboardSnapshot != null
                    && email.equals(cachedDashboardEmail)
                    && now - cachedDashboardAt <= DASHBOARD_CACHE_TTL_MS) {
                return cachedDashboardSnapshot.copy();
            }
        }

        DashboardSnapshot snapshot = buildDashboardSnapshot(email);
        synchronized (cacheLock) {
            cachedDashboardSnapshot = snapshot.copy();
            cachedDashboardEmail = email;
            cachedDashboardAt = now;
        }
        return snapshot;
    }

    public void getDashboardSnapshotAsync(DataCallback<DashboardSnapshot> callback) {
        if (callback == null) {
            return;
        }

        synchronized (dashboardRequestLock) {
            pendingDashboardCallbacks.add(callback);
            if (dashboardRequestRunning) {
                return;
            }
            dashboardRequestRunning = true;
        }

        executorService.execute(() -> {
            DashboardSnapshot snapshot = getDashboardSnapshot();
            List<DataCallback<DashboardSnapshot>> callbacks;
            synchronized (dashboardRequestLock) {
                callbacks = new ArrayList<>(pendingDashboardCallbacks);
                pendingDashboardCallbacks.clear();
                dashboardRequestRunning = false;
            }

            mainHandler.post(() -> {
                for (DataCallback<DashboardSnapshot> dataCallback : callbacks) {
                    try {
                        dataCallback.onResult(snapshot.copy());
                    } catch (Exception ignored) {
                    }
                }
            });
        });
    }

    private DashboardSnapshot buildDashboardSnapshot(String email) {
        DashboardSnapshot snapshot = new DashboardSnapshot();
        UserStatsEntity stats = getOrCreateUserStats();
        int learnedWords = getLearnedWords();
        int scannedWords = stats != null ? stats.totalWordsScanned : 0;
        int streak = stats != null ? stats.currentStreak : 0;
        int bestStreak = stats != null ? stats.bestStreak : 0;
        int xpToday = stats != null ? stats.xpTodayEarned : 0;
        int totalXp = stats != null ? stats.totalXpEarned : 0;
        int totalStudyMinutes = stats != null ? stats.totalStudyMinutes : 0;

        int sessionDerivedStreak = computeRecentStreakFromStudySessions(email);
        if (sessionDerivedStreak > streak) {
            streak = sessionDerivedStreak;
        }
        if (streak > bestStreak) {
            bestStreak = streak;
        }

        snapshot.userName = getUserName();
        snapshot.photoUrl = getPhotoUrl();
        snapshot.dailyWordGoal = getDailyWordGoal();
        snapshot.xpGoal = getXpGoal();
        snapshot.wordsLearnedToday = getWordsLearnedToday();
        snapshot.unlearnedWordsCount = Math.max(0, 3000 - learnedWords);
        snapshot.lastTopicTitle = getLastTopicTitle();
        snapshot.lastTopicDomain = getLastTopicDomain();
        snapshot.lastTopicRemainingCount = getLastTopicRemainingCount();
        snapshot.completedMapNodes = getCompletedMapNodeCount();
        snapshot.totalMapNodes = getTotalMapNodeCount();
        snapshot.chatSessions = getChatSessions();
        snapshot.weeklyStudyMinutes = getWeeklyStudyMinutes();
        snapshot.totalWeeklyMinutes = 0;
        for (Integer minutes : snapshot.weeklyStudyMinutes) {
            snapshot.totalWeeklyMinutes += Math.max(0, minutes);
        }
        snapshot.domains = getDomains();
        snapshot.achievements = buildAchievements(streak, learnedWords, scannedWords, snapshot.chatSessions);

        UserProgress progress = new UserProgress();
        progress.totalWordsLearned = learnedWords;
        progress.totalWordsScanned = scannedWords;
        progress.currentStreak = streak;
        progress.bestStreak = bestStreak;
        progress.totalXpEarned = totalXp;
        progress.xpTodayEarned = xpToday;
        progress.totalStudyMinutes = totalStudyMinutes;
        progress.cefrLevel = getCefrLevelForLearned(learnedWords);
        snapshot.userProgress = progress;

        return snapshot;
    }

    public int getStreakDays() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            return stats != null ? stats.currentStreak : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getWordsLearnedToday() {
        try {
            long startOfDay = getStartOfDay(System.currentTimeMillis());

            List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();
            if (cloudSessions != null) {
                int learned = 0;
                for (StudySession session : cloudSessions) {
                    if (session.getEndTime() >= startOfDay) {
                        learned += Math.max(0, session.getWordsLearned());
                    }
                }
                return learned;
            }

            Integer count = database.studySessionDao().getTotalWordsLearnedSince(getCurrentEmail(), startOfDay);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDailyWordGoal() {
        return 25;
    }

    public int getCompletedMapNodeCount() {
        try {
            Set<String> completed = preferences.getStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), new HashSet<>());
            return completed != null ? completed.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getTotalMapNodeCount() {
        int saved = preferences.getInt(getPrefKey(KEY_TOTAL_MAP_NODES), JourneyLessonRepository.DEFAULT_JOURNEY_SIZE);
        return Math.max(1, saved);
    }

    public void setTotalMapNodeCount(int count) {
        int safeCount = Math.max(1, count);
        preferences.edit().putInt(getPrefKey(KEY_TOTAL_MAP_NODES), safeCount).apply();
        invalidateDashboardCache();
    }

    public int getXpToday() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            return stats.xpTodayEarned;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getXpGoal() {
        return 120;
    }

    public int getLearnedWords() {
        try {
            List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
            if (cloudWords != null) {
                return cloudWords.size();
            }

            Integer count = database.learnedWordDao().getTotalWordsCount(getCurrentEmail());
            return count != null ? count : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getScannedImages() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            return stats != null ? stats.totalWordsScanned : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getChatSessions() {
        try {
            return database.chatMessageDao().getMessageCount();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getBestStreak() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            return stats != null ? stats.bestStreak : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getUnlearnedWordsCount() {
        return Math.max(0, 3000 - getLearnedWords()); // Total vocabulary reference
    }

    public List<Integer> getWeeklyStudyMinutes() {
        return buildWeeklyStudyMinutes(getCurrentEmail());
    }

    private List<Integer> buildWeeklyStudyMinutes(String email) {
        List<Integer> weeklyMinutes = new ArrayList<>();
        List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();

        if (cloudSessions != null) {
            try {
                Calendar now = Calendar.getInstance();
                int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                int daysSinceMonday = (currentDayOfWeek + 5) % 7;

                Calendar monday = (Calendar) now.clone();
                monday.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);

                for (int i = 0; i < 7; i++) {
                    if (i > daysSinceMonday) {
                        weeklyMinutes.add(0);
                        continue;
                    }

                    Calendar cal = (Calendar) monday.clone();
                    cal.add(Calendar.DAY_OF_MONTH, i);

                    long startOfDay = getStartOfDay(cal.getTimeInMillis());
                    long endOfDay = getEndOfDay(cal.getTimeInMillis());
                    long durationMs = 0L;
                    for (StudySession session : cloudSessions) {
                        long start = session.getStartTime();
                        long end = session.getEndTime();
                        if (end <= 0L) {
                            end = start;
                        }
                        if (start >= startOfDay && end <= endOfDay) {
                            durationMs += Math.max(0L, end - start);
                        }
                    }

                    int minutes = durationMs > 0L ? (int) Math.max(1, durationMs / 60000L) : 0;
                    weeklyMinutes.add(minutes);
                }
                return weeklyMinutes;
            } catch (Exception ignored) {
                weeklyMinutes.clear();
            }
        }

        try {
            Calendar now = Calendar.getInstance();
            int currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            // Convert to 0-indexed where Monday is 0, Sunday is 6
            int daysSinceMonday = (currentDayOfWeek + 5) % 7; 
            
            // Go back to Monday of this week
            Calendar monday = (Calendar) now.clone();
            monday.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
            
            for (int i = 0; i < 7; i++) {
                // If the queried day is in the future relative to today, just return 0
                if (i > daysSinceMonday) {
                    weeklyMinutes.add(0);
                    continue;
                }

                Calendar cal = (Calendar) monday.clone();
                cal.add(Calendar.DAY_OF_MONTH, i);
                
                long startOfDay = getStartOfDay(cal.getTimeInMillis());
                long endOfDay = getEndOfDay(cal.getTimeInMillis());
                
                try {
                    Long ms = database.studySessionDao().getStudyDurationMsForPeriod(email, startOfDay, endOfDay);
                    // Ensure a day with activity has at least 1 minute to trigger the streak fire icon
                    int minutes = (ms != null && ms > 0) ? (int) Math.max(1, ms / 60000) : 0;
                    weeklyMinutes.add(minutes);
                } catch (Exception e) {
                    weeklyMinutes.add(0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Return 7 zeros if logic fails
            for (int i = 0; i < 7; i++) {
                weeklyMinutes.add(0);
            }
        }
        return weeklyMinutes;
    }

    public List<Integer> getStudySessionsForWeek() {
        return getWeeklyStudyMinutes();
    }

    public int getTotalStudyMinutes() {
        try {
            List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();
            if (cloudSessions != null) {
                long total = 0L;
                for (StudySession session : cloudSessions) {
                    long start = session.getStartTime();
                    long end = session.getEndTime();
                    if (end <= 0L) {
                        end = start;
                    }
                    total += Math.max(0L, end - start);
                }
                return (int) (total / 60000L);
            }

            Long ms = database.studySessionDao().getTotalStudyDurationMs(getCurrentEmail());
            return ms != null ? (int) (ms / 60000) : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<AchievementItem> getAchievements() {
        int streak = getStreakDays();
        int learned = getLearnedWords();
        int scanned = getScannedImages();
        int chat = getChatSessions();

        return buildAchievements(streak, learned, scanned, chat);
    }

    private List<AchievementItem> buildAchievements(int streak, int learned, int scanned, int chat) {
        List<AchievementItem> list = new ArrayList<>();
        list.add(new AchievementItem("7 ngày bền bỉ", "Giữ streak 7 ngày", "🔥", streak >= 7, streak, 7));
        list.add(new AchievementItem("Tân binh scan", "Scan 10 vật thể", "📷", scanned >= 10, scanned, 10));
        list.add(new AchievementItem("100 từ đầu tiên", "Học 100 từ", "📚", learned >= 100, learned, 100));
        list.add(new AchievementItem("Chat pro", "20 cuộc chat", "💬", chat >= 20, chat, 20));
        list.add(new AchievementItem("Ngày bền bỉ", "Vào học mỗi ngày 30 ngày", "⏱️", streak >= 30, streak, 30));
        list.add(new AchievementItem("Hacker từ vựng", "Học 250 từ", "🧠", learned >= 250, learned, 250));
        return list;
    }

    // New internal storage for topic progress (mocking for now since we don't have this table yet)
    // In a real app, this should be a Room table like 'topic_progress'
    private static final java.util.Map<String, String> TOPIC_PROGRESS = new java.util.HashMap<>();

    public void updateTopicStatus(String topic, String status) {
        TOPIC_PROGRESS.put(topic, status);
        invalidateDashboardCache();
        // In real app: database.topicProgressDao().upsert(new TopicProgressEntity(topic, status))
    }

    public String getTopicStatus(String topic) {
        return getTopicStatus(topic, null);
    }

    private String getTopicStatus(String topic, List<CustomVocabularyEntity> domainEntities) {
        int progress = getTopicProgressPercent(topic, domainEntities);
        if (progress >= 100) {
            return TopicItem.STATUS_COMPLETED;
        }
        if (progress > 0) {
            return TopicItem.STATUS_LEARNING;
        }
        return TOPIC_PROGRESS.getOrDefault(topic, TopicItem.STATUS_NOT_STARTED);
    }

    public List<DomainItem> getDomains() {
        String email = getCurrentEmail();
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (!cachedDomains.isEmpty()
                    && email.equals(cachedDomainsEmail)
                    && now - cachedDomainsAt <= DOMAINS_CACHE_TTL_MS) {
                List<DomainItem> cachedCopy = new ArrayList<>(cachedDomains);
                cachedCopy.removeIf(item -> item != null && shouldHideDomainCard(item.getName()));
                return cachedCopy;
            }
        }

        List<DomainItem> list = new ArrayList<>();
        try {
            List<CustomVocabularyEntity> allVocabulary = getEffectiveCustomVocabulary();
            Set<String> domainSet = new LinkedHashSet<>();
            for (CustomVocabularyEntity item : allVocabulary) {
                String domain = nonEmpty(item.domain, "general");
                if (!domain.trim().isEmpty()) {
                    domainSet.add(domain.trim());
                }
            }

            List<String> domains = new ArrayList<>(domainSet);
            for (String domain : domains) {
                if (domain.equalsIgnoreCase("general") || domain.isEmpty() || shouldHideDomainCard(domain)) {
                    continue;
                }

                List<CustomVocabularyEntity> domainEntities = getEffectiveCustomVocabularyByDomain(domain);
                
                String emoji = getEmojiForDomain(domain);
                String start = getGradientStartForDomain(domain);
                String end = getGradientEndForDomain(domain);
                int progress = getDomainProgress(domain, domainEntities);
                int bgRes = getBackgroundImageForDomain(domain);
                
                list.add(new DomainItem(
                    emoji,
                    domain,
                    progress,
                    start,
                    end,
                    bgRes,
                    sampleTopics(domain, domainEntities)
                ));
            }
            
            // If empty, add some defaults as fallback (though seed should handle it)
            if (list.isEmpty()) {
                list.add(new DomainItem("🍜", "Ẩm thực", 0, "#1A7A5E", "#2AAE84", R.drawable.am_thuc, sampleTopics("Ẩm thực")));
                list.add(new DomainItem("✈️", "Du lịch", 0, "#207A9F", "#3AB6E6", R.drawable.du_lich, sampleTopics("Du lịch")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        list.removeIf(item -> item != null && shouldHideDomainCard(item.getName()));

        synchronized (cacheLock) {
            cachedDomains = new ArrayList<>(list);
            cachedDomainsEmail = email;
            cachedDomainsAt = now;
        }
        return list;
    }

    private boolean shouldHideDomainCard(String domain) {
        if (domain == null) {
            return false;
        }
        String normalized = domain.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return "everyday situational english".equals(normalized);
    }

    public void getDomainsAsync(DataCallback<List<DomainItem>> callback) {
        executorService.execute(() -> {
            List<DomainItem> list = getDomains();
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public List<TopicItem> getTopicsForDomain(String domain) {
        return sampleTopics(domain);
    }

    private String getEmojiForDomain(String domain) {
        String d = domain.toLowerCase();
        if (d.contains("ẩm thực") || d.contains("am thuc")) return "🍜";
        if (d.contains("du lịch") || d.contains("du lich")) return "✈️";
        if (d.contains("công việc") || d.contains("cong viec")) return "💼";
        if (d.contains("sức khoẻ") || d.contains("suc khoe")) return "🏥";
        if (d.contains("học tập") || d.contains("hoc tap")) return "🎓";
        if (d.contains("nhà cửa") || d.contains("nha cua")) return "🏠";
        if (d.contains("công nghệ") || d.contains("cong nghe")) return "💻";
        if (d.contains("kinh doanh")) return "📈";
        if (d.contains("môi trường") || d.contains("moi truong")) return "🌿";
        if (d.contains("nghệ thuật") || d.contains("nghe thuat")) return "🎨";
        if (d.contains("thể thao") || d.contains("the thao")) return "⚽";
        if (d.contains("pháp luật") || d.contains("phap luat")) return "⚖️";
        if (d.contains("khoa học") || d.contains("khoa hoc")) return "🧪";
        if (d.contains("tài chính") || d.contains("tai chinh")) return "💰";
        if (d.contains("gia đình") || d.contains("gia dinh")) return "👨‍👩‍👧‍👦";
        if (d.contains("văn hoá") || d.contains("van hoa")) return "🏛️";
        return "📚";
    }

    private String getGradientStartForDomain(String domain) {
        String d = domain.toLowerCase();
        if (d.contains("ẩm thực") || d.contains("am thuc")) return "#FF7043"; // Orange
        if (d.contains("du lịch") || d.contains("du lich")) return "#26A69A"; // Teal
        if (d.contains("công việc") || d.contains("cong viec")) return "#5C6BC0"; // Indigo
        if (d.contains("sức khoẻ") || d.contains("suc khoe")) return "#66BB6A"; // Green
        if (d.contains("nhà cửa") || d.contains("nha cua")) return "#78909C"; // Blue Grey
        if (d.contains("công nghệ") || d.contains("cong nghe")) return "#42A5F5"; // Blue
        if (d.contains("kinh doanh")) return "#EC407A"; // Pink
        if (d.contains("môi trường") || d.contains("moi truong")) return "#9CCC65"; // Light Green
        if (d.contains("khoa học") || d.contains("khoa hoc")) return "#AB47BC"; // Purple
        if (d.contains("tài chính") || d.contains("tai chinh")) return "#FFCA28"; // Amber
        if (d.contains("văn hoá") || d.contains("van hoa")) return "#8D6E63"; // Brown
        if (d.contains("thể thao") || d.contains("the thao")) return "#FFA726"; // Orange 
        return "#7E57C2"; // Deep Purple
    }

    private String getGradientEndForDomain(String domain) {
        String d = domain.toLowerCase();
        if (d.contains("ẩm thực") || d.contains("am thuc")) return "#FFAB91";
        if (d.contains("du lịch") || d.contains("du lich")) return "#80CBC4";
        if (d.contains("công việc") || d.contains("cong viec")) return "#9FA8DA";
        if (d.contains("sức khoẻ") || d.contains("suc khoe")) return "#A5D6A7";
        if (d.contains("nhà cửa") || d.contains("nha cua")) return "#B0BEC5";
        if (d.contains("công nghệ") || d.contains("cong nghe")) return "#90CAF9";
        if (d.contains("kinh doanh")) return "#F48FB1";
        if (d.contains("môi trường") || d.contains("moi truong")) return "#C5E1A5";
        if (d.contains("khoa học") || d.contains("khoa hoc")) return "#CE93D8";
        if (d.contains("tài chính") || d.contains("tai chinh")) return "#FFE082";
        if (d.contains("văn hoá") || d.contains("van hoa")) return "#BCAAA4";
        if (d.contains("thể thao") || d.contains("the thao")) return "#FFCC80";
        return "#B39DDB";
    }

    private int getBackgroundImageForDomain(String domain) {
        String d = domain.toLowerCase();
        if (d.contains("ẩm thực") || d.contains("am thuc")) return R.drawable.am_thuc;
        if (d.contains("du lịch") || d.contains("du lich")) return R.drawable.du_lich;
        if (d.contains("công việc") || d.contains("cong viec")) return R.drawable.cong_viec;
        if (d.contains("sức khoẻ") || d.contains("suc khoe")) return R.drawable.suc_khoe;
        if (d.contains("học tập") || d.contains("hoc tap")) return R.drawable.hoc_tap;
        if (d.contains("nhà cửa") || d.contains("nha cua")) return R.drawable.nha_cua;
        if (d.contains("công nghệ") || d.contains("cong nghe")) return R.drawable.cong_nghe;
        if (d.contains("kinh doanh") || d.contains("kinh doanh")) return R.drawable.kinh_doanh;
        if (d.contains("môi trường") || d.contains("moi truong")) return R.drawable.moi_truong;
        if (d.contains("nghệ thuật") || d.contains("nghe thuat")) return R.drawable.nghe_thuat;
        if (d.contains("thể thao") || d.contains("the thao")) return R.drawable.the_thao;
        if (d.contains("pháp luật") || d.contains("phap luat")) return R.drawable.phap_luat;
        if (d.contains("khoa học") || d.contains("khoa hoc")) return R.drawable.khoa_hoc;
        if (d.contains("tài chính") || d.contains("tai chinh")) return R.drawable.tai_chinh;
        if (d.contains("gia đình") || d.contains("gia dinh")) return R.drawable.gia_dinh;
        if (d.contains("văn hoá") || d.contains("van hoa")) return R.drawable.van_hoa;
        return 0;
    }

    private int getDomainProgress(String domain, List<CustomVocabularyEntity> domainEntities) {
        List<TopicItem> topics = sampleTopics(domain, domainEntities);
        if (topics.isEmpty()) {
            return 0;
        }

        float score = 0f;
        for (TopicItem topic : topics) {
            score += getTopicProgressPercent(topic.getTitle(), domainEntities);
        }
        return Math.min(100, Math.round(score / topics.size()));
    }

    private int getWordCountByDomain(String domain) {
        try {
            List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
            if (cloudWords != null) {
                int count = 0;
                for (WordEntry item : cloudWords) {
                    if (domain.equalsIgnoreCase(nonEmpty(item.getCategory(), "general"))) {
                        count++;
                    }
                }
                return count;
            }

            Integer count = database.learnedWordDao().getWordCountByDomain(getCurrentEmail(), domain);
            return count != null ? count : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<FlashcardItem> getFlashcardsForTopic(String topic) {
        return buildFlashcardsForTopic(topic, false);
    }

    public void getFlashcardsForTopicAsync(String topic, DataCallback<List<FlashcardItem>> callback) {
        backgroundExecutor.execute(() -> {
            // Return local data immediately for smooth first paint.
            List<FlashcardItem> cards = buildFlashcardsForTopic(topic, false);
            mainHandler.post(() -> callback.onResult(cards));

            // Enrich missing translations/usages in background for future sessions.
            enrichFlashcardsForTopicInBackground(topic);
        });
    }

    private void enrichFlashcardsForTopicInBackground(String topic) {
        try {
            List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic);
            if (topicPool.isEmpty()) {
                return;
            }

            String domain = getDomainFromTopic(topic);
            Map<String, Integer> wordScores = getTopicWordScores(topic);
            int remainingBudget = FLASHCARD_BACKGROUND_ENRICH_LIMIT;

            for (CustomVocabularyEntity entity : topicPool) {
                if (remainingBudget <= 0) {
                    break;
                }

                String normalizedWord = normalizeWord(entity.word);
                int score = wordScores.getOrDefault(normalizedWord, 0);
                if (score >= 100) {
                    continue;
                }

                boolean missingExampleVi = isMissingLearningField(entity.exampleVi);
                boolean missingUsage = isMissingLearningField(entity.usage);
                if (!missingExampleVi && !missingUsage) {
                    continue;
                }

                toFlashcardItem(entity, domain, true);
                remainingBudget--;
            }
        } catch (Exception ignored) {
        }
    }

    private List<FlashcardItem> buildFlashcardsForTopic(String topic, boolean enrichLearningFields) {
        List<FlashcardItem> cards = new ArrayList<>();
        boolean topicPoolEmpty = true;
        Map<String, Integer> wordScores = getTopicWordScores(topic);
        try {
            List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic);
            topicPoolEmpty = topicPool.isEmpty();
            if (!topicPoolEmpty) {
                String domain = getDomainFromTopic(topic);
                for (CustomVocabularyEntity entity : topicPool) {
                    String normalizedWord = normalizeWord(entity.word);
                    int score = wordScores.getOrDefault(normalizedWord, 0);
                    if (score >= 100) {
                        continue;
                    }
                    cards.add(toFlashcardItem(entity, domain, enrichLearningFields));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if (cards.isEmpty()) {
            updateTopicStatus(topic, TopicItem.STATUS_COMPLETED);
        } else if (getTopicProgressPercent(topic) > 0) {
            updateTopicStatus(topic, TopicItem.STATUS_LEARNING);
        }

        if (cards.isEmpty() && topicPoolEmpty) {
            addMockFlashcards(cards);
        }
        return cards;
    }

    private FlashcardItem toFlashcardItem(CustomVocabularyEntity entity, String domain, boolean enrichLearningFields) {
        String safeWord = sanitizeWordForFlashcard(entity.word);
        String safeMeaning = sanitizeMeaningForFlashcard(entity.meaning, safeWord);
        String safeIpa = sanitizeIpaForFlashcard(entity.ipa);
        String safeExample = sanitizeExampleForFlashcard(entity.example, safeWord);

        String exampleVi = sanitizeLearningText(entity.exampleVi);
        String usage = sanitizeLearningText(entity.usage);
        boolean missingExampleVi = isMissingLearningField(exampleVi);
        boolean missingUsage = isMissingLearningField(usage);

        if (missingExampleVi) {
            String translated = enrichLearningFields ? translateEnglishSentenceToVietnamese(safeExample) : "";
            exampleVi = !isMissingLearningField(translated)
                    ? translated
                    : buildExampleTranslationFallback(safeWord, safeMeaning);
        }

        if (missingUsage) {
            usage = buildUsageGuidance(safeWord, safeMeaning, domain, safeExample);
        }

        if (enrichLearningFields && (missingExampleVi || missingUsage)) {
            persistEnrichedLearningFields(entity.word, exampleVi, usage);
        }

        return new FlashcardItem(
                getEmojiForDomain(domain),
                safeWord,
                safeIpa,
                safeMeaning,
                safeExample,
                exampleVi,
                usage
        );
    }

    private void persistEnrichedLearningFields(String word, String exampleVi, String usage) {
        String normalizedWord = sanitizeLearningText(word);
        String normalizedExampleVi = sanitizeLearningText(exampleVi);
        String normalizedUsage = sanitizeLearningText(usage);
        if (normalizedWord.isEmpty() || normalizedExampleVi.isEmpty() || normalizedUsage.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("exampleVi", normalizedExampleVi);
        payload.put("usage", normalizedUsage);
        payload.put("updatedAt", System.currentTimeMillis());
        firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                .document(normalizedWord)
                .set(payload, com.google.firebase.firestore.SetOptions.merge());

        try {
            database.customVocabularyDao().enrichLearningFields(
                    normalizedWord,
                    normalizedExampleVi,
                    normalizedUsage,
                    LEGACY_FLASHCARD_EXAMPLE_PLACEHOLDER,
                    LEGACY_FLASHCARD_USAGE_PLACEHOLDER,
                    System.currentTimeMillis()
            );
        } catch (Exception ignored) {
        }
    }

    private String sanitizeWordForFlashcard(String word) {
        String normalized = sanitizeLearningText(word);
        return normalized.isEmpty() ? "word" : normalized;
    }

    private String sanitizeMeaningForFlashcard(String meaning, String word) {
        String normalized = sanitizeLearningText(meaning);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return "nghĩa thường dùng của từ \"" + word + "\"";
    }

    private String sanitizeIpaForFlashcard(String ipa) {
        String normalized = sanitizeLearningText(ipa);
        return normalized.isEmpty() ? "-" : normalized;
    }

    private String sanitizeExampleForFlashcard(String example, String word) {
        String normalized = sanitizeLearningText(example);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return "People often use \"" + word + "\" in everyday conversation.";
    }

    private String sanitizeLearningText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isMissingLearningField(String value) {
        String normalized = sanitizeLearningText(value);
        if (normalized.isEmpty()) {
            return true;
        }

        String lowered = normalized.toLowerCase(Locale.US);
        return lowered.equals(LEGACY_FLASHCARD_EXAMPLE_PLACEHOLDER.toLowerCase(Locale.US))
                || lowered.equals(LEGACY_FLASHCARD_USAGE_PLACEHOLDER.toLowerCase(Locale.US))
                || lowered.equals("vd trống")
                || lowered.equals("translation pending")
                || lowered.contains("đang được cập nhật")
                || lowered.contains("đang được biên soạn");
    }

    private String buildExampleTranslationFallback(String word, String meaning) {
        return "Câu ví dụ minh họa cách dùng \"" + word + "\" với nghĩa \"" + meaning + "\".";
    }

    private String buildUsageGuidance(String word, String meaning, String domain, String example) {
        String role = inferWordRole(word, meaning, example);
        String domainLabel = sanitizeLearningText(domain);
        if (domainLabel.isEmpty()) {
            domainLabel = "giao tiếp";
        }
        String usage;
        switch (role) {
            case "verb":
                usage = "Dùng \"" + word + "\" như động từ với nghĩa \"" + meaning + "\". " +
                        "Hãy chia thì theo chủ ngữ và ngữ cảnh câu.";
                break;
            case "adjective":
                usage = "Dùng \"" + word + "\" như tính từ mang nghĩa \"" + meaning + "\". " +
                        "Thường đứng trước danh từ hoặc sau động từ to be/look/feel.";
                break;
            case "adverb":
                usage = "Dùng \"" + word + "\" như trạng từ để bổ nghĩa cho động từ hoặc tính từ, " +
                        "thể hiện ý \"" + meaning + "\".";
                break;
            case "noun":
                usage = "Dùng \"" + word + "\" như danh từ mang nghĩa \"" + meaning + "\". " +
                        "Thường kết hợp với a/an/the hoặc các lượng từ phù hợp.";
                break;
            default:
                usage = "Dùng \"" + word + "\" để diễn đạt ý \"" + meaning + "\" trong ngữ cảnh " +
                        domainLabel.toLowerCase(Locale.US) + ".";
                break;
        }

        if (!sanitizeLearningText(example).isEmpty()) {
            usage += " Ví dụ tham chiếu: \"" + shortenText(example, 110) + "\".";
        }
        return usage;
    }

    private String inferWordRole(String word, String meaning, String example) {
        String lowerWord = sanitizeLearningText(word).toLowerCase(Locale.US);
        String lowerMeaning = sanitizeLearningText(meaning).toLowerCase(Locale.US);
        String lowerExample = sanitizeLearningText(example).toLowerCase(Locale.US);

        if (lowerWord.endsWith("ly")) {
            return "adverb";
        }

        if (!lowerWord.isEmpty() && lowerExample.startsWith(lowerWord + " ")) {
            String secondToken = getTokenAt(lowerExample, 1);
            if (!isBeVerb(secondToken) && !isDeterminer(secondToken)) {
                return "verb";
            }
        }

        if (containsTokenPattern(lowerExample, " is " + lowerWord)
                || containsTokenPattern(lowerExample, " are " + lowerWord)
                || containsTokenPattern(lowerExample, " feels " + lowerWord)
                || containsTokenPattern(lowerExample, " seems " + lowerWord)
                || containsTokenPattern(lowerExample, " looks " + lowerWord)
                || containsTokenPattern(lowerExample, " very " + lowerWord)) {
            return "adjective";
        }

        if (containsTokenPattern(lowerExample, " a " + lowerWord)
                || containsTokenPattern(lowerExample, " an " + lowerWord)
                || containsTokenPattern(lowerExample, " the " + lowerWord)
                || containsTokenPattern(lowerExample, " some " + lowerWord)
                || containsTokenPattern(lowerExample, " many " + lowerWord)) {
            return "noun";
        }

        if (lowerWord.endsWith("tion") || lowerWord.endsWith("ment") || lowerWord.endsWith("ness")
                || lowerWord.endsWith("ity") || lowerWord.endsWith("ship")) {
            return "noun";
        }

        if (lowerWord.endsWith("ate") || lowerWord.endsWith("ify") || lowerWord.endsWith("ise")
                || lowerWord.endsWith("ize") || lowerWord.endsWith("ing")) {
            return "verb";
        }

        if (lowerWord.endsWith("ous") || lowerWord.endsWith("ive") || lowerWord.endsWith("ful")
                || lowerWord.endsWith("less") || lowerWord.endsWith("able") || lowerWord.endsWith("al")
                || lowerWord.endsWith("ic")) {
            return "adjective";
        }

        if (lowerMeaning.startsWith("sự ") || lowerMeaning.startsWith("việc ") || lowerMeaning.startsWith("người ")) {
            return "noun";
        }

        if (lowerMeaning.startsWith("làm ") || lowerMeaning.startsWith("đi ") || lowerMeaning.startsWith("ăn ")
                || lowerMeaning.startsWith("uống ") || lowerMeaning.startsWith("nấu ") || lowerMeaning.startsWith("chiên ")
                || lowerMeaning.startsWith("nướng ") || lowerMeaning.startsWith("gọt ") || lowerMeaning.startsWith("băm ")) {
            return "verb";
        }

        return "general";
    }

    private boolean containsTokenPattern(String text, String pattern) {
        return text != null && pattern != null && !pattern.isEmpty() && text.contains(pattern);
    }

    private String getTokenAt(String text, int index) {
        String normalized = sanitizeLearningText(text);
        if (normalized.isEmpty()) {
            return "";
        }
        String[] tokens = normalized.split("\\s+");
        if (index < 0 || index >= tokens.length) {
            return "";
        }
        return tokens[index].replaceAll("[^a-z]", "").trim();
    }

    private boolean isBeVerb(String token) {
        return "is".equals(token) || "am".equals(token) || "are".equals(token)
                || "was".equals(token) || "were".equals(token) || "be".equals(token);
    }

    private boolean isDeterminer(String token) {
        return "a".equals(token) || "an".equals(token) || "the".equals(token)
                || "some".equals(token) || "any".equals(token) || "many".equals(token)
                || "much".equals(token) || "this".equals(token) || "that".equals(token)
                || "these".equals(token) || "those".equals(token);
    }

    private String shortenText(String text, int maxLength) {
        String normalized = sanitizeLearningText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String translateEnglishSentenceToVietnamese(String englishText) {
        String source = sanitizeLearningText(englishText);
        if (source.isEmpty()) {
            return "";
        }

        HttpURLConnection connection = null;
        try {
            String query = URLEncoder.encode(source, StandardCharsets.UTF_8.name());
            String urlString = "https://api.mymemory.translated.net/get?q=" + query + "&langpair=en|vi";
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(FLASHCARD_TRANSLATION_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(FLASHCARD_TRANSLATION_READ_TIMEOUT_MS);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return "";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            String translated = extractBestTranslationCandidate(json, source);
            return isValidVietnameseTranslation(source, translated) ? translated : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String extractBestTranslationCandidate(JSONObject json, String sourceText) {
        String bestCandidate = "";
        int bestScore = Integer.MIN_VALUE;

        JSONArray matches = json.optJSONArray("matches");
        if (matches != null) {
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.optJSONObject(i);
                if (match == null) {
                    continue;
                }

                String candidate = cleanupTranslatedText(match.optString("translation", ""));
                int quality = safeParseInt(match.optString("quality", "0"));
                int usageCount = safeParseInt(match.optString("usage-count", "0"));
                int score = quality + Math.min(usageCount, 100);

                if (isValidVietnameseTranslation(sourceText, candidate) && score > bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
        }

        if (!bestCandidate.isEmpty()) {
            return bestCandidate;
        }

        JSONObject responseData = json.optJSONObject("responseData");
        if (responseData == null) {
            return "";
        }
        return cleanupTranslatedText(responseData.optString("translatedText", ""));
    }

    private int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String cleanupTranslatedText(String value) {
        String normalized = sanitizeLearningText(value)
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ");

        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private boolean isValidVietnameseTranslation(String sourceText, String translatedText) {
        String translated = sanitizeLearningText(translatedText);
        if (translated.isEmpty()) {
            return false;
        }

        if (translated.equalsIgnoreCase(sanitizeLearningText(sourceText))) {
            return false;
        }

        String lowered = translated.toLowerCase(Locale.US);
        if (lowered.contains("mymemory warning") || lowered.contains("translated by")) {
            return false;
        }

        boolean hasVietnameseDiacritics = lowered.matches(".*[àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ].*");
        if (hasVietnameseDiacritics) {
            return true;
        }

        return !(lowered.contains(" the ") || lowered.contains(" is ") || lowered.contains(" are ")
                || lowered.contains(" to ") || lowered.contains(" of "));
    }

    private void addMockFlashcards(List<FlashcardItem> cards) {
        // High-quality mock fallback for UI testing
        cards.add(new FlashcardItem("🍽️", "menu", "/ˈmen.juː/", "thực đơn", "Could I see the menu, please?", "Làm ơn cho tôi xem thực đơn được không?", "Sử dụng khi yêu cầu danh sách các món ăn tại nhà hàng."));
        cards.add(new FlashcardItem("🥗", "healthy", "/ˈhel.θi/", "lành mạnh", "I try to eat healthy meals every day.", "Tôi cố gắng ăn các bữa ăn lành mạnh mỗi ngày.", "Dùng để mô tả thói quen hoặc thực phẩm có lợi cho sức khỏe."));
        cards.add(new FlashcardItem("🍳", "fry", "/fraɪ/", "chiên, rán", "Fry the eggs in a little oil.", "Rán trứng trong một ít dầu.", "Hành động chế biến thực phẩm bằng dầu nóng."));
        cards.add(new FlashcardItem("🥖", "bread", "/bred/", "bánh mì", "He bought a loaf of fresh bread.", "Anh ấy đã mua một ổ bánh mì mới ra lò.", "Danh từ chỉ loại thực phẩm phổ biến làm từ bột mì."));
        cards.add(new FlashcardItem("🍶", "sauce", "/sɔːs/", "nước sốt", "Add some more soy sauce to the stir-fry.", "Cho thêm một ít nước tương vào món xào.", "Chất lỏng dùng để tăng hương vị cho món ăn."));
    }

    public ScanResult mockScanResult() {
        List<String> relatedWords = new ArrayList<>();
        relatedWords.add("glass");
        relatedWords.add("container");
        relatedWords.add("liquid");
        
        return new ScanResult(
                "bottle",
                "/ˈbɒt.əl/",
                "chai, lọ",
                "noun",
                "Please recycle this plastic bottle.",
                "Vui lòng tái chế chai nhựa này.",
                "Nhà cửa",
                "The word 'bottle' comes from Medieval Latin 'butticula'.",
                relatedWords
        );
    }

    public List<WordEntry> getSavedWords() {
        List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
        if (cloudWords != null) {
            return cloudWords;
        }

        List<WordEntry> words = new ArrayList<>();
        try {
            List<LearnedWordEntity> entities = database.learnedWordDao().getAllWords(getCurrentEmail());
            if (entities != null) {
                for (LearnedWordEntity entity : entities) {
                    words.add(new WordEntry(
                        entity.word, 
                        entity.ipa, 
                        entity.meaning, 
                        entity.wordType != null ? entity.wordType : "noun",
                        entity.example, 
                        entity.exampleVi != null ? entity.exampleVi : "",
                        entity.usage != null ? entity.usage : "",
                        entity.domain,
                        entity.note != null ? entity.note : ""
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return words;
    }

    public void saveWord(WordEntry wordEntry) {
        executorService.execute(() -> {
            try {
                String email = getCurrentEmail();
                String uid = getCurrentUid();
                String normalizedWord = normalizeWord(wordEntry.getWord());

                List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
                if (cloudWords != null) {
                    for (WordEntry item : cloudWords) {
                        if (normalizedWord.equals(normalizeWord(item.getWord()))) {
                            return;
                        }
                    }
                }

                if (!uid.isEmpty() && !normalizedWord.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("word", nonEmpty(wordEntry.getWord(), normalizedWord));
                    payload.put("ipa", safeString(wordEntry.getIpa()));
                    payload.put("meaning", safeString(wordEntry.getMeaning()));
                    payload.put("wordType", nonEmpty(wordEntry.getWordType(), "noun"));
                    payload.put("example", safeString(wordEntry.getExample()));
                    payload.put("exampleVi", safeString(wordEntry.getExampleVi()));
                    payload.put("usage", safeString(wordEntry.getUsage()));
                    payload.put("note", safeString(wordEntry.getNote()));
                    payload.put("domain", nonEmpty(wordEntry.getCategory(), "general"));
                    payload.put("topic", "");
                    payload.put("learnedAt", System.currentTimeMillis());
                    payload.put("updatedAt", System.currentTimeMillis());

                    firestore.collection(COLLECTION_USERS)
                            .document(uid)
                            .collection(COLLECTION_LEARNED_WORDS)
                            .document(normalizedWord)
                            .set(payload, com.google.firebase.firestore.SetOptions.merge());
                }

                // Check if word already exists in local fallback storage.
                LearnedWordEntity existing = database.learnedWordDao().getWordByName(email, wordEntry.getWord());
                if (existing != null) {
                    return;
                }
                
                LearnedWordEntity entity = new LearnedWordEntity(
                    wordEntry.getWord(),
                    wordEntry.getIpa(),
                    wordEntry.getMeaning(),
                    wordEntry.getWordType(),
                    wordEntry.getExample(),
                    wordEntry.getExampleVi(),
                    wordEntry.getUsage(),
                    wordEntry.getNote(),
                    wordEntry.getCategory(),
                    ""
                );
                entity.userEmail = email;
                database.learnedWordDao().insert(entity);
                
                // Update stats
                // Robust specific update
                database.userStatsDao().incrementWordsLearned(email, 1);
                invalidateDashboardCache();
                syncCurrentUserProgressToCloud();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public CustomVocabularyEntity findCustomVocabulary(String label) {
        if (label == null || label.trim().isEmpty()) {
            return null;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(label);

        List<CustomVocabularyEntity> cloudValues = getRealtimeCustomVocabularyCopyIfReady();
        if (cloudValues != null) {
            for (CustomVocabularyEntity item : cloudValues) {
                if (normalized.equalsIgnoreCase(item.word)) {
                    return item;
                }
            }
        }

        return database.customVocabularyDao().findByWord(normalized);
    }

    public void saveCustomVocabulary(String label, String meaning) {
        if (label == null || label.trim().isEmpty() || meaning == null || meaning.trim().isEmpty()) {
            return;
        }

        String normalized = ScanAnalyzer.canonicalizeLabel(label);
        executorService.execute(() -> {
            CustomVocabularyEntity existing = findCustomVocabulary(normalized);
            if (existing != null && existing.isLocked) {
                return;
            }
            CustomVocabularyEntity entity = new CustomVocabularyEntity(
                    normalized,
                    meaning.trim(),
                    "-",
                    "User-defined meaning",
                    "", // exampleVi
                    ""  // usage
            );
            entity.source = "user";
            entity.domain = "general";

            Map<String, Object> payload = new HashMap<>();
            payload.put("word", entity.word);
            payload.put("meaning", nonEmpty(entity.meaning, ""));
            payload.put("ipa", nonEmpty(entity.ipa, "-"));
            payload.put("example", nonEmpty(entity.example, ""));
            payload.put("exampleVi", nonEmpty(entity.exampleVi, ""));
            payload.put("usage", nonEmpty(entity.usage, ""));
            payload.put("isLocked", entity.isLocked);
            payload.put("source", nonEmpty(entity.source, "user"));
            payload.put("domain", nonEmpty(entity.domain, "general"));
            payload.put("updatedAt", System.currentTimeMillis());

            firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                    .document(entity.word)
                    .set(payload, com.google.firebase.firestore.SetOptions.merge());

            database.customVocabularyDao().upsert(entity);
            invalidateDashboardCache();
        });
    }

    public List<CustomVocabularyEntity> getAllCustomVocabulary() {
        return getEffectiveCustomVocabulary();
    }

    public void getSystemVocabularyCountAsync(DataCallback<Integer> callback) {
        executorService.execute(() -> {
            int count = getSystemVocabularyCount();
            mainHandler.post(() -> callback.onResult(count));
        });
    }

    public int getSystemVocabularyCount() {
        Set<String> seededWords = new HashSet<>();
        int totalSystemEntries = 0;

        try {
            totalSystemEntries += countSeedPackageEntries(readSeedJson(), seededWords);
        } catch (Exception ignored) {
        }

        try {
            totalSystemEntries += countLegacySeedEntries(readAssetJson(LEGACY_SEED_FILE_NAME), seededWords);
        } catch (Exception ignored) {
        }

        int extraCustomEntries = 0;
        try {
            List<CustomVocabularyEntity> allVocabulary = getEffectiveCustomVocabulary();
            for (CustomVocabularyEntity entity : allVocabulary) {
                String normalizedWord = normalizeWord(entity.word);
                if (normalizedWord.isEmpty()) {
                    continue;
                }

                String source = nonEmpty(entity.source, "").toLowerCase(Locale.US);
                boolean fromSeed = "seed".equals(source) || seededWords.contains(normalizedWord);
                if (!fromSeed) {
                    extraCustomEntries++;
                }
            }
        } catch (Exception ignored) {
        }

        int total = totalSystemEntries + extraCustomEntries;
        if (total > 0) {
            return total;
        }

        try {
            return getEffectiveCustomVocabulary().size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int countSeedPackageEntries(String json, Set<String> seededWords) {
        if (json == null || json.trim().isEmpty()) {
            return 0;
        }

        int count = 0;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray packages = root.optJSONArray("packages");
            if (packages == null) {
                return 0;
            }

            for (int i = 0; i < packages.length(); i++) {
                JSONObject pkg = packages.optJSONObject(i);
                if (pkg == null) {
                    continue;
                }

                JSONArray entries = pkg.optJSONArray("entries");
                if (entries == null) {
                    continue;
                }

                for (int j = 0; j < entries.length(); j++) {
                    JSONObject entry = entries.optJSONObject(j);
                    if (entry == null) {
                        continue;
                    }

                    count++;
                    String normalizedWord = normalizeWord(entry.optString("word", ""));
                    if (seededWords != null && !normalizedWord.isEmpty()) {
                        seededWords.add(normalizedWord);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return count;
    }

    private int countLegacySeedEntries(String json, Set<String> seededWords) {
        if (json == null || json.trim().isEmpty()) {
            return 0;
        }

        int count = 0;
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                count++;
                String normalizedWord = normalizeWord(keys.next());
                if (seededWords != null && !normalizedWord.isEmpty()) {
                    seededWords.add(normalizedWord);
                }
            }
        } catch (Exception ignored) {
        }

        return count;
    }

    public boolean updateCustomMeaning(String word, String meaning, boolean forceIfLocked) {
        if (word == null || word.trim().isEmpty() || meaning == null || meaning.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);

        CustomVocabularyEntity existing = findCustomVocabulary(normalized);
        if (existing != null && existing.isLocked && !forceIfLocked) {
            return false;
        }

        long now = System.currentTimeMillis();
        int updated = forceIfLocked
                ? database.customVocabularyDao().forceUpdateMeaning(normalized, meaning.trim(), now)
                : database.customVocabularyDao().updateMeaningIfUnlocked(normalized, meaning.trim(), now);

        Map<String, Object> payload = new HashMap<>();
        payload.put("meaning", meaning.trim());
        payload.put("updatedAt", now);
        firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                .document(normalized)
                .set(payload, com.google.firebase.firestore.SetOptions.merge());

        if (updated > 0) {
            invalidateDashboardCache();
        }
        return updated > 0 || existing != null;
    }

    public boolean deleteCustomVocabulary(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);
        boolean deleted = database.customVocabularyDao().deleteByWord(normalized) > 0;
        firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                .document(normalized)
                .delete();
        if (deleted) {
            invalidateDashboardCache();
        }
        return true;
    }

    public boolean setCustomVocabularyLocked(String word, boolean isLocked) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);
        boolean updated = database.customVocabularyDao().setLocked(normalized, isLocked, System.currentTimeMillis()) > 0;
        Map<String, Object> payload = new HashMap<>();
        payload.put("isLocked", isLocked);
        payload.put("updatedAt", System.currentTimeMillis());
        firestore.collection(COLLECTION_CUSTOM_VOCABULARY)
                .document(normalized)
                .set(payload, com.google.firebase.firestore.SetOptions.merge());
        if (updated) {
            invalidateDashboardCache();
        }
        return true;
    }

    public void logFailedLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(label);
        String suggestedAlias = buildSuggestedAlias(label);
        executorService.execute(() -> {
            FailedLabelLogEntity existing = database.failedLabelLogDao().findByLabel(normalized);
            long now = System.currentTimeMillis();
            if (existing == null) {
                database.failedLabelLogDao().insert(new FailedLabelLogEntity(normalized, suggestedAlias));
            } else {
                database.failedLabelLogDao().increment(normalized, suggestedAlias, now);
            }
        });
    }

    public void markFailedLabelResolved(String label) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(label);
        executorService.execute(() -> database.failedLabelLogDao().setResolved(normalized, true));
    }

    public List<FailedLabelLogEntity> getTopFailedLabels(int limit) {
        return database.failedLabelLogDao().getTopFailed(limit);
    }

    public void fetchVietnameseSuggestion(String englishWord, MeaningSuggestionCallback callback) {
        if (englishWord == null || englishWord.trim().isEmpty()) {
            mainHandler.post(() -> callback.onResult(null));
            return;
        }

        executorService.execute(() -> {
            String suggestion = null;
            HttpURLConnection connection = null;
            try {
                String query = URLEncoder.encode(englishWord.trim(), StandardCharsets.UTF_8.name());
                String urlString = "https://api.mymemory.translated.net/get?q=" + query + "&langpair=en|vi";
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    JSONObject responseData = json.optJSONObject("responseData");
                    if (responseData != null) {
                        String translated = responseData.optString("translatedText", "").trim();
                        if (!translated.isEmpty()) {
                            suggestion = translated;
                        }
                    }
                }
            } catch (Exception ignored) {
                suggestion = null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            String finalSuggestion = suggestion;
            mainHandler.post(() -> callback.onResult(finalSuggestion));
        });
    }

    private void importSeedVocabularyIfNeeded() {
        // Always attempt package sync so new package versions are imported after app updates.
        backgroundExecutor.execute(() -> {
            try {
                String json = readSeedJson();
                importSeedPackages(json);
                preferences.edit().putBoolean(KEY_SEED_IMPORTED, true).apply();
            } catch (Exception e) {
                // Retry on next app launch if import fails.
            }
        });
    }

    private String readSeedJson() throws Exception {
        return readAssetJson(SEED_FILE_NAME);
    }

    private String readAssetJson(String fileName) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(appContext.getAssets().open(fileName), StandardCharsets.UTF_8)
        );
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private void importSeedPackages(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray packages = root.optJSONArray("packages");
        if (packages == null) {
            return;
        }

        for (int i = 0; i < packages.length(); i++) {
            JSONObject pkg = packages.optJSONObject(i);
            if (pkg == null) {
                continue;
            }

            String packageName = pkg.optString("name", "").trim();
            int packageVersion = pkg.optInt("version", 1);
            String domain = pkg.optString("domain", "general").trim();
            if (packageName.isEmpty()) {
                continue;
            }

            SeedPackageStateEntity state = database.seedPackageStateDao().findByPackageName(packageName);
            int importedVersion = state != null ? state.version : 0;
            if (importedVersion >= packageVersion) {
                continue;
            }

            JSONArray entries = pkg.optJSONArray("entries");
            if (entries == null) {
                continue;
            }

            List<CustomVocabularyEntity> seeds = new ArrayList<>();
            for (int j = 0; j < entries.length(); j++) {
                JSONObject entry = entries.optJSONObject(j);
                if (entry == null) {
                    continue;
                }
                String word = entry.optString("word", "").trim();
                String meaning = entry.optString("meaning", "").trim();
                if (word.isEmpty() || meaning.isEmpty()) {
                    continue;
                }

                CustomVocabularyEntity entity = new CustomVocabularyEntity(
                        ScanAnalyzer.canonicalizeLabel(word),
                        meaning,
                        entry.optString("ipa", "-"),
                        entry.optString("example", "Seed vocabulary"),
                        entry.optString("exampleVi", ""),
                        entry.optString("usage", "")
                );
                entity.source = "seed";
                entity.domain = domain.isEmpty() ? "general" : domain;
                seeds.add(entity);
            }

            if (!seeds.isEmpty()) {
                database.customVocabularyDao().insertSeed(seeds);

                com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                for (CustomVocabularyEntity seed : seeds) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("word", seed.word);
                    payload.put("meaning", nonEmpty(seed.meaning, ""));
                    payload.put("ipa", nonEmpty(seed.ipa, "-"));
                    payload.put("example", nonEmpty(seed.example, ""));
                    payload.put("exampleVi", nonEmpty(seed.exampleVi, ""));
                    payload.put("usage", nonEmpty(seed.usage, ""));
                    payload.put("isLocked", seed.isLocked);
                    payload.put("source", nonEmpty(seed.source, "seed"));
                    payload.put("domain", nonEmpty(seed.domain, "general"));
                    payload.put("updatedAt", System.currentTimeMillis());

                    batch.set(
                            firestore.collection(COLLECTION_CUSTOM_VOCABULARY).document(seed.word),
                            payload,
                            com.google.firebase.firestore.SetOptions.merge()
                    );
                }
                batch.commit();
            }

            SeedPackageStateEntity nextState = new SeedPackageStateEntity(packageName, packageVersion);
            database.seedPackageStateDao().upsert(nextState);
        }
        invalidateDashboardCache();
    }

    private String buildSuggestedAlias(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase();
        String canonical = ScanAnalyzer.canonicalizeLabel(normalized);
        if (!canonical.equals(normalized)) {
            return canonical;
        }
        if (normalized.contains("phone")) {
            return "phone";
        }
        if (normalized.contains("table") || normalized.contains("desk")) {
            return "table";
        }
        if (normalized.contains("screen") || normalized.contains("monitor")) {
            return "television";
        }
        if (normalized.contains("dog") || normalized.contains("puppy")) {
            return "dog";
        }
        return "";
    }

    public interface MeaningSuggestionCallback {
        void onResult(String suggestion);
    }

    public interface DataCallback<T> {
        void onResult(T result);
    }

    public void getAllCustomVocabularyAsync(DataCallback<List<CustomVocabularyEntity>> callback) {
        executorService.execute(() -> {
            List<CustomVocabularyEntity> result = getAllCustomVocabulary();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getTopFailedLabelsAsync(int limit, DataCallback<List<FailedLabelLogEntity>> callback) {
        executorService.execute(() -> {
            List<FailedLabelLogEntity> result = database.failedLabelLogDao().getTopFailed(limit);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public LiveData<List<CustomVocabularyEntity>> getAllCustomVocabularyLive() {
        ensureRealtimeListenersBound();
        List<CustomVocabularyEntity> values = getRealtimeCustomVocabularyCopyIfReady();
        if (values != null) {
            customVocabularyLiveData.postValue(values);
        } else {
            executorService.execute(() -> customVocabularyLiveData.postValue(database.customVocabularyDao().getAll()));
        }
        return customVocabularyLiveData;
    }

    public androidx.lifecycle.LiveData<List<FailedLabelLogEntity>> getTopFailedLabelsLive(int limit) {
        return database.failedLabelLogDao().getTopFailedLive(limit);
    }

    public void removeWord(WordEntry wordEntry) {
        // Note: You might want to add a delete method to the DAO if needed
    }

    public void increaseScanCount() {
        executorService.execute(() -> {
            try {
                UserStatsEntity stats = getOrCreateUserStats();
                if (stats != null) {
                    stats.totalWordsScanned += 1;
                    database.userStatsDao().update(stats);
                    invalidateDashboardCache();
                    syncCurrentUserProgressToCloud();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void increaseChatCount() {
        // Chat count is derived from database
    }

    public void addXp(int xp) {
        if (xp <= 0) return;
        executorService.execute(() -> {
            try {
                // Ensure daily XP is for today
                String email = getCurrentEmail();
                UserStatsEntity stats = getOrCreateUserStats(); 
                
                // Specific updates to avoid overwriting
                database.userStatsDao().addTotalXp(email, xp);
                database.userStatsDao().addDailyXp(email, xp);
                updateStreakForNewActivity(email, stats, System.currentTimeMillis());
                invalidateDashboardCache();
                syncCurrentUserProgressToCloud();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public androidx.lifecycle.LiveData<UserStatsEntity> getLiveUserStats() {
        return database.userStatsDao().getLiveUserStats(getCurrentEmail());
    }

    public void spendXpAsync(int xpCost, DataCallback<SpendXpResult> callback) {
        int safeCost = Math.max(0, xpCost);
        if (safeCost <= 0) {
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(new SpendXpResult(false, 0, "Chi phí XP không hợp lệ")));
            }
            return;
        }

        executorService.execute(() -> {
            SpendXpResult result;
            try {
                String email = getCurrentEmail();
                UserStatsEntity stats = getOrCreateUserStats();
                int currentXp = stats != null ? Math.max(0, stats.totalXpEarned) : 0;

                if (currentXp < safeCost) {
                    result = new SpendXpResult(false, currentXp, "Không đủ XP để mở ảnh này");
                } else {
                    int updatedRows = database.userStatsDao().spendTotalXp(email, safeCost);
                    UserStatsEntity refreshed = database.userStatsDao().getUserStats(email);
                    int remainingXp = refreshed != null ? Math.max(0, refreshed.totalXpEarned) : Math.max(0, currentXp - safeCost);

                    if (updatedRows <= 0) {
                        result = new SpendXpResult(false, remainingXp, "Không thể trừ XP, vui lòng thử lại");
                    } else {
                        invalidateDashboardCache();
                        syncCurrentUserProgressToCloud();
                        result = new SpendXpResult(true, remainingXp, "");
                    }
                }
            } catch (Exception e) {
                result = new SpendXpResult(false, 0, "Không thể xử lý giao dịch XP lúc này");
            }

            if (callback != null) {
                SpendXpResult callbackResult = result;
                mainHandler.post(() -> callback.onResult(callbackResult));
            }
        });
    }

    public void addStudySession(StudySession session) {
        executorService.execute(() -> {
            try {
                String email = getCurrentEmail();
                String uid = getCurrentUid();
                StudySessionEntity entity = new StudySessionEntity(
                    session.getStartTime(),
                    session.getEndTime(),
                    session.getWordsLearned(),
                    session.getDomainName(),
                    session.getTopicName(),
                    session.getXpEarned()
                );
                entity.userEmail = email;
                database.studySessionDao().insert(entity);

                if (!uid.isEmpty()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("startTime", session.getStartTime());
                    payload.put("endTime", session.getEndTime());
                    payload.put("wordsLearned", session.getWordsLearned());
                    payload.put("domain", nonEmpty(session.getDomainName(), ""));
                    payload.put("topic", nonEmpty(session.getTopicName(), ""));
                    payload.put("xpEarned", session.getXpEarned());
                    payload.put("updatedAt", System.currentTimeMillis());

                    firestore.collection(COLLECTION_USERS)
                            .document(uid)
                            .collection(COLLECTION_STUDY_SESSIONS)
                            .add(payload);
                }
                
                // Ensure daily context
                UserStatsEntity stats = getOrCreateUserStats();
                
                // Use robust specialized updates
                database.userStatsDao().addStudyMinutes(email, (int) entity.getDurationMinutes());
                if (session.getXpEarned() > 0) {
                    database.userStatsDao().addTotalXp(email, session.getXpEarned());
                    database.userStatsDao().addDailyXp(email, session.getXpEarned());
                }
                updateStreakForNewActivity(email, stats, System.currentTimeMillis());
                invalidateDashboardCache();
                syncCurrentUserProgressToCloud();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getCefrLevel() {
        return getCefrLevelForLearned(getLearnedWords());
    }

    private String getCefrLevelForLearned(int learned) {
        if (learned < 80) return "A1";
        if (learned < 160) return "A2";
        if (learned < 260) return "B1";
        if (learned < 380) return "B2";
        if (learned < 520) return "C1";
        return "C2";
    }

    public void resetProgress() {
        executorService.execute(() -> {
            try {
                String email = getCurrentEmail();
                String uid = getCurrentUid();
                database.learnedWordDao().deleteAllWords(email);
                database.studySessionDao().deleteAllSessions(email);
                // database.chatMessageDao().deleteAllMessages(email); // If chat supports multi-user

                if (!uid.isEmpty()) {
                    clearUserRealtimeCollection(uid, COLLECTION_LEARNED_WORDS);
                    clearUserRealtimeCollection(uid, COLLECTION_STUDY_SESSIONS);
                }
                
                UserStatsEntity stats = new UserStatsEntity();
                stats.userEmail = email;
                database.userStatsDao().update(stats);

                SharedPreferences.Editor editor = preferences.edit();
                String prefix = getPrefKey("");
                for (String key : preferences.getAll().keySet()) {
                    if (key.startsWith(prefix)) {
                        editor.remove(key);
                    }
                }
                editor.apply();
                invalidateDashboardCache();
                syncCurrentUserProgressToCloud();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<TopicItem> sampleTopics(String domain) {
        return sampleTopics(domain, null);
    }

    private List<TopicItem> sampleTopics(String domain, List<CustomVocabularyEntity> domainEntities) {
        List<TopicItem> topics = new ArrayList<>();
        topics.add(new TopicItem(domain + " cơ bản", getTopicStatus(domain + " cơ bản", domainEntities)));
        topics.add(new TopicItem(domain + " giao tiếp", getTopicStatus(domain + " giao tiếp", domainEntities)));
        topics.add(new TopicItem(domain + " nâng cao", getTopicStatus(domain + " nâng cao", domainEntities)));
        topics.add(new TopicItem(domain + " thực chiến", getTopicStatus(domain + " thực chiến", domainEntities)));
        topics.add(new TopicItem(domain + " chuyên sâu", getTopicStatus(domain + " chuyên sâu", domainEntities)));
        topics.add(new TopicItem(domain + " chuyên ngành", getTopicStatus(domain + " chuyên ngành", domainEntities)));
        topics.add(new TopicItem(domain + " thuật ngữ", getTopicStatus(domain + " thuật ngữ", domainEntities)));
        topics.add(new TopicItem(domain + " tình huống", getTopicStatus(domain + " tình huống", domainEntities)));
        topics.add(new TopicItem(domain + " tiếng lóng", getTopicStatus(domain + " tiếng lóng", domainEntities)));
        topics.add(new TopicItem(domain + " thành ngữ", getTopicStatus(domain + " thành ngữ", domainEntities)));
        topics.add(new TopicItem(domain + " mở rộng", getTopicStatus(domain + " mở rộng", domainEntities)));
        topics.add(new TopicItem(domain + " ứng dụng", getTopicStatus(domain + " ứng dụng", domainEntities)));
        topics.add(new TopicItem(domain + " nâng tầm", getTopicStatus(domain + " nâng tầm", domainEntities)));
        topics.add(new TopicItem(domain + " học thuật", getTopicStatus(domain + " học thuật", domainEntities)));
        topics.add(new TopicItem(domain + " chuyên gia", getTopicStatus(domain + " chuyên gia", domainEntities)));
        return topics;
    }

    private long getStartOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long getEndOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    private String getDayKey(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int year = cal.get(Calendar.YEAR);
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
        return year + "-" + dayOfYear;
    }

    private boolean isDifferentDay(long oldTime, long newTime) {
        if (oldTime <= 0L) {
            return true;
        }
        return getStartOfDay(oldTime) != getStartOfDay(newTime);
    }

    private int computeRecentStreakFromStudySessions(String email) {
        try {
            Set<Long> activeDays = new HashSet<>();

            List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();
            if (cloudSessions != null) {
                for (StudySession session : cloudSessions) {
                    if (session == null) {
                        continue;
                    }
                    long activityAt = session.getEndTime() > 0L ? session.getEndTime() : session.getStartTime();
                    if (activityAt > 0L) {
                        activeDays.add(getStartOfDay(activityAt));
                    }
                }
            } else {
                List<StudySessionEntity> localSessions = database.studySessionDao().getAllSessions(email);
                if (localSessions != null) {
                    for (StudySessionEntity session : localSessions) {
                        if (session == null) {
                            continue;
                        }
                        long activityAt = session.endTime > 0L ? session.endTime : session.startTime;
                        if (activityAt > 0L) {
                            activeDays.add(getStartOfDay(activityAt));
                        }
                    }
                }
            }

            if (activeDays.isEmpty()) {
                return 0;
            }

            long dayMs = 24L * 60L * 60L * 1000L;
            long todayStart = getStartOfDay(System.currentTimeMillis());
            long latestDay = 0L;
            for (Long day : activeDays) {
                if (day != null && day > latestDay) {
                    latestDay = day;
                }
            }

            if (latestDay < (todayStart - dayMs)) {
                return 0;
            }

            int streak = 0;
            long cursor = latestDay;
            while (activeDays.contains(cursor)) {
                streak++;
                cursor -= dayMs;
            }

            return Math.max(0, streak);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private UserStatsEntity getOrCreateUserStats() {
        String email = getCurrentEmail();
        UserStatsEntity stats = database.userStatsDao().getUserStats(email);
        if (stats == null) {
            stats = new UserStatsEntity();
            stats.userEmail = email;
            database.userStatsDao().insert(stats);
        } else {
            // Robust New Day Reset check
            long lastStudyTime = stats.lastStudyDate;
            if (lastStudyTime > 0) {
                long now = System.currentTimeMillis();
                long startToday = getStartOfDay(now);
                long startLast = getStartOfDay(lastStudyTime);
                long diffDays = (startToday - startLast) / (24 * 60 * 60 * 1000);
                
                boolean isNewDay = diffDays > 0;
                
                if (isNewDay) {
                    database.userStatsDao().resetDailyXp(email);
                    stats.xpTodayEarned = 0;
                }
                
                if (diffDays > 1 && stats.currentStreak > 0) {
                    stats.currentStreak = 0;
                    database.userStatsDao().updateStreakAndDate(email, stats.currentStreak, stats.bestStreak, lastStudyTime);
                }
            }
        }
        return stats;
    }

    public void syncCurrentUserProgressToCloud() {
        String uid = getCurrentUid();
        if (uid.isEmpty()) {
            return;
        }

        try {
            String email = getCurrentEmail();
            UserStatsEntity stats = database.userStatsDao().getUserStats(email);

            List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
            List<StudySession> cloudSessions = getRealtimeStudySessionsCopyIfReady();

            Integer learnedWords = cloudWords != null
                    ? cloudWords.size()
                    : database.learnedWordDao().getTotalWordsCount(email);

            int totalXp = stats != null ? stats.totalXpEarned : 0;
            int xpToday = stats != null ? stats.xpTodayEarned : 0;
            int currentStreak = stats != null ? stats.currentStreak : 0;
            int bestStreak = stats != null ? stats.bestStreak : 0;
            int totalWordsScanned = stats != null ? stats.totalWordsScanned : 0;
            int totalStudyMinutes = stats != null ? stats.totalStudyMinutes : 0;
            long localLastStudyAtMs = stats != null ? stats.lastStudyDate : 0L;
            if (cloudSessions != null) {
                long totalMs = 0L;
                for (StudySession session : cloudSessions) {
                    long start = session.getStartTime();
                    long end = session.getEndTime();
                    if (end <= 0L) {
                        end = start;
                    }
                    totalMs += Math.max(0L, end - start);
                }
                totalStudyMinutes = (int) (totalMs / 60000L);
            }

            firebaseUserStore.syncUserProgress(
                    uid,
                    getUserName(),
                    totalXp,
                    xpToday,
                    learnedWords != null ? learnedWords : 0,
                    currentStreak,
                    bestStreak,
                    totalWordsScanned,
                    totalStudyMinutes,
                    localLastStudyAtMs
            );
        } catch (Exception ignored) {
        }
    }

    private void updateStreakForNewActivity(String email, UserStatsEntity stats, long now) {
        long lastStudyTime = stats.lastStudyDate;
        if (lastStudyTime > 0) {
            long startToday = getStartOfDay(now);
            long startLast = getStartOfDay(lastStudyTime);
            long diffDays = (startToday - startLast) / (24 * 60 * 60 * 1000);
            
            if (diffDays == 1) {
                stats.currentStreak++;
            } else if (diffDays > 1) {
                stats.currentStreak = 1;
            } else if (diffDays == 0 && stats.currentStreak == 0) {
                stats.currentStreak = 1;
            }
        } else {
            stats.currentStreak = 1;
        }
        
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak;
        }
        
        stats.lastStudyDate = now;
        database.userStatsDao().updateStreakAndDate(email, stats.currentStreak, stats.bestStreak, now);
    }

    public void markTopicWordLearned(String topic, String word) {
        recordTopicWordRating(topic, word, 3);
    }

    public void unmarkTopicWordLearned(String topic, String word) {
        recordTopicWordRating(topic, word, 1);
    }

    public void recordTopicWordRating(String topic, String word, int ratingScore) {
        if (topic == null || topic.trim().isEmpty() || word == null || word.trim().isEmpty()) {
            return;
        }

        int progressScore;
        if (ratingScore >= 3) {
            progressScore = 100;
        } else if (ratingScore == 2) {
            progressScore = 50;
        } else {
            progressScore = 20;
        }

        Map<String, Integer> wordScores = getTopicWordScores(topic);
        String normalizedWord = normalizeWord(word);
        wordScores.put(normalizedWord, progressScore);
        saveTopicWordScores(topic, wordScores);

        if (progressScore >= 100) {
            Set<String> learnedWords = getLearnedWordsForTopic(topic);
            learnedWords.add(normalizedWord);
            saveLearnedWordsForTopic(topic, learnedWords);
        }
        invalidateDashboardCache();
    }

    public Set<String> getLearnedWordsForTopic(String topic) {
        Set<String> learnedWords = new HashSet<>();
        if (topic == null || topic.trim().isEmpty()) {
            return learnedWords;
        }

        String raw = preferences.getString(getPrefKey(KEY_TOPIC_LEARNED_WORDS_PREFIX + topic.trim()), "");
        if (raw == null || raw.trim().isEmpty()) {
            return learnedWords;
        }

        try {
            JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                String value = jsonArray.optString(i, "");
                if (!value.trim().isEmpty()) {
                    learnedWords.add(value.trim());
                }
            }
        } catch (Exception ignored) {
        }
        return learnedWords;
    }

    private void saveLearnedWordsForTopic(String topic, Set<String> learnedWords) {
        JSONArray jsonArray = new JSONArray();
        for (String word : learnedWords) {
            jsonArray.put(word);
        }
        preferences.edit()
                .putString(getPrefKey(KEY_TOPIC_LEARNED_WORDS_PREFIX + topic.trim()), jsonArray.toString())
                .apply();
    }

    private int getTopicProgressPercent(String topic) {
        return getTopicProgressPercent(topic, null);
    }

    private int getTopicProgressPercent(String topic, List<CustomVocabularyEntity> domainEntities) {
        List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic, domainEntities);
        if (topicPool.isEmpty()) {
            return 0;
        }

        Map<String, Integer> wordScores = getTopicWordScores(topic);
        int totalScore = 0;
        for (CustomVocabularyEntity entity : topicPool) {
            String normalizedWord = normalizeWord(entity.word);
            totalScore += wordScores.getOrDefault(normalizedWord, 0);
        }

        return Math.min(100, Math.round((float) totalScore / topicPool.size()));
    }

    private List<CustomVocabularyEntity> getTopicVocabularyPool(String topic) {
        return getTopicVocabularyPool(topic, null);
    }

    private List<CustomVocabularyEntity> getTopicVocabularyPool(String topic, List<CustomVocabularyEntity> domainEntities) {
        List<CustomVocabularyEntity> topicPool = new ArrayList<>();
        try {
            String domain = getDomainFromTopic(topic);
            int startIndex = getStartIndexForTopic(topic);
            List<CustomVocabularyEntity> entities = domainEntities;
            if (entities == null) {
                entities = getEffectiveCustomVocabularyByDomain(domain);
            }
            if (entities == null || entities.isEmpty()) {
                return topicPool;
            }

            // Keep a deterministic ordering so topic progress does not "reset" after login
            // when source switches between local fallback and realtime cloud snapshots.
            List<CustomVocabularyEntity> stableEntities = new ArrayList<>(entities);
            stableEntities.sort((left, right) -> {
                String leftWord = normalizeWord(left != null ? left.word : "");
                String rightWord = normalizeWord(right != null ? right.word : "");
                int byWord = leftWord.compareToIgnoreCase(rightWord);
                if (byWord != 0) {
                    return byWord;
                }

                String leftMeaning = left != null && left.meaning != null ? left.meaning : "";
                String rightMeaning = right != null && right.meaning != null ? right.meaning : "";
                return leftMeaning.compareToIgnoreCase(rightMeaning);
            });

            int targetSize = Math.min(10, stableEntities.size());
            for (int i = 0; i < targetSize; i++) {
                int index = (startIndex + i) % stableEntities.size();
                topicPool.add(stableEntities.get(index));
            }
        } catch (Exception ignored) {
        }
        return topicPool;
    }

    private String getDomainFromTopic(String topic) {
        if (topic == null) {
            return "";
        }
        String[] suffixes = {
            " cơ bản", " giao tiếp", " nâng cao", " thực chiến",
            " chuyên sâu", " chuyên ngành", " thuật ngữ", " tình huống",
            " tiếng lóng", " thành ngữ", " mở rộng", " ứng dụng",
            " nâng tầm", " học thuật", " chuyên gia"
        };
        for (String suffix : suffixes) {
            if (topic.endsWith(suffix)) {
                return topic.replace(suffix, "");
            }
        }
        return topic;
    }

    private int getStartIndexForTopic(String topic) {
        if (topic == null) return 0;
        if (topic.endsWith(" cơ bản")) return 0;
        if (topic.endsWith(" giao tiếp")) return 10;
        if (topic.endsWith(" nâng cao")) return 20;
        if (topic.endsWith(" thực chiến")) return 30;
        if (topic.endsWith(" chuyên sâu")) return 40;
        if (topic.endsWith(" chuyên ngành")) return 50;
        if (topic.endsWith(" thuật ngữ")) return 60;
        if (topic.endsWith(" tình huống")) return 70;
        if (topic.endsWith(" tiếng lóng")) return 80;
        if (topic.endsWith(" thành ngữ")) return 90;
        if (topic.endsWith(" mở rộng")) return 100;
        if (topic.endsWith(" ứng dụng")) return 110;
        if (topic.endsWith(" nâng tầm")) return 120;
        if (topic.endsWith(" học thuật")) return 130;
        if (topic.endsWith(" chuyên gia")) return 140;
        return 0;
    }

    private Map<String, Integer> getTopicWordScores(String topic) {
        Map<String, Integer> wordScores = new HashMap<>();
        if (topic == null || topic.trim().isEmpty()) {
            return wordScores;
        }

        String raw = preferences.getString(getPrefKey(KEY_TOPIC_WORD_SCORES_PREFIX + topic.trim()), "");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject(raw);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int value = jsonObject.optInt(key, 0);
                    wordScores.put(key, Math.max(0, Math.min(100, value)));
                }
            } catch (Exception ignored) {
            }
        }

        // Backward compatibility for old learned set data.
        Set<String> legacyLearnedWords = getLearnedWordsForTopic(topic);
        for (String word : legacyLearnedWords) {
            if (!wordScores.containsKey(word)) {
                wordScores.put(word, 100);
            }
        }

        return wordScores;
    }

    private void saveTopicWordScores(String topic, Map<String, Integer> wordScores) {
        JSONObject jsonObject = new JSONObject();
        try {
            for (Map.Entry<String, Integer> entry : wordScores.entrySet()) {
                jsonObject.put(entry.getKey(), Math.max(0, Math.min(100, entry.getValue())));
            }
        } catch (Exception ignored) {
        }

        preferences.edit()
                .putString(getPrefKey(KEY_TOPIC_WORD_SCORES_PREFIX + topic.trim()), jsonObject.toString())
                .apply();
    }

    public void getSavedWordsAsync(DataCallback<List<WordEntry>> callback) {
        executorService.execute(() -> {
            List<WordEntry> result = getSavedWords();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getLearnedWordEntitiesAsync(DataCallback<List<LearnedWordEntity>> callback) {
        executorService.execute(() -> {
            try {
                List<WordEntry> cloudWords = getRealtimeLearnedWordsCopyIfReady();
                List<LearnedWordEntity> entities;
                if (cloudWords != null) {
                    entities = new ArrayList<>();
                    String email = getCurrentEmail();
                    for (WordEntry word : cloudWords) {
                        LearnedWordEntity entity = new LearnedWordEntity(
                                nonEmpty(word.getWord(), ""),
                                nonEmpty(word.getIpa(), ""),
                                nonEmpty(word.getMeaning(), ""),
                                nonEmpty(word.getWordType(), "noun"),
                                nonEmpty(word.getExample(), ""),
                                nonEmpty(word.getExampleVi(), ""),
                                nonEmpty(word.getUsage(), ""),
                                nonEmpty(word.getNote(), ""),
                                nonEmpty(word.getCategory(), "general"),
                                ""
                        );
                        entity.userEmail = email;
                        entities.add(entity);
                    }
                } else {
                    entities = database.learnedWordDao().getAllWords(getCurrentEmail());
                }

                mainHandler.post(() -> callback.onResult(entities != null ? entities : new ArrayList<>()));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onResult(new ArrayList<>()));
            }
        });
    }

    public void getFillBlankTopicsAsync(DataCallback<List<FillBlankTopicItem>> callback) {
        backgroundExecutor.execute(() -> {
            List<FillBlankTopicItem> topics = getFillBlankTopics();
            int retries = 0;
            while (topics.isEmpty()
                    && retries < FILL_BLANK_BOOTSTRAP_MAX_RETRY
                    && shouldRetryFillBlankBootstrap()) {
                sleepForFillBlankBootstrap();
                topics = getFillBlankTopics();
                retries++;
            }
            List<FillBlankTopicItem> safeTopics = topics != null ? topics : new ArrayList<>();
            mainHandler.post(() -> callback.onResult(safeTopics));
        });
    }

    public void getFillBlankQuestionsAsync(String topic, DataCallback<List<FillBlankQuestionItem>> callback) {
        backgroundExecutor.execute(() -> {
            List<FillBlankQuestionItem> questions = getFillBlankQuestions(topic);
            int retries = 0;
            while (questions.isEmpty()
                    && retries < FILL_BLANK_BOOTSTRAP_MAX_RETRY
                    && shouldRetryFillBlankBootstrap()) {
                sleepForFillBlankBootstrap();
                questions = getFillBlankQuestions(topic);
                retries++;
            }
            List<FillBlankQuestionItem> safeQuestions = questions != null ? questions : new ArrayList<>();
            mainHandler.post(() -> callback.onResult(safeQuestions));
        });
    }

    public List<FillBlankQuestionItem> getFillBlankQuestions(String topic) {
        // Keep first paint fast: avoid network translation work on the UI-critical path.
        return buildFillBlankQuestionsForTopic(topic, true, false, false);
    }

    private boolean shouldRetryFillBlankBootstrap() {
        String uid = getCurrentUid();
        if (uid == null || uid.trim().isEmpty()) {
            return false;
        }

        boolean learnedPending;
        boolean customPending;
        synchronized (realtimeLock) {
            learnedPending = !realtimeLearnedReady;
            customPending = !realtimeCustomReady;
        }

        if (!learnedPending && !customPending) {
            return false;
        }

        try {
            String email = getCurrentEmail();
            int localLearnedCount = Math.max(0, database.learnedWordDao().getTotalWordsCount(email));
            int localVocabularyCount = Math.max(0, database.customVocabularyDao().getCount());

            if (learnedPending && localLearnedCount == 0) {
                return true;
            }
            return customPending && localVocabularyCount == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sleepForFillBlankBootstrap() {
        try {
            Thread.sleep(FILL_BLANK_BOOTSTRAP_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public FillBlankStats getFillBlankStats() {
        FillBlankStats stats = new FillBlankStats();
        stats.totalSessions = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_SESSIONS), 0);
        stats.totalAttempts = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_ATTEMPTS), 0);
        stats.totalCorrect = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_CORRECT), 0);
        stats.totalWrong = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_WRONG), 0);
        stats.totalXpEarned = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_XP), 0);
        stats.bestCombo = preferences.getInt(getPrefKey(KEY_FILL_BLANK_STATS_BEST_COMBO), 0);
        stats.lastPlayedAt = preferences.getLong(getPrefKey(KEY_FILL_BLANK_STATS_LAST_PLAYED_AT), 0L);
        stats.lastTopic = preferences.getString(getPrefKey(KEY_FILL_BLANK_STATS_LAST_TOPIC), "");
        return stats;
    }

    public void getFillBlankStatsAsync(DataCallback<FillBlankStats> callback) {
        executorService.execute(() -> {
            FillBlankStats stats = getFillBlankStats();
            mainHandler.post(() -> callback.onResult(stats));
        });
    }

    public void recordFillBlankAnswerResult(boolean isCorrect, int xpAwarded, int comboAfterAnswer) {
        executorService.execute(() -> {
            FillBlankStats stats = getFillBlankStats();
            stats.totalAttempts += 1;
            if (isCorrect) {
                stats.totalCorrect += 1;
                stats.totalXpEarned += Math.max(0, xpAwarded);
            } else {
                stats.totalWrong += 1;
            }
            stats.bestCombo = Math.max(stats.bestCombo, Math.max(0, comboAfterAnswer));
            stats.lastPlayedAt = System.currentTimeMillis();
            saveFillBlankStats(stats);
        });
    }

    public void recordFillBlankSessionResult(String topic,
                                             int sessionXp,
                                             int sessionBestCombo,
                                             int sessionCorrect,
                                             int sessionWrong) {
        executorService.execute(() -> {
            FillBlankStats stats = getFillBlankStats();
            stats.totalSessions += 1;
            stats.bestCombo = Math.max(stats.bestCombo, Math.max(0, sessionBestCombo));
            stats.totalXpEarned = Math.max(stats.totalXpEarned, 0);

            String safeTopic = safeString(topic);
            if (!safeTopic.isEmpty()) {
                stats.lastTopic = safeTopic;
            }
            stats.lastPlayedAt = System.currentTimeMillis();

            // Ensure aggregate values are never lower than completed session totals.
            stats.totalCorrect = Math.max(stats.totalCorrect, Math.max(0, sessionCorrect));
            stats.totalWrong = Math.max(stats.totalWrong, Math.max(0, sessionWrong));
            stats.totalXpEarned = Math.max(stats.totalXpEarned, Math.max(0, sessionXp));

            saveFillBlankStats(stats);
        });
    }

    private void saveFillBlankStats(FillBlankStats stats) {
        if (stats == null) {
            return;
        }
        preferences.edit()
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_SESSIONS), Math.max(0, stats.totalSessions))
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_ATTEMPTS), Math.max(0, stats.totalAttempts))
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_CORRECT), Math.max(0, stats.totalCorrect))
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_WRONG), Math.max(0, stats.totalWrong))
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_TOTAL_XP), Math.max(0, stats.totalXpEarned))
                .putInt(getPrefKey(KEY_FILL_BLANK_STATS_BEST_COMBO), Math.max(0, stats.bestCombo))
                .putLong(getPrefKey(KEY_FILL_BLANK_STATS_LAST_PLAYED_AT), Math.max(0L, stats.lastPlayedAt))
                .putString(getPrefKey(KEY_FILL_BLANK_STATS_LAST_TOPIC), nonEmpty(stats.lastTopic))
                .apply();
    }

    public boolean isFillBlankQuestionCompleted(String topic, String sourceWord) {
        if (topic == null || sourceWord == null) return false;
        Set<String> completed = preferences.getStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), new HashSet<>());
        return completed.contains(normalizeWord(topic) + "_" + normalizeWord(sourceWord));
    }

    public void markFillBlankQuestionCompleted(String topic, String sourceWord) {
        if (topic == null || sourceWord == null) return;
        String key = normalizeWord(topic) + "_" + normalizeWord(sourceWord);
        Set<String> completed = new HashSet<>(preferences.getStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), new HashSet<>()));
        if (completed.add(key)) {
            preferences.edit().putStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), completed).apply();
            invalidateDashboardCache();
        }
    }

    public void resetFillBlankProgressForTopic(String topic) {
        if (topic == null) return;
        String normalizedTopic = normalizeWord(topic);
        Set<String> completed = new HashSet<>(preferences.getStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), new HashSet<>()));
        Iterator<String> it = completed.iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(normalizedTopic + "_")) {
                it.remove();
            }
        }
        preferences.edit().putStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), completed).apply();
    }


    private List<FillBlankTopicItem> getFillBlankTopics() {
        List<FillBlankTopicItem> topicItems = new ArrayList<>();
        List<DomainItem> domains = getDomains();

        if (domains == null || domains.isEmpty()) {
            return topicItems;
        }

        Set<String> seenTopics = new HashSet<>();

        for (DomainItem domain : domains) {
            if (domain == null || domain.getTopics() == null) {
                continue;
            }

            for (TopicItem topicItem : domain.getTopics()) {
                if (topicItem == null) {
                    continue;
                }

                String topic = nonEmpty(topicItem.getTitle());
                String normalizedTopic = normalizeWord(topic);
                if (topic.isEmpty() || normalizedTopic.isEmpty() || !seenTopics.add(normalizedTopic)) {
                    continue;
                }

                List<FillBlankQuestionItem> remainingQuestions = buildFillBlankQuestionsForTopic(topic, false, false, false);
                if (remainingQuestions.isEmpty()) {
                    // Check if it's because it's completed or truly empty pool
                    List<FillBlankQuestionItem> allQuestions = buildFillBlankQuestionsForTopic(topic, false, false, true);
                    if (allQuestions.isEmpty()) {
                         continue;
                    }
                    FillBlankTopicItem item = new FillBlankTopicItem(
                            nonEmpty(domain.getName(), getDomainFromTopic(topic)),
                            topic,
                            getLearnedWordCandidatesForTopic(topic, null, false).size(),
                            allQuestions.size()
                    );
                    item.completedCount = allQuestions.size();
                    item.status = FillBlankTopicItem.STATUS_COMPLETED;
                    topicItems.add(item);
                } else {
                    List<FillBlankQuestionItem> allQuestions = buildFillBlankQuestionsForTopic(topic, false, false, true);
                    FillBlankTopicItem item = new FillBlankTopicItem(
                            nonEmpty(domain.getName(), getDomainFromTopic(topic)),
                            topic,
                            getLearnedWordCandidatesForTopic(topic, null, false).size(),
                            allQuestions.size()
                    );
                    item.completedCount = allQuestions.size() - remainingQuestions.size();
                    if (item.completedCount > 0) {
                        item.status = FillBlankTopicItem.STATUS_IN_PROGRESS;
                    } else {
                        item.status = FillBlankTopicItem.STATUS_NOT_STARTED;
                    }
                    topicItems.add(item);
                }
            }
        }

        topicItems.sort((left, right) -> {
            int byCount = Integer.compare(right.questionCount, left.questionCount);
            if (byCount != 0) {
                return byCount;
            }
            return nonEmpty(left.topic).compareToIgnoreCase(nonEmpty(right.topic));
        });
        return topicItems;
    }

    public List<FillBlankQuestionItem> buildFillBlankQuestionsForTopic(String topic,
                                                                        boolean allowFallback,
                                                                        boolean useNetworkTranslation,
                                                                        boolean includeCompleted) {
        List<FillBlankQuestionItem> questions = new ArrayList<>();
        if (topic == null || topic.trim().isEmpty()) {
            return questions;
        }

        List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic);
        if (topicPool == null || topicPool.isEmpty()) {
            return questions;
        }

        Set<String> learnedCandidates = getLearnedWordCandidatesForTopic(topic, topicPool, allowFallback);
        if (learnedCandidates.isEmpty()) {
            return questions;
        }

        String domain = getDomainFromTopic(topic);
        Set<String> completedSet = preferences.getStringSet(getPrefKey(KEY_FILL_BLANK_COMPLETED_QUESTIONS), new HashSet<>());
        String topicKeyPrefix = normalizeWord(topic) + "_";
        Set<String> dedupe = new HashSet<>();


        for (CustomVocabularyEntity entity : topicPool) {
            if (entity == null) {
                continue;
            }

            String sourceWord = sanitizeWordForFlashcard(entity.word);
            String normalizedWord = normalizeWord(sourceWord);

            // Filter by learned candidates
            if (!learnedCandidates.contains(normalizedWord)) {
                continue;
            }

            // FILTER: Skip already completed questions in this topic if NOT includeCompleted
            if (!includeCompleted && completedSet.contains(topicKeyPrefix + normalizedWord)) {
                continue;
            }

            FillBlankQuestionItem question = createFillBlankQuestionItem(domain, topic, entity, useNetworkTranslation);
            if (question == null) {
                continue;
            }

            String key = normalizeWord(question.maskedSentence + "|" + question.expectedAnswer);
            if (!key.isEmpty() && dedupe.add(key)) {
                questions.add(question);
            }
        }

        if (!includeCompleted) {
            Collections.shuffle(questions);
        }
        return questions;
    }


    private FillBlankQuestionItem createFillBlankQuestionItem(String domain,
                                                              String topic,
                                                              CustomVocabularyEntity entity,
                                                              boolean useNetworkTranslation) {
        String sourceWord = sanitizeWordForFlashcard(entity.word);
        String meaning = sanitizeMeaningForFlashcard(entity.meaning, sourceWord);
        String sentence = sanitizeExampleForFlashcard(entity.example, sourceWord);
        MaskedSentenceResult maskResult = buildMaskedSentence(sentence, sourceWord);
        if (maskResult == null) {
            return null;
        }

        String sentenceVi = sanitizeLearningText(entity.exampleVi);
        if (isMissingLearningField(sentenceVi)) {
            String translated = useNetworkTranslation ? translateEnglishSentenceToVietnamese(sentence) : "";
            sentenceVi = !isMissingLearningField(translated)
                    ? translated
                    : buildExampleTranslationFallback(sourceWord, meaning);
        }

        Set<String> acceptedAnswers = new LinkedHashSet<>();
        String normalizedExpected = normalizeQuizAnswer(maskResult.expectedAnswer);
        String normalizedSource = normalizeQuizAnswer(sourceWord);
        if (!normalizedExpected.isEmpty()) {
            acceptedAnswers.add(normalizedExpected);
        }
        if (!normalizedSource.isEmpty()) {
            acceptedAnswers.add(normalizedSource);
        }
        if (acceptedAnswers.isEmpty()) {
            return null;
        }

        return new FillBlankQuestionItem(
                domain,
                topic,
                sourceWord,
                sentence,
                maskResult.maskedSentence,
                sentenceVi,
                meaning,
                maskResult.expectedAnswer,
                acceptedAnswers
        );
    }

    private Set<String> getLearnedWordCandidatesForTopic(String topic,
                                                         List<CustomVocabularyEntity> topicPool,
                                                         boolean allowFallback) {
        Set<String> candidates = new HashSet<>();

        Set<String> learnedSet = getLearnedWordsForTopic(topic);
        for (String learned : learnedSet) {
            String normalized = normalizeWord(learned);
            if (!normalized.isEmpty()) {
                candidates.add(normalized);
            }
        }

        Map<String, Integer> scores = getTopicWordScores(topic);
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() >= 100) {
                String normalized = normalizeWord(entry.getKey());
                if (!normalized.isEmpty()) {
                    candidates.add(normalized);
                }
            }
        }

        candidates.addAll(getPersistedFlashcardLearnedWordsForTopic(topic, topicPool));

        return candidates;
    }

    private Set<String> getPersistedFlashcardLearnedWordsForTopic(String topic,
                                                                  List<CustomVocabularyEntity> topicPool) {
        Set<String> matches = new HashSet<>();
        String safeTopic = nonEmpty(topic);
        if (safeTopic.isEmpty()) {
            return matches;
        }

        List<CustomVocabularyEntity> sourcePool = topicPool != null ? topicPool : getTopicVocabularyPool(topic);
        if (sourcePool == null || sourcePool.isEmpty()) {
            return matches;
        }

        Set<String> poolWords = new HashSet<>();
        for (CustomVocabularyEntity entity : sourcePool) {
            if (entity == null) {
                continue;
            }
            String normalizedWord = normalizeWord(entity.word);
            if (!normalizedWord.isEmpty()) {
                poolWords.add(normalizedWord);
            }
        }

        if (poolWords.isEmpty()) {
            return matches;
        }

        String normalizedTopic = safeTopic.toLowerCase(Locale.US);
        String normalizedDomain = normalizeWord(getDomainFromTopic(safeTopic));
        List<WordEntry> savedWords = getSavedWords();
        for (WordEntry entry : savedWords) {
            if (entry == null) {
                continue;
            }

            String normalizedWord = normalizeWord(entry.getWord());
            if (normalizedWord.isEmpty() || !poolWords.contains(normalizedWord)) {
                continue;
            }

            String sourceTopic = safeString(entry.getNote()).toLowerCase(Locale.US);
            if (normalizedTopic.equals(sourceTopic)) {
                matches.add(normalizedWord);
                continue;
            }

            // Backward compatibility for older flashcard records without topic note.
            if (sourceTopic.isEmpty()) {
                String sourceDomain = normalizeWord(entry.getCategory());
                if (!normalizedDomain.isEmpty() && normalizedDomain.equals(sourceDomain)) {
                    matches.add(normalizedWord);
                }
            }
        }

        return matches;
    }

    private MaskedSentenceResult buildMaskedSentence(String sentence, String sourceWord) {
        String safeSentence = sanitizeLearningText(sentence);
        String safeWord = sanitizeLearningText(sourceWord);
        if (safeSentence.isEmpty() || safeWord.isEmpty()) {
            return null;
        }

        Pattern strictPattern = Pattern.compile(
                "(?i)\\b" + Pattern.quote(safeWord) + "(?:s|es|ed|ing)?\\b"
        );
        Matcher strictMatcher = strictPattern.matcher(safeSentence);
        if (strictMatcher.find()) {
            String expected = strictMatcher.group();
            String masked = strictMatcher.replaceFirst("_____" );
            return new MaskedSentenceResult(masked, expected);
        }

        Pattern relaxedPattern = Pattern.compile("(?i)" + Pattern.quote(safeWord));
        Matcher relaxedMatcher = relaxedPattern.matcher(safeSentence);
        if (!relaxedMatcher.find()) {
            return null;
        }

        String expected = relaxedMatcher.group();
        String masked = relaxedMatcher.replaceFirst("_____");
        return new MaskedSentenceResult(masked, expected);
    }

    private String normalizeQuizAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim().toLowerCase(Locale.US);
        normalized = normalized.replace('’', '\'');
        normalized = normalized.replaceAll("^[^a-z0-9']+|[^a-z0-9']+$", "");
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }

    public void getLeaderboardAsync(String period, DataCallback<List<LeaderboardItem>> callback) {
        firebaseUserStore.fetchLeaderboard(period, items -> mainHandler.post(() -> callback.onResult(items)));
    }

    private void clearUserRealtimeCollection(String uid, String collectionName) {
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(collectionName)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        return;
                    }

                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        batch.delete(document.getReference());
                    }
                    batch.commit();
                });
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private int safeInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(safeString(value));
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
            return Long.parseLong(safeString(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean safeBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(safeString(value)) || "1".equals(safeString(value));
    }

    private String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return word.trim().toLowerCase(Locale.US);
    }

    public void saveMapNodeCompleted(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return;
        }
        Set<String> completed = new HashSet<>(preferences.getStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), new HashSet<>()));
        completed.add(nodeId);
        preferences.edit().putStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), completed).apply();
        invalidateDashboardCache();
    }

    public boolean isMapNodeCompleted(String nodeId) {
        Set<String> completed = preferences.getStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), new HashSet<>());
        return completed.contains(nodeId);
    }
}
