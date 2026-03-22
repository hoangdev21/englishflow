package com.example.englishflow.database.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.englishflow.database.entity.SeedPackageStateEntity;

@Dao
public interface SeedPackageStateDao {

    @Nullable
    @Query("SELECT * FROM seed_package_state WHERE packageName = :packageName LIMIT 1")
    SeedPackageStateEntity findByPackageName(String packageName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SeedPackageStateEntity entity);
}
