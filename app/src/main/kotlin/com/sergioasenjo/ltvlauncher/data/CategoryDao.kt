package com.sergioasenjo.ltvlauncher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category_apps ORDER BY category_id, position")
    fun observeMemberships(): Flow<List<CategoryAppEntity>>

    @Query("SELECT * FROM spacers ORDER BY position")
    fun observeSpacers(): Flow<List<SpacerEntity>>

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE category_id = :categoryId")
    suspend fun renameCategory(categoryId: Long, name: String)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM categories " +
            "WHERE name = :name COLLATE NOCASE AND category_id != :excludedCategoryId)"
    )
    suspend fun categoryNameExists(name: String, excludedCategoryId: Long): Boolean

    @Query("DELETE FROM categories WHERE category_id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: CategoryAppEntity)

    @Query("DELETE FROM category_apps WHERE category_id = :categoryId AND component_name = :packageName")
    suspend fun deleteMembership(categoryId: Long, packageName: String)

    @Query("UPDATE categories SET position = :position WHERE category_id = :categoryId")
    suspend fun updateCategoryPosition(categoryId: Long, position: Long)

    @Query("UPDATE spacers SET position = :position WHERE spacer_id = :spacerId")
    suspend fun updateSpacerPosition(spacerId: Long, position: Long)

    @Query(
        "UPDATE category_apps SET position = :position WHERE category_id = :categoryId AND component_name = :packageName"
    )
    suspend fun updateAppPosition(categoryId: Long, packageName: String, position: Long)

    @Query(
        "UPDATE categories SET sort_mode = :sortMode, layout_type = :layoutType, " +
            "grid_columns = :gridColumns, row_height = :rowHeight WHERE category_id = :categoryId"
    )
    suspend fun updateCategoryConfiguration(
        categoryId: Long,
        sortMode: String,
        layoutType: String,
        gridColumns: Int,
        rowHeight: Int
    )

    @Insert
    suspend fun insertSpacer(spacer: SpacerEntity): Long

    @Query("UPDATE spacers SET height = :height WHERE spacer_id = :spacerId")
    suspend fun updateSpacerHeight(spacerId: Long, height: Int)

    @Query("DELETE FROM spacers WHERE spacer_id = :spacerId")
    suspend fun deleteSpacer(spacerId: Long)

    @Query(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM (" +
            "SELECT position FROM categories UNION ALL SELECT position FROM spacers)"
    )
    suspend fun nextSectionPosition(): Long

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category_apps WHERE category_id = :categoryId")
    suspend fun nextAppPosition(categoryId: Long): Long

    @Transaction
    suspend fun addApp(categoryId: Long, packageName: String) {
        insertMembership(
            CategoryAppEntity(
                categoryId = categoryId,
                componentName = packageName,
                position = nextAppPosition(categoryId)
            )
        )
    }

    @Transaction
    suspend fun setSectionOrder(categoryIds: List<Long>, spacerIds: List<Long>, orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { index, key ->
            val parts = key.split(':', limit = 2)
            val id = parts[1].toLong()
            if (parts[0] == "category" && id in categoryIds) {
                updateCategoryPosition(id, index.toLong())
            } else if (parts[0] == "spacer" && id in spacerIds) {
                updateSpacerPosition(id, index.toLong())
            }
        }
    }

    @Transaction
    suspend fun setAppOrder(categoryId: Long, packageNames: List<String>) {
        packageNames.forEachIndexed { index, packageName ->
            updateAppPosition(categoryId, packageName, index.toLong())
        }
    }
}
