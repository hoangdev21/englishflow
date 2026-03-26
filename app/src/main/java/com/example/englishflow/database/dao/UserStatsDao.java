package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import com.example.englishflow.database.entity.UserStatsEntity;

@Dao
public interface UserStatsDao {
    
    @Insert
    void insert(UserStatsEntity stats);

    @Query("SELECT * FROM user_stats")
    List<UserStatsEntity> getAllStats();
    
    @Update
    void update(UserStatsEntity stats);
    
    @Query("SELECT * FROM user_stats WHERE userEmail = :userEmail")
    UserStatsEntity getUserStats(String userEmail);
    
    @Query("UPDATE user_stats SET totalWordsLearned = totalWordsLearned + :count WHERE userEmail = :userEmail")
    void incrementWordsLearned(String userEmail, int count);
    
    @Query("UPDATE user_stats SET totalXpEarned = totalXpEarned + :xp WHERE userEmail = :userEmail")
    void addTotalXp(String userEmail, int xp);
    
    @Query("UPDATE user_stats SET xpTodayEarned = xpTodayEarned + :xp WHERE userEmail = :userEmail")
    void addDailyXp(String userEmail, int xp);
    
    @Query("UPDATE user_stats SET totalStudyMinutes = totalStudyMinutes + :minutes WHERE userEmail = :userEmail")
    void addStudyMinutes(String userEmail, int minutes);
    
    @Query("UPDATE user_stats SET xpTodayEarned = 0 WHERE userEmail = :userEmail")
    void resetDailyXp(String userEmail);
    
    @Query("UPDATE user_stats SET lastStudyDate = :date WHERE userEmail = :userEmail")
    void updateLastStudyDate(String userEmail, long date);
    
    @Query("UPDATE user_stats SET currentStreak = :streak, bestStreak = :bestStreak, lastStudyDate = :date WHERE userEmail = :userEmail")
    void updateStreakAndDate(String userEmail, int streak, int bestStreak, long date);
}
