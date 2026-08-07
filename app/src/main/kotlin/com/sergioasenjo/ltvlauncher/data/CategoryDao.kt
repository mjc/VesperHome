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

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE category_id = :categoryId")
    suspend fun renameCategory(categoryId: Long, name: String)

    @Query("DELETE FROM categories WHERE category_id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembership(membership: CategoryAppEntity)

    @Query("DELETE FROM category_apps WHERE category_id = :categoryId AND component_name = :componentName")
    suspend fun deleteMembership(categoryId: Long, componentName: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextCategoryPosition(): Long

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM category_apps WHERE category_id = :categoryId")
    suspend fun nextAppPosition(categoryId: Long): Long

    @Transaction
    suspend fun createCategory(name: String): Long =
        insertCategory(CategoryEntity(name = name, position = nextCategoryPosition()))

    @Transaction
    suspend fun addApp(categoryId: Long, componentName: String) {
        insertMembership(
            CategoryAppEntity(
                categoryId = categoryId,
                componentName = componentName,
                position = nextAppPosition(categoryId)
            )
        )
    }
}
