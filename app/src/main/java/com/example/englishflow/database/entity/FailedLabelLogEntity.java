package com.example.englishflow.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "failed_label_logs")
public class FailedLabelLogEntity {
    @PrimaryKey
    @NonNull
    public String label;

    public int failCount;
    public String suggestedAlias;
    public boolean resolved;
    public long lastSeenAt;

    public FailedLabelLogEntity(@NonNull String label, String suggestedAlias) {
        this.label = label;
        this.suggestedAlias = suggestedAlias;
        this.failCount = 1;
        this.resolved = false;
        this.lastSeenAt = System.currentTimeMillis();
    }
}
