package com.example.englishflow.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.englishflow.database.entity.ChatSessionEntity;

import java.util.List;

@Dao
public interface ChatSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ChatSessionEntity session);

    @Query("SELECT * FROM chat_sessions ORDER BY startTime DESC")
    List<ChatSessionEntity> getAllSessions();

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    ChatSessionEntity getSessionById(String sessionId);

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    void deleteSession(String sessionId);

    @Query("UPDATE chat_sessions SET lastMessage = :lastMessage WHERE sessionId = :sessionId")
    void updateLastMessage(String sessionId, String lastMessage);

    @Query("DELETE FROM chat_sessions")
    void deleteAllSessions();
}
