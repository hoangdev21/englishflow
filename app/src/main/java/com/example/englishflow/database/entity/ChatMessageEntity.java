package com.example.englishflow.database.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessageEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String sessionId; // Unique ID for each conversation
    public String role; // "user" or "ai"
    public String content;
    public String correction;
    public String explanation;
    public long timestamp;
    
    public ChatMessageEntity(String sessionId, String role, String content, String correction, String explanation) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.correction = correction;
        this.explanation = explanation;
        this.timestamp = System.currentTimeMillis();
    }
}
