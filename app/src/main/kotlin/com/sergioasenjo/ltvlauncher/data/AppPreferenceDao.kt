package com.sergioasenjo.ltvlauncher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences")
    fun observeAll(): Flow<List<AppPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(preference: AppPreferenceEntity)

    @Query("UPDATE app_preferences SET is_favorite = :isFavorite WHERE component_name = :packageName")
    suspend fun updateFavorite(packageName: String, isFavorite: Boolean)

    @Query("UPDATE app_preferences SET is_hidden = :isHidden WHERE component_name = :packageName")
    suspend fun updateHidden(packageName: String, isHidden: Boolean)

    @Query("UPDATE app_preferences SET manual_order = :manualOrder WHERE component_name = :packageName")
    suspend fun updateManualOrder(packageName: String, manualOrder: Long?)

    @Transaction
    suspend fun setFavorite(packageName: String, isFavorite: Boolean) {
        insertIfMissing(AppPreferenceEntity(packageName))
        updateFavorite(packageName, isFavorite)
    }

    @Transaction
    suspend fun setHidden(packageName: String, isHidden: Boolean) {
        insertIfMissing(AppPreferenceEntity(packageName))
        updateHidden(packageName, isHidden)
    }

    @Transaction
    suspend fun setManualOrder(packageName: String, manualOrder: Long?) {
        insertIfMissing(AppPreferenceEntity(packageName))
        updateManualOrder(packageName, manualOrder)
    }
}
