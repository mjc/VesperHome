package com.sergioasenjo.ltvlauncher.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppPreferenceEntity::class, CategoryEntity::class, CategoryAppEntity::class, SpacerEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)]
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao

    abstract fun categoryDao(): CategoryDao
}
