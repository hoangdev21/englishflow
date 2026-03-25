package com.example.englishflow.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_sessions")
public class ChatSessionEntity {
    @PrimaryKey
    @NonNull
    public String sessionId; // UUID
    
    public String title;
    public long startTime;
    public String lastMessage;
    public String topic;

    public ChatSessionEntity(@NonNull String sessionId, String title, String topic, String lastMessage) {
        this.sessionId = sessionId;
        this.title = title;
        this.topic = topic;
        this.lastMessage = lastMessage;
        this.startTime = System.currentTimeMillis();
    }
}
