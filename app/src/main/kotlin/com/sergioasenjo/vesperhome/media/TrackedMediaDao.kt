package com.sergioasenjo.vesperhome.media

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedMediaDao {
    @Query("SELECT * FROM tracked_media ORDER BY added_at DESC")
    fun observeAll(): Flow<List<TrackedMediaEntity>>

    @Query("SELECT * FROM tracked_media ORDER BY added_at DESC")
    suspend fun getAll(): List<TrackedMediaEntity>

    @Upsert
    suspend fun upsert(item: TrackedMediaEntity)
}
