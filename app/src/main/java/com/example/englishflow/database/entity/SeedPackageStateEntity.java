package com.example.englishflow.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "seed_package_state")
public class SeedPackageStateEntity {
    @PrimaryKey
    @NonNull
    public String packageName;

    public int version;
    public long updatedAt;

    public SeedPackageStateEntity(@NonNull String packageName, int version) {
        this.packageName = packageName;
        this.version = version;
        this.updatedAt = System.currentTimeMillis();
    }
}
