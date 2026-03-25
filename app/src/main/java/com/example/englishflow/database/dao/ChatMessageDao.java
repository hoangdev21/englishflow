package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.englishflow.database.entity.ChatMessageEntity;

import java.util.List;

@Dao
public interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC")
    List<ChatMessageEntity> getAllMessages();
    
    @Insert
    long insert(ChatMessageEntity message);
    
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<ChatMessageEntity> getMessagesBySession(String sessionId);
    
    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    List<ChatMessageEntity> getRecentMessages(int limit);
    
    @Query("DELETE FROM chat_messages")
    void deleteAllMessages();

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    void deleteSessionMessages(String sessionId);
}
