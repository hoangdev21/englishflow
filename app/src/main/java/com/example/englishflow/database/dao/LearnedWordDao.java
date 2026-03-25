package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.englishflow.database.entity.LearnedWordEntity;

import java.util.List;

@Dao
public interface LearnedWordDao {
    
    @Insert
    long insert(LearnedWordEntity word);
    
    @Query("SELECT * FROM learned_words WHERE userEmail = :userEmail ORDER BY learnedAt DESC")
    List<LearnedWordEntity> getAllWords(String userEmail);
    
    @Query("SELECT * FROM learned_words WHERE userEmail = :userEmail AND domain = :domain ORDER BY learnedAt DESC")
    List<LearnedWordEntity> getWordsByDomain(String userEmail, String domain);
    
    @Query("SELECT COUNT(*) FROM learned_words WHERE userEmail = :userEmail")
    int getTotalWordsCount(String userEmail);
    
    @Query("SELECT COUNT(*) FROM learned_words WHERE userEmail = :userEmail AND domain = :domain")
    int getWordCountByDomain(String userEmail, String domain);
    
    @Query("SELECT * FROM learned_words WHERE userEmail = :userEmail AND word = :word LIMIT 1")
    LearnedWordEntity getWordByName(String userEmail, String word);
    
    @Query("DELETE FROM learned_words WHERE userEmail = :userEmail")
    void deleteAllWords(String userEmail);
}
