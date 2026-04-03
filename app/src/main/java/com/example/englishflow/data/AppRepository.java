package com.example.englishflow.data;

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
import com.example.englishflow.database.entity.LocalUserEntity;
import com.example.englishflow.database.entity.StudySessionEntity;
import com.example.englishflow.database.entity.UserStatsEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
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
    private static final String SEED_FILE_NAME = "vocab_seed_packages.json";

    private static AppRepository instance;

    private final SharedPreferences preferences;
    private final EnglishFlowDatabase database;
    private final LocalAuthStore authStore;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context appContext;

    private AppRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        database = EnglishFlowDatabase.getInstance(appContext);
        authStore = new LocalAuthStore(appContext);
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
        String email = authStore.getCurrentUser() != null ? authStore.getCurrentUser().email : "guest";
        return email != null ? email : "guest";
    }

    private String getPrefKey(String baseKey) {
        return getCurrentEmail() + "_" + baseKey;
    }

    public String getUserName() {
        if (authStore != null && authStore.getCurrentUser() != null) {
            String displayName = authStore.getCurrentUser().displayName;
            if (displayName != null && !displayName.isEmpty()) {
                return displayName;
            }
        }
        return preferences.getString(getPrefKey(KEY_NAME), "Bạn");
    }

    public void setUserName(String name) {
        preferences.edit().putString(getPrefKey(KEY_NAME), name).apply();
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
    }

    public String getLastTopicTitle() {
        String saved = preferences.getString(getPrefKey(KEY_LAST_TOPIC_TITLE), null);
        if (saved != null) return saved;
        
        // Fallback to last session from DB
        StudySessionEntity last = database.studySessionDao().getLastSession(getCurrentEmail());
        if (last != null) return last.topic;
        
        return "Giao tiếp cơ bản"; // Default if clean slate
    }

    public String getLastTopicDomain() {
        String saved = preferences.getString(getPrefKey(KEY_LAST_TOPIC_DOMAIN), null);
        if (saved != null) return saved;
        
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
            // Use specialized internal getters to ensure logic consistency (like daily reset)
            UserStatsEntity stats = getOrCreateUserStats();
            String email = getCurrentEmail();
            
            List<StudySessionEntity> sessions = database.studySessionDao().getAllSessions(email);
            List<StudySession> studySessions = new ArrayList<>();
            if (sessions != null) {
                for (StudySessionEntity entity : sessions) {
                    studySessions.add(new StudySession(
                        entity.startTime, entity.endTime, entity.wordsLearned,
                        entity.domain, entity.topic, entity.xpEarned
                    ));
                }
            }
            
            UserProgress progress = new UserProgress();
            // Use real-time counts from DAOs/Calculated logic for better accuracy
            progress.totalWordsLearned = getLearnedWords(); 
            progress.totalWordsScanned = stats.totalWordsScanned;
            progress.currentStreak = stats.currentStreak;
            progress.bestStreak = stats.bestStreak;
            progress.totalXpEarned = stats.totalXpEarned;
            progress.xpTodayEarned = stats.xpTodayEarned;
            progress.totalStudyMinutes = stats.totalStudyMinutes;
            progress.cefrLevel = getCefrLevel();
            progress.setStudySessions(studySessions);
            
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

    public int getStreakDays() {
        try {
            UserStatsEntity stats = getOrCreateUserStats();
            return stats != null ? stats.currentStreak : 0;
        } catch (Exception e) {
            return 0;
        }
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
            List<ChatMessageEntity> messages = database.chatMessageDao().getAllMessages();
            return messages != null ? messages.size() : 0;
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
        return 3000 - getLearnedWords(); // Total vocabulary reference
    }

    public List<Integer> getWeeklyStudyMinutes() {
        List<Integer> weeklyMinutes = new ArrayList<>();
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
                    Integer minutes = database.studySessionDao().getStudyMinutesForPeriod(getCurrentEmail(), startOfDay, endOfDay);
                    weeklyMinutes.add(minutes != null ? minutes : 0);
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
            Integer minutes = database.studySessionDao().getTotalStudyMinutes(getCurrentEmail());
            return minutes != null ? minutes : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<AchievementItem> getAchievements() {
        List<AchievementItem> list = new ArrayList<>();
        int streak = getStreakDays();
        int learned = getLearnedWords();
        int scanned = getScannedImages();
        int chat = getChatSessions();
        
        list.add(new AchievementItem("7 ngày bền bỉ", "Giữ streak 7 ngày", "🔥", streak >= 7));
        list.add(new AchievementItem("Tân binh scan", "Scan 10 vật thể", "📷", scanned >= 10));
        list.add(new AchievementItem("100 từ đầu tiên", "Học 100 từ", "📚", learned >= 100));
        list.add(new AchievementItem("Chat pro", "20 cuộc chat", "💬", chat >= 20));
        list.add(new AchievementItem("Ngày bền bỉ", "Vào học mỗi ngày 30 ngày", "⏱️", streak >= 30));
        list.add(new AchievementItem("Hacker từ vựng", "Học 250 từ", "🧠", learned >= 250));
        return list;
    }

    // New internal storage for topic progress (mocking for now since we don't have this table yet)
    // In a real app, this should be a Room table like 'topic_progress'
    private static final java.util.Map<String, String> TOPIC_PROGRESS = new java.util.HashMap<>();

    public void updateTopicStatus(String topic, String status) {
        TOPIC_PROGRESS.put(topic, status);
        // In real app: database.topicProgressDao().upsert(new TopicProgressEntity(topic, status))
    }

    public String getTopicStatus(String topic) {
        int progress = getTopicProgressPercent(topic);
        if (progress >= 100) {
            return TopicItem.STATUS_COMPLETED;
        }
        if (progress > 0) {
            return TopicItem.STATUS_LEARNING;
        }
        return TOPIC_PROGRESS.getOrDefault(topic, TopicItem.STATUS_NOT_STARTED);
    }

    public List<DomainItem> getDomains() {
        List<DomainItem> list = new ArrayList<>();
        try {
            List<String> domains = database.customVocabularyDao().getUniqueDomains();
            for (String domain : domains) {
                if (domain.equalsIgnoreCase("general") || domain.isEmpty()) continue;
                
                String emoji = getEmojiForDomain(domain);
                String start = getGradientStartForDomain(domain);
                String end = getGradientEndForDomain(domain);
                int progress = getDomainProgress(domain);
                int bgRes = getBackgroundImageForDomain(domain);
                
                list.add(new DomainItem(
                    emoji,
                    domain,
                    progress,
                    start,
                    end,
                    bgRes,
                    sampleTopics(domain)
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
        return list;
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

    private int getDomainProgress(String domain) {
        List<TopicItem> topics = sampleTopics(domain);
        if (topics.isEmpty()) {
            return 0;
        }

        float score = 0f;
        for (TopicItem topic : topics) {
            score += getTopicProgressPercent(topic.getTitle());
        }
        return Math.min(100, Math.round(score / topics.size()));
    }

    private int getWordCountByDomain(String domain) {
        try {
            Integer count = database.learnedWordDao().getWordCountByDomain(getCurrentEmail(), domain);
            return count != null ? count : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<FlashcardItem> getFlashcardsForTopic(String topic) {
        List<FlashcardItem> cards = new ArrayList<>();
        Map<String, Integer> wordScores = getTopicWordScores(topic);
        try {
            List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic);
            if (!topicPool.isEmpty()) {
                String domain = getDomainFromTopic(topic);
                for (CustomVocabularyEntity entity : topicPool) {
                    String normalizedWord = normalizeWord(entity.word);
                    int score = wordScores.getOrDefault(normalizedWord, 0);
                    if (score >= 100) {
                        continue;
                    }
                    String exampleVi = (entity.exampleVi != null && !entity.exampleVi.isEmpty()) 
                            ? entity.exampleVi 
                            : "Bản dịch đang được cập nhật...";
                    String usage = (entity.usage != null && !entity.usage.isEmpty()) 
                            ? entity.usage 
                            : "Thông tin cách dùng đang được biên soạn cho từ này.";

                    cards.add(new FlashcardItem(
                            getEmojiForDomain(domain),
                            entity.word,
                            entity.ipa,
                            entity.meaning,
                            entity.example,
                            exampleVi,
                            usage
                    ));
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

        if (cards.isEmpty() && getTopicVocabularyPool(topic).isEmpty()) {
            // High-quality mock fallback for UI testing
            cards.add(new FlashcardItem("🍽️", "menu", "/ˈmen.juː/", "thực đơn", "Could I see the menu, please?", "Làm ơn cho tôi xem thực đơn được không?", "Sử dụng khi yêu cầu danh sách các món ăn tại nhà hàng."));
            cards.add(new FlashcardItem("🥗", "healthy", "/ˈhel.θi/", "lành mạnh", "I try to eat healthy meals every day.", "Tôi cố gắng ăn các bữa ăn lành mạnh mỗi ngày.", "Dùng để mô tả thói quen hoặc thực phẩm có lợi cho sức khỏe."));
            cards.add(new FlashcardItem("🍳", "fry", "/fraɪ/", "chiên, rán", "Fry the eggs in a little oil.", "Rán trứng trong một ít dầu.", "Hành động chế biến thực phẩm bằng dầu nóng."));
            cards.add(new FlashcardItem("🥖", "bread", "/bred/", "bánh mì", "He bought a loaf of fresh bread.", "Anh ấy đã mua một ổ bánh mì mới ra lò.", "Danh từ chỉ loại thực phẩm phổ biến làm từ bột mì."));
            cards.add(new FlashcardItem("🍶", "sauce", "/sɔːs/", "nước sốt", "Add some more soy sauce to the stir-fry.", "Cho thêm một ít nước tương vào món xào.", "Chất lỏng dùng để tăng hương vị cho món ăn."));
        }
        return cards;
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
                // Check if word already exists
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
        return database.customVocabularyDao().findByWord(normalized);
    }

    public void saveCustomVocabulary(String label, String meaning) {
        if (label == null || label.trim().isEmpty() || meaning == null || meaning.trim().isEmpty()) {
            return;
        }

        String normalized = ScanAnalyzer.canonicalizeLabel(label);
        executorService.execute(() -> {
            CustomVocabularyEntity existing = database.customVocabularyDao().findByWord(normalized);
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
            database.customVocabularyDao().upsert(entity);
        });
    }

    public List<CustomVocabularyEntity> getAllCustomVocabulary() {
        return database.customVocabularyDao().getAll();
    }

    public boolean updateCustomMeaning(String word, String meaning, boolean forceIfLocked) {
        if (word == null || word.trim().isEmpty() || meaning == null || meaning.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);
        long now = System.currentTimeMillis();
        int updated = forceIfLocked
                ? database.customVocabularyDao().forceUpdateMeaning(normalized, meaning.trim(), now)
                : database.customVocabularyDao().updateMeaningIfUnlocked(normalized, meaning.trim(), now);
        return updated > 0;
    }

    public boolean deleteCustomVocabulary(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);
        return database.customVocabularyDao().deleteByWord(normalized) > 0;
    }

    public boolean setCustomVocabularyLocked(String word, boolean isLocked) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        String normalized = ScanAnalyzer.canonicalizeLabel(word);
        return database.customVocabularyDao().setLocked(normalized, isLocked, System.currentTimeMillis()) > 0;
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
        executorService.execute(() -> {
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
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(appContext.getAssets().open(SEED_FILE_NAME), StandardCharsets.UTF_8)
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
            }

            SeedPackageStateEntity nextState = new SeedPackageStateEntity(packageName, packageVersion);
            database.seedPackageStateDao().upsert(nextState);
        }
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
            List<CustomVocabularyEntity> result = database.customVocabularyDao().getAll();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public void getTopFailedLabelsAsync(int limit, DataCallback<List<FailedLabelLogEntity>> callback) {
        executorService.execute(() -> {
            List<FailedLabelLogEntity> result = database.failedLabelLogDao().getTopFailed(limit);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    public androidx.lifecycle.LiveData<List<CustomVocabularyEntity>> getAllCustomVocabularyLive() {
        return database.customVocabularyDao().getAllLive();
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void addStudySession(StudySession session) {
        executorService.execute(() -> {
            try {
                String email = getCurrentEmail();
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
                
                // Ensure daily context
                UserStatsEntity stats = getOrCreateUserStats();
                
                // Use robust specialized updates
                database.userStatsDao().addStudyMinutes(email, (int) entity.getDurationMinutes());
                if (session.getXpEarned() > 0) {
                    database.userStatsDao().addTotalXp(email, session.getXpEarned());
                    database.userStatsDao().addDailyXp(email, session.getXpEarned());
                }
                updateStreakForNewActivity(email, stats, System.currentTimeMillis());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getCefrLevel() {
        int learned = getLearnedWords();
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
                database.learnedWordDao().deleteAllWords(email);
                database.studySessionDao().deleteAllSessions(email);
                // database.chatMessageDao().deleteAllMessages(email); // If chat supports multi-user
                
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
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<TopicItem> sampleTopics(String domain) {
        List<TopicItem> topics = new ArrayList<>();
        topics.add(new TopicItem(domain + " cơ bản", getTopicStatus(domain + " cơ bản")));
        topics.add(new TopicItem(domain + " giao tiếp", getTopicStatus(domain + " giao tiếp")));
        topics.add(new TopicItem(domain + " nâng cao", getTopicStatus(domain + " nâng cao")));
        topics.add(new TopicItem(domain + " thực chiến", getTopicStatus(domain + " thực chiến")));
        topics.add(new TopicItem(domain + " chuyên sâu", getTopicStatus(domain + " chuyên sâu")));
        topics.add(new TopicItem(domain + " chuyên ngành", getTopicStatus(domain + " chuyên ngành")));
        topics.add(new TopicItem(domain + " thuật ngữ", getTopicStatus(domain + " thuật ngữ")));
        topics.add(new TopicItem(domain + " tình huống", getTopicStatus(domain + " tình huống")));
        topics.add(new TopicItem(domain + " tiếng lóng", getTopicStatus(domain + " tiếng lóng")));
        topics.add(new TopicItem(domain + " thành ngữ", getTopicStatus(domain + " thành ngữ")));
        topics.add(new TopicItem(domain + " mở rộng", getTopicStatus(domain + " mở rộng")));
        topics.add(new TopicItem(domain + " ứng dụng", getTopicStatus(domain + " ứng dụng")));
        topics.add(new TopicItem(domain + " nâng tầm", getTopicStatus(domain + " nâng tầm")));
        topics.add(new TopicItem(domain + " học thuật", getTopicStatus(domain + " học thuật")));
        topics.add(new TopicItem(domain + " chuyên gia", getTopicStatus(domain + " chuyên gia")));
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
        List<CustomVocabularyEntity> topicPool = getTopicVocabularyPool(topic);
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
        List<CustomVocabularyEntity> topicPool = new ArrayList<>();
        try {
            String domain = getDomainFromTopic(topic);
            int startIndex = getStartIndexForTopic(topic);
            List<CustomVocabularyEntity> entities = database.customVocabularyDao().getByDomain(domain);
            if (entities == null || entities.isEmpty()) {
                return topicPool;
            }

            int targetSize = Math.min(10, entities.size());
            for (int i = 0; i < targetSize; i++) {
                int index = (startIndex + i) % entities.size();
                topicPool.add(entities.get(index));
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
                List<LearnedWordEntity> entities = database.learnedWordDao().getAllWords(getCurrentEmail());
                mainHandler.post(() -> callback.onResult(entities != null ? entities : new ArrayList<>()));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onResult(new ArrayList<>()));
            }
        });
    }

    public void getLeaderboardAsync(String period, DataCallback<List<LeaderboardItem>> callback) {
        executorService.execute(() -> {
            List<LeaderboardItem> items = new ArrayList<>();
            try {
                List<UserStatsEntity> allStats = database.userStatsDao().getAllStats();
                Map<String, UserStatsEntity> statsMap = new HashMap<>();
                for (UserStatsEntity s : allStats) {
                    statsMap.put(s.userEmail, s);
                }

                List<LocalUserEntity> allUsers = database.localUserDao().getAllUsers();
                for (LocalUserEntity user : allUsers) {
                    UserStatsEntity s = statsMap.get(user.email);
                    int score = 0;
                    if (s != null) {
                        if ("today".equals(period)) {
                            score = s.xpTodayEarned;
                        } else {
                            score = s.totalXpEarned;
                        }
                    }
                    items.add(new LeaderboardItem(user.displayName, user.email, score, 0));
                }

                // (Mock data removed as requested - showing real database data only)

                // Sort by score descending
                Collections.sort(items, (a, b) -> Integer.compare(b.score, a.score));
                
                // Assign ranks
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).rank = i + 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mainHandler.post(() -> callback.onResult(items));
        });
    }

    private String normalizeWord(String word) {
        if (word == null) {
            return "";
        }
        return word.trim().toLowerCase(Locale.US);
    }

    public void saveMapNodeCompleted(String nodeId) {
        Set<String> completed = new HashSet<>(preferences.getStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), new HashSet<>()));
        completed.add(nodeId);
        preferences.edit().putStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), completed).apply();
    }

    public boolean isMapNodeCompleted(String nodeId) {
        Set<String> completed = preferences.getStringSet(getPrefKey(KEY_COMPLETED_MAP_NODES), new HashSet<>());
        return completed.contains(nodeId);
    }
}
