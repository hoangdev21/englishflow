package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessageEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String role; // "user" or "ai"
    public String content;
    public long timestamp;
    
    public ChatMessageEntity(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }
}
