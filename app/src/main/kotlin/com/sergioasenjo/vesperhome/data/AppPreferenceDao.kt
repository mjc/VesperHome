package com.sergioasenjo.vesperhome.data

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

    @Query("SELECT * FROM app_preferences")
    suspend fun getAll(): List<AppPreferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(preferences: List<AppPreferenceEntity>)

    @Query("DELETE FROM app_preferences")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(preference: AppPreferenceEntity)

    @Query("UPDATE app_preferences SET is_favorite = :isFavorite WHERE component_name = :packageName")
    suspend fun updateFavorite(packageName: String, isFavorite: Boolean)

    @Query("UPDATE app_preferences SET is_hidden = :isHidden WHERE component_name = :packageName")
    suspend fun updateHidden(packageName: String, isHidden: Boolean)

    @Query("UPDATE app_preferences SET manual_order = :manualOrder WHERE component_name = :packageName")
    suspend fun updateManualOrder(packageName: String, manualOrder: Long?)

    @Query("UPDATE app_preferences SET last_used_at = :lastUsedAt WHERE component_name = :packageName")
    suspend fun updateLastUsedAt(packageName: String, lastUsedAt: Long)

    @Query(
        "UPDATE app_preferences SET custom_banner_revision = " +
            "CASE WHEN :present THEN COALESCE(custom_banner_revision, 0) + 1 ELSE NULL END " +
            "WHERE component_name = :packageName"
    )
    suspend fun updateCustomBanner(packageName: String, present: Boolean)

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

    @Transaction
    suspend fun setManualOrder(packageNames: List<String>) {
        packageNames.forEachIndexed { index, packageName ->
            setManualOrder(packageName, index.toLong())
        }
    }

    @Transaction
    suspend fun setLastUsedAt(packageName: String, lastUsedAt: Long) {
        insertIfMissing(AppPreferenceEntity(packageName))
        updateLastUsedAt(packageName, lastUsedAt)
    }

    @Transaction
    suspend fun setCustomBanner(packageName: String, present: Boolean) {
        insertIfMissing(AppPreferenceEntity(packageName))
        updateCustomBanner(packageName, present)
    }
}
