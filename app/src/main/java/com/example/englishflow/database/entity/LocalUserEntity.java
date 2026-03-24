package com.example.englishflow.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_users")
public class LocalUserEntity {

    @PrimaryKey
    @NonNull
    public String email;

    @NonNull
    public String displayName;

    @NonNull
    public String passwordHash;

    public long createdAt;
    public long updatedAt;

    public LocalUserEntity(@NonNull String email, @NonNull String displayName, @NonNull String passwordHash, long createdAt, long updatedAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
