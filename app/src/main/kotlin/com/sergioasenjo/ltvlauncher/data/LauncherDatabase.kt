package com.sergioasenjo.ltvlauncher.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppPreferenceEntity::class],
    version = 1
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao
}
