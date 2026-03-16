package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.englishflow.database.entity.StudySessionEntity;

import java.util.List;

@Dao
public interface StudySessionDao {
    
    @Insert
    long insert(StudySessionEntity session);
    
    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    List<StudySessionEntity> getAllSessions();
    
    @Query("SELECT * FROM study_sessions WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    List<StudySessionEntity> getSessionsForDay(long startOfDay, long endOfDay);
    
    @Query("SELECT SUM(wordsLearned) FROM study_sessions WHERE startTime >= :startTime")
    Integer getTotalWordsLearnedSince(long startTime);
    
    @Query("SELECT SUM(xpEarned) FROM study_sessions WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    Integer getXpEarnedToday(long startOfDay, long endOfDay);
    
    @Query("SELECT SUM(endTime - startTime) / 60000 FROM study_sessions")
    Integer getTotalStudyMinutes();
    
    @Query("SELECT SUM(endTime - startTime) / 60000 FROM study_sessions WHERE startTime >= :startTime AND startTime < :endTime")
    Integer getStudyMinutesForPeriod(long startTime, long endTime);
    
    @Query("SELECT COUNT(*) FROM study_sessions")
    int getSessionCount();
    
    @Query("DELETE FROM study_sessions")
    void deleteAllSessions();
}
