package com.sergioasenjo.vesperhome.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.sergioasenjo.vesperhome.media.TrackedMediaDao
import com.sergioasenjo.vesperhome.media.TrackedMediaEntity

@Database(
    entities = [
        AppPreferenceEntity::class,
        CategoryEntity::class,
        CategoryAppEntity::class,
        SpacerEntity::class,
        TrackedMediaEntity::class
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appPreferenceDao(): AppPreferenceDao

    abstract fun categoryDao(): CategoryDao

    abstract fun trackedMediaDao(): TrackedMediaDao
}
