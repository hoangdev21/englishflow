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

    @Query("SELECT * FROM learned_words WHERE userEmail = :userEmail AND LOWER(word) = :normalizedWord LIMIT 1")
    LearnedWordEntity getWordByNameNormalized(String userEmail, String normalizedWord);

    @Query("UPDATE learned_words SET meaning = :meaning, learnedAt = :updatedAt WHERE userEmail = :userEmail AND LOWER(word) = :normalizedWord")
    int updateMeaningByNormalizedWord(String userEmail, String normalizedWord, String meaning, long updatedAt);

    @Query("DELETE FROM learned_words WHERE userEmail = :userEmail AND LOWER(word) = :normalizedWord")
    int deleteByNormalizedWord(String userEmail, String normalizedWord);
    
    @Query("DELETE FROM learned_words WHERE userEmail = :userEmail")
    void deleteAllWords(String userEmail);
}
