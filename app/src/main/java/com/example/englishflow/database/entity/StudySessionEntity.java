package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "study_sessions")
public class StudySessionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @androidx.annotation.NonNull
    public String userEmail = "";
    public long startTime;
    public long endTime;
    public int wordsLearned;
    public String domain;
    public String topic;
    public int xpEarned;
    
    public StudySessionEntity(long startTime, long endTime, int wordsLearned, String domain, String topic, int xpEarned) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.wordsLearned = wordsLearned;
        this.domain = domain;
        this.topic = topic;
        this.xpEarned = xpEarned;
    }
    
    public int getDurationMinutes() {
        return (int) ((endTime - startTime) / 60000);
    }
}
