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
    
    @Query("SELECT * FROM study_sessions WHERE userEmail = :userEmail ORDER BY startTime DESC")
    List<StudySessionEntity> getAllSessions(String userEmail);

    @Query("SELECT * FROM study_sessions WHERE userEmail = :userEmail ORDER BY startTime DESC LIMIT 1")
    StudySessionEntity getLastSession(String userEmail);
    
    @Query("SELECT * FROM study_sessions WHERE userEmail = :userEmail AND startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    List<StudySessionEntity> getSessionsForDay(String userEmail, long startOfDay, long endOfDay);
    
    @Query("SELECT SUM(wordsLearned) FROM study_sessions WHERE userEmail = :userEmail AND startTime >= :startTime")
    Integer getTotalWordsLearnedSince(String userEmail, long startTime);
    
    @Query("SELECT SUM(xpEarned) FROM study_sessions WHERE userEmail = :userEmail AND startTime >= :startOfDay AND startTime < :endOfDay")
    Integer getXpEarnedToday(String userEmail, long startOfDay, long endOfDay);
    
    @Query("SELECT SUM(endTime - startTime) / 60000 FROM study_sessions WHERE userEmail = :userEmail")
    Integer getTotalStudyMinutes(String userEmail);
    
    @Query("SELECT SUM(endTime - startTime) / 60000 FROM study_sessions WHERE userEmail = :userEmail AND startTime >= :startTime AND startTime < :endTime")
    Integer getStudyMinutesForPeriod(String userEmail, long startTime, long endTime);
    
    @Query("SELECT COUNT(*) FROM study_sessions WHERE userEmail = :userEmail")
    int getSessionCount(String userEmail);
    
    @Query("DELETE FROM study_sessions WHERE userEmail = :userEmail")
    void deleteAllSessions(String userEmail);
}
