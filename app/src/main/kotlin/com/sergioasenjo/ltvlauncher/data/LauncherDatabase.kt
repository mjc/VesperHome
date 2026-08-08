package com.sergioasenjo.ltvlauncher.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppPreferenceEntity::class, CategoryEntity::class, CategoryAppEntity::class, SpacerEntity::class],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao

    abstract fun categoryDao(): CategoryDao
}
