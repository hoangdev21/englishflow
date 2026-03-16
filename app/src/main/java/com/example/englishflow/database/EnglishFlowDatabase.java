package com.example.englishflow.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.englishflow.database.dao.ChatMessageDao;
import com.example.englishflow.database.dao.LearnedWordDao;
import com.example.englishflow.database.dao.StudySessionDao;
import com.example.englishflow.database.dao.UserStatsDao;
import com.example.englishflow.database.entity.ChatMessageEntity;
import com.example.englishflow.database.entity.LearnedWordEntity;
import com.example.englishflow.database.entity.StudySessionEntity;
import com.example.englishflow.database.entity.UserStatsEntity;

@Database(
    entities = {
        LearnedWordEntity.class,
        StudySessionEntity.class,
        UserStatsEntity.class,
        ChatMessageEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class EnglishFlowDatabase extends RoomDatabase {
    
    public abstract LearnedWordDao learnedWordDao();
    public abstract StudySessionDao studySessionDao();
    public abstract UserStatsDao userStatsDao();
    public abstract ChatMessageDao chatMessageDao();
    
    private static volatile EnglishFlowDatabase INSTANCE;
    
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
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
