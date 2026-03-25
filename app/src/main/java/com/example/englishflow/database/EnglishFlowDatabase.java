package com.example.englishflow.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.englishflow.database.dao.ChatMessageDao;
import com.example.englishflow.database.dao.ChatSessionDao;
import com.example.englishflow.database.dao.CustomVocabularyDao;
import com.example.englishflow.database.dao.FailedLabelLogDao;
import com.example.englishflow.database.dao.LearnedWordDao;
import com.example.englishflow.database.dao.LocalUserDao;
import com.example.englishflow.database.dao.SeedPackageStateDao;
import com.example.englishflow.database.dao.StudySessionDao;
import com.example.englishflow.database.dao.UserStatsDao;
import com.example.englishflow.database.entity.ChatMessageEntity;
import com.example.englishflow.database.entity.ChatSessionEntity;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.database.entity.FailedLabelLogEntity;
import com.example.englishflow.database.entity.LearnedWordEntity;
import com.example.englishflow.database.entity.LocalUserEntity;
import com.example.englishflow.database.entity.SeedPackageStateEntity;
import com.example.englishflow.database.entity.StudySessionEntity;
import com.example.englishflow.database.entity.UserStatsEntity;

@Database(
    entities = {
        LearnedWordEntity.class,
        StudySessionEntity.class,
        UserStatsEntity.class,
        ChatMessageEntity.class,
        ChatSessionEntity.class,
        CustomVocabularyEntity.class,
        FailedLabelLogEntity.class,
        SeedPackageStateEntity.class,
        LocalUserEntity.class
    },
    version = 7,
    exportSchema = false
)
public abstract class EnglishFlowDatabase extends RoomDatabase {
    
    public abstract LearnedWordDao learnedWordDao();
    public abstract StudySessionDao studySessionDao();
    public abstract UserStatsDao userStatsDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract ChatSessionDao chatSessionDao();
    public abstract CustomVocabularyDao customVocabularyDao();
    public abstract FailedLabelLogDao failedLabelLogDao();
    public abstract SeedPackageStateDao seedPackageStateDao();
    public abstract LocalUserDao localUserDao();
    
    private static volatile EnglishFlowDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS custom_vocabulary (word TEXT NOT NULL, meaning TEXT, ipa TEXT, example TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(word))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE custom_vocabulary ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE custom_vocabulary ADD COLUMN source TEXT");
            database.execSQL("ALTER TABLE custom_vocabulary ADD COLUMN domain TEXT");
            database.execSQL("UPDATE custom_vocabulary SET source = 'user' WHERE source IS NULL");
            database.execSQL("UPDATE custom_vocabulary SET domain = 'general' WHERE domain IS NULL");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS failed_label_logs (label TEXT NOT NULL, failCount INTEGER NOT NULL, suggestedAlias TEXT, resolved INTEGER NOT NULL, lastSeenAt INTEGER NOT NULL, PRIMARY KEY(label))");
            database.execSQL("CREATE TABLE IF NOT EXISTS seed_package_state (packageName TEXT NOT NULL, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(packageName))");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS local_users (email TEXT NOT NULL, displayName TEXT NOT NULL, passwordHash TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(email))");
        }
    };
    
    public static EnglishFlowDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (EnglishFlowDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        EnglishFlowDatabase.class,
                        "englishflow_db"
                    )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS chat_sessions (sessionId TEXT NOT NULL, title TEXT, startTime INTEGER NOT NULL, lastMessage TEXT, topic TEXT, PRIMARY KEY(sessionId))");
            // Add columns to existing chat_messages table
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId TEXT");
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN correction TEXT");
            database.execSQL("ALTER TABLE chat_messages ADD COLUMN explanation TEXT");
        }
    };
}
