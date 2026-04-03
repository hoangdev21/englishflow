package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "learned_words")
public class LearnedWordEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String word;
    public String ipa;
    public String meaning;
    public String example;
    public String domain;
    public String topic;
    public long learnedAt;
    
    public LearnedWordEntity(String word, String ipa, String meaning, String example, String domain, String topic) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.example = example;
        this.domain = domain;
        this.topic = topic;
        this.learnedAt = System.currentTimeMillis();
    }
}
