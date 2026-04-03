package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_stats")
public class UserStatsEntity {
    @PrimaryKey
    public int id = 1; // Single row for user stats
    
    public int totalWordsLearned = 0;
    public int totalWordsScanned = 0;
    public int currentStreak = 0;
    public int bestStreak = 0;
    public long lastStudyDate = 0;
    public int totalXpEarned = 0;
    public int xpTodayEarned = 0;
    public int totalStudyMinutes = 0;
    public String cefrLevel = "A1";
    
    public UserStatsEntity() {}
}
