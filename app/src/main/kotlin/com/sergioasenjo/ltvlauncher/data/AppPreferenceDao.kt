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

    @Query("UPDATE app_preferences SET is_favorite = :isFavorite WHERE component_name = :componentName")
    suspend fun updateFavorite(componentName: String, isFavorite: Boolean)

    @Query("UPDATE app_preferences SET is_hidden = :isHidden WHERE component_name = :componentName")
    suspend fun updateHidden(componentName: String, isHidden: Boolean)

    @Query("UPDATE app_preferences SET manual_order = :manualOrder WHERE component_name = :componentName")
    suspend fun updateManualOrder(componentName: String, manualOrder: Long?)

    @Transaction
    suspend fun setFavorite(componentName: String, isFavorite: Boolean) {
        insertIfMissing(AppPreferenceEntity(componentName))
        updateFavorite(componentName, isFavorite)
    }

    @Transaction
    suspend fun setHidden(componentName: String, isHidden: Boolean) {
        insertIfMissing(AppPreferenceEntity(componentName))
        updateHidden(componentName, isHidden)
    }

    @Transaction
    suspend fun setManualOrder(componentName: String, manualOrder: Long?) {
        insertIfMissing(AppPreferenceEntity(componentName))
        updateManualOrder(componentName, manualOrder)
    }
}
