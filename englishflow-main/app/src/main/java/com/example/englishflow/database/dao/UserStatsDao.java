package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.englishflow.database.entity.UserStatsEntity;

@Dao
public interface UserStatsDao {
    
    @Insert
    void insert(UserStatsEntity stats);
    
    @Update
    void update(UserStatsEntity stats);
    
    @Query("SELECT * FROM user_stats WHERE id = 1")
    UserStatsEntity getUserStats();
    
    @Query("UPDATE user_stats SET totalWordsLearned = totalWordsLearned + :count WHERE id = 1")
    void incrementWordsLearned(int count);
    
    @Query("UPDATE user_stats SET totalXpEarned = totalXpEarned + :xp WHERE id = 1")
    void addTotalXp(int xp);
    
    @Query("UPDATE user_stats SET xpTodayEarned = xpTodayEarned + :xp WHERE id = 1")
    void addDailyXp(int xp);
    
    @Query("UPDATE user_stats SET totalStudyMinutes = totalStudyMinutes + :minutes WHERE id = 1")
    void addStudyMinutes(int minutes);
}
