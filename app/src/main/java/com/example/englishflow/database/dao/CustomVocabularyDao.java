package com.example.englishflow.database.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.englishflow.database.entity.CustomVocabularyEntity;

import java.util.List;

@Dao
public interface CustomVocabularyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CustomVocabularyEntity vocabulary);

    @Nullable
    @Query("SELECT * FROM custom_vocabulary WHERE word = :word LIMIT 1")
    CustomVocabularyEntity findByWord(String word);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSeed(List<CustomVocabularyEntity> vocabularies);

    @Query("SELECT COUNT(*) FROM custom_vocabulary")
    int getCount();

    @Query("SELECT * FROM custom_vocabulary ORDER BY updatedAt DESC")
    List<CustomVocabularyEntity> getAll();

    @Query("SELECT * FROM custom_vocabulary ORDER BY updatedAt DESC")
    androidx.lifecycle.LiveData<List<CustomVocabularyEntity>> getAllLive();

    @Query("UPDATE custom_vocabulary SET meaning = :meaning, updatedAt = :updatedAt WHERE word = :word AND isLocked = 0")
    int updateMeaningIfUnlocked(String word, String meaning, long updatedAt);

    @Query("UPDATE custom_vocabulary SET meaning = :meaning, updatedAt = :updatedAt WHERE word = :word")
    int forceUpdateMeaning(String word, String meaning, long updatedAt);

    @Query("UPDATE custom_vocabulary SET isLocked = :isLocked, updatedAt = :updatedAt WHERE word = :word")
    int setLocked(String word, boolean isLocked, long updatedAt);

    @Query("UPDATE custom_vocabulary SET " +
            "exampleVi = CASE WHEN exampleVi IS NULL OR TRIM(exampleVi) = '' OR exampleVi = :legacyExamplePlaceholder THEN :exampleVi ELSE exampleVi END, " +
            "usage = CASE WHEN usage IS NULL OR TRIM(usage) = '' OR usage = :legacyUsagePlaceholder THEN :usage ELSE usage END, " +
            "updatedAt = :updatedAt " +
            "WHERE word = :word")
    int enrichLearningFields(String word,
                             String exampleVi,
                             String usage,
                             String legacyExamplePlaceholder,
                             String legacyUsagePlaceholder,
                             long updatedAt);

    @Query("DELETE FROM custom_vocabulary WHERE word = :word")
    int deleteByWord(String word);

    @Query("SELECT DISTINCT domain FROM custom_vocabulary")
    List<String> getUniqueDomains();

    @Query("SELECT * FROM custom_vocabulary WHERE domain = :domain")
    List<CustomVocabularyEntity> getByDomain(String domain);

    @Query("SELECT COUNT(*) FROM custom_vocabulary WHERE domain = :domain")
    int getWordCountByDomain(String domain);

    @Query("SELECT * FROM custom_vocabulary WHERE word IN (:words) OR meaning LIKE '%' || :query || '%' LIMIT 5")
    List<CustomVocabularyEntity> searchRelevant(List<String> words, String query);
}
