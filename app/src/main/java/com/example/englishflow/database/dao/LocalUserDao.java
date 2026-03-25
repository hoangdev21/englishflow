package com.example.englishflow.database.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import com.example.englishflow.database.entity.LocalUserEntity;

@Dao
public interface LocalUserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(LocalUserEntity user);

    @Nullable
    @Query("SELECT * FROM local_users WHERE email = :email LIMIT 1")
    LocalUserEntity findByEmail(String email);

    @Query("UPDATE local_users SET displayName = :displayName, updatedAt = :updatedAt WHERE email = :email")
    int updateDisplayName(String email, String displayName, long updatedAt);

    @Query("SELECT * FROM local_users")
    List<LocalUserEntity> getAllUsers();
}
