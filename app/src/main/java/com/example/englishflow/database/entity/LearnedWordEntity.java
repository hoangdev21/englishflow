package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "learned_words")
public class LearnedWordEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @androidx.annotation.NonNull
    public String userEmail = "";
    public String word;
    public String ipa;
    public String meaning;
    public String wordType;
    public String example;
    public String exampleVi;
    public String note;
    public String domain;
    public String topic;
    public long learnedAt;
    
    public LearnedWordEntity(String word, String ipa, String meaning, String wordType, String example, 
                            String exampleVi, String note, String domain, String topic) {
        this.word = word;
        this.ipa = ipa;
        this.meaning = meaning;
        this.wordType = wordType;
        this.example = example;
        this.exampleVi = exampleVi;
        this.note = note;
        this.domain = domain;
        this.topic = topic;
        this.learnedAt = System.currentTimeMillis();
    }
}
