package com.example.englishflow.database.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.englishflow.database.entity.FailedLabelLogEntity;

import java.util.List;

@Dao
public interface FailedLabelLogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(FailedLabelLogEntity entity);

    @Nullable
    @Query("SELECT * FROM failed_label_logs WHERE label = :label LIMIT 1")
    FailedLabelLogEntity findByLabel(String label);

    @Query("UPDATE failed_label_logs SET failCount = failCount + 1, suggestedAlias = :suggestedAlias, lastSeenAt = :lastSeenAt, resolved = 0 WHERE label = :label")
    void increment(String label, String suggestedAlias, long lastSeenAt);

    @Query("UPDATE failed_label_logs SET resolved = :resolved WHERE label = :label")
    void setResolved(String label, boolean resolved);

    @Query("SELECT * FROM failed_label_logs ORDER BY failCount DESC, lastSeenAt DESC LIMIT :limit")
    List<FailedLabelLogEntity> getTopFailed(int limit);
}
