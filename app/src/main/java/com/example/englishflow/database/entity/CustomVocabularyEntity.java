package com.example.englishflow.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "custom_vocabulary")
public class CustomVocabularyEntity {
    @PrimaryKey
    @NonNull
    public String word;

    public String meaning;
    public String ipa;
    public String example;
    public boolean isLocked;
    public String source;
    public String domain;
    public long updatedAt;

    public CustomVocabularyEntity(@NonNull String word, String meaning, String ipa, String example) {
        this.word = word;
        this.meaning = meaning;
        this.ipa = ipa;
        this.example = example;
        this.isLocked = false;
        this.source = "user";
        this.domain = "general";
        this.updatedAt = System.currentTimeMillis();
    }
}
