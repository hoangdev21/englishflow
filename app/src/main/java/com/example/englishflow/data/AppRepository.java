package com.example.englishflow.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.englishflow.database.EnglishFlowDatabase;
import com.example.englishflow.database.entity.ChatMessageEntity;
import com.example.englishflow.database.entity.LearnedWordEntity;
import com.example.englishflow.database.entity.StudySessionEntity;
import com.example.englishflow.database.entity.UserStatsEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppRepository {
    private static final String PREFS = "englishflow_prefs";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";

    private static AppRepository instance;

    private final SharedPreferences preferences;
    private final EnglishFlowDatabase database;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private AppRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        database = EnglishFlowDatabase.getInstance(context.getApplicationContext());
    }

    public static synchronized AppRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AppRepository(context);
        }
        return instance;
    }

    public String getUserName() {
        return preferences.getString(KEY_NAME, "An");
    }

    public void setUserName(String name) {
        preferences.edit().putString(KEY_NAME, name).apply();
    }

    public int getReminderHour() {
        return preferences.getInt(KEY_REMINDER_HOUR, 20);
    }

    public int getReminderMinute() {
        return preferences.getInt(KEY_REMINDER_MINUTE, 0);
    }

    public void setReminderTime(int hour, int minute) {
        preferences.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply();
    }

    // ===== User Progress & Statistics (REAL DATA) =====
    
    public UserProgress getUserProgress() {
        try {
            UserStatsEntity stats = database.userStatsDao().getUserStats();
            if (stats == null) {
                stats = new UserStatsEntity();
                database.userStatsDao().insert(stats);
            }
            
            List<StudySessionEntity> sessions = database.studySessionDao().getAllSessions();
            if (sessions == null) {
                sessions = new ArrayList<>();
            }
            
            List<StudySession> studySessions = new ArrayList<>();
            for (StudySessionEntity entity : sessions) {
                studySessions.add(new StudySession(
                    entity.startTime,
                    entity.endTime,
                    entity.wordsLearned,
                    entity.domain,
                    entity.topic,
                    entity.xpEarned
                ));
            }
            
            UserProgress progress = new UserProgress();
            progress.totalWordsLearned = stats.totalWordsLearned;
            progress.totalWordsScanned = stats.totalWordsScanned;
            progress.currentStreak = stats.currentStreak;
            progress.bestStreak = stats.bestStreak;
            progress.totalXpEarned = stats.totalXpEarned;
            progress.xpTodayEarned = stats.xpTodayEarned;
            progress.totalStudyMinutes = stats.totalStudyMinutes;
            progress.cefrLevel = stats.cefrLevel;
            progress.setStudySessions(studySessions);
            
            return progress;
        } catch (Exception e) {
            e.printStackTrace();
            return new UserProgress();
        }
    }

    public int getStreakDays() {
        try {
            UserStatsEntity stats = database.userStatsDao().getUserStats();
            return stats != null ? stats.currentStreak : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getXpToday() {
        try {
            UserStatsEntity stats = database.userStatsDao().getUserStats();
            return stats != null ? stats.xpTodayEarned : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getXpGoal() {
        return 120;
    }

    public int getLearnedWords() {
        try {
            Integer count = database.learnedWordDao().getTotalWordsCount();
            return count != null ? count : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getScannedImages() {
        try {
            UserStatsEntity stats = database.userStatsDao().getUserStats();
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
            UserStatsEntity stats = database.userStatsDao().getUserStats();
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
            Calendar cal = Calendar.getInstance();
            
            // Get minutes for each day of the past 7 days
            for (int i = 6; i >= 0; i--) {
                cal.add(Calendar.DAY_OF_MONTH, -i);
                long startOfDay = getStartOfDay(cal.getTimeInMillis());
                long endOfDay = getEndOfDay(cal.getTimeInMillis());
                
                try {
                    Integer minutes = database.studySessionDao().getStudyMinutesForPeriod(startOfDay, endOfDay);
                    weeklyMinutes.add(minutes != null ? minutes : 0);
                } catch (Exception e) {
                    weeklyMinutes.add(0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Return 7 zeros if database fails
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
            Integer minutes = database.studySessionDao().getTotalStudyMinutes();
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
        
        list.add(new AchievementItem("7 ngày bền bỉ", "Giữ streak 7 ngày", streak >= 7));
        list.add(new AchievementItem("Tân binh scan", "Scan 10 vật thể", scanned >= 10));
        list.add(new AchievementItem("100 từ đầu tiên", "Học 100 từ", learned >= 100));
        list.add(new AchievementItem("Chat pro", "20 cuộc chat", chat >= 20));
        list.add(new AchievementItem("Ngày bền bỉ", "Vào học mỗi ngày 30 ngày", streak >= 30));
        list.add(new AchievementItem("Hacker từ vựng", "Học 250 từ", learned >= 250));
        return list;
    }

    public List<DomainItem> getDomains() {
        List<DomainItem> list = new ArrayList<>();
        list.add(new DomainItem("🍜", "Ẩm thực", getWordCountByDomain("Ẩm thực"), "#1A7A5E", "#2AAE84", sampleTopics("Ẩm thực")));
        list.add(new DomainItem("✈️", "Du lịch", getWordCountByDomain("Du lịch"), "#207A9F", "#3AB6E6", sampleTopics("Du lịch")));
        list.add(new DomainItem("💼", "Công việc", getWordCountByDomain("Công việc"), "#5A759A", "#7A96BC", sampleTopics("Công việc")));
        list.add(new DomainItem("🏥", "Sức khoẻ", getWordCountByDomain("Sức khoẻ"), "#2E8F6C", "#66B88F", sampleTopics("Sức khoẻ")));
        list.add(new DomainItem("🎓", "Học tập", getWordCountByDomain("Học tập"), "#8F6A2E", "#C8944A", sampleTopics("Học tập")));
        list.add(new DomainItem("🏠", "Nhà cửa", getWordCountByDomain("Nhà cửa"), "#557B63", "#7FA28B", sampleTopics("Nhà cửa")));
        return list;
    }

    private int getWordCountByDomain(String domain) {
        try {
            Integer count = database.learnedWordDao().getWordCountByDomain(domain);
            return count != null ? count : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<FlashcardItem> getFlashcardsForTopic(String topic) {
        List<FlashcardItem> cards = new ArrayList<>();
        cards.add(new FlashcardItem("🍽️", "menu", "/ˈmen.juː/", "thực đơn", "Could I see the menu, please?"));
        cards.add(new FlashcardItem("🥗", "healthy", "/ˈhel.θi/", "lành mạnh", "I try to eat healthy meals every day."));
        cards.add(new FlashcardItem("☕", "beverage", "/ˈbev.ər.ɪdʒ/", "đồ uống", "Tea is my favorite beverage."));
        cards.add(new FlashcardItem("🍲", "broth", "/brɒθ/", "nước dùng", "This broth smells delicious."));
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
                "Please recycle this plastic bottle.",
                "Nhà cửa",
                "The word 'bottle' comes from Medieval Latin 'butticula'.",
                relatedWords
        );
    }

    public List<WordEntry> getSavedWords() {
        List<WordEntry> words = new ArrayList<>();
        try {
            List<LearnedWordEntity> entities = database.learnedWordDao().getAllWords();
            if (entities != null) {
                for (LearnedWordEntity entity : entities) {
                    words.add(new WordEntry(entity.word, entity.ipa, entity.meaning, entity.example, entity.domain));
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
                // Check if word already exists
                LearnedWordEntity existing = database.learnedWordDao().getWordByName(wordEntry.getWord());
                if (existing != null) {
                    return;
                }
                
                LearnedWordEntity entity = new LearnedWordEntity(
                    wordEntry.getWord(),
                    wordEntry.getIpa(),
                    wordEntry.getMeaning(),
                    wordEntry.getExample(),
                    wordEntry.getCategory(),
                    ""
                );
                database.learnedWordDao().insert(entity);
                
                // Update stats
                UserStatsEntity stats = database.userStatsDao().getUserStats();
                if (stats != null) {
                    stats.totalWordsLearned += 1;
                    database.userStatsDao().update(stats);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void removeWord(WordEntry wordEntry) {
        // Note: You might want to add a delete method to the DAO if needed
    }

    public void increaseScanCount() {
        executorService.execute(() -> {
            try {
                UserStatsEntity stats = database.userStatsDao().getUserStats();
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
        executorService.execute(() -> {
            try {
                UserStatsEntity stats = database.userStatsDao().getUserStats();
                if (stats != null) {
                    stats.xpTodayEarned += xp;
                    stats.totalXpEarned += xp;
                    database.userStatsDao().update(stats);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void addStudySession(StudySession session) {
        executorService.execute(() -> {
            try {
                StudySessionEntity entity = new StudySessionEntity(
                    session.getStartTime(),
                    session.getEndTime(),
                    session.getWordsLearned(),
                    session.getDomainName(),
                    session.getTopicName(),
                    session.getXpEarned()
                );
                database.studySessionDao().insert(entity);
                
                // Update user stats
                UserStatsEntity stats = database.userStatsDao().getUserStats();
                if (stats != null) {
                    stats.totalStudyMinutes += entity.getDurationMinutes();
                    stats.xpTodayEarned += session.getXpEarned();
                    stats.totalXpEarned += session.getXpEarned();
                    database.userStatsDao().update(stats);
                }
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
                database.learnedWordDao().deleteAllWords();
                database.studySessionDao().deleteAllSessions();
                database.chatMessageDao().deleteAllMessages();
                
                UserStatsEntity stats = new UserStatsEntity();
                database.userStatsDao().update(stats);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private List<TopicItem> sampleTopics(String domain) {
        List<TopicItem> topics = new ArrayList<>();
        topics.add(new TopicItem(domain + " cơ bản", TopicItem.STATUS_COMPLETED));
        topics.add(new TopicItem(domain + " giao tiếp", TopicItem.STATUS_LEARNING));
        topics.add(new TopicItem(domain + " nâng cao", TopicItem.STATUS_NOT_STARTED));
        topics.add(new TopicItem(domain + " thực chiến", TopicItem.STATUS_NOT_STARTED));
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
}
