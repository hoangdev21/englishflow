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
    
    @Query("SELECT * FROM learned_words ORDER BY learnedAt DESC")
    List<LearnedWordEntity> getAllWords();
    
    @Query("SELECT * FROM learned_words WHERE domain = :domain ORDER BY learnedAt DESC")
    List<LearnedWordEntity> getWordsByDomain(String domain);
    
    @Query("SELECT COUNT(*) FROM learned_words")
    int getTotalWordsCount();
    
    @Query("SELECT COUNT(*) FROM learned_words WHERE domain = :domain")
    int getWordCountByDomain(String domain);
    
    @Query("SELECT * FROM learned_words WHERE word = :word LIMIT 1")
    LearnedWordEntity getWordByName(String word);
    
    @Query("DELETE FROM learned_words")
    void deleteAllWords();
}
