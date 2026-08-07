package com.sergioasenjo.ltvlauncher.data

import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LauncherCategoryDefinition(val id: Long, val name: String, val position: Long, val appKeys: List<String>)

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun observeCategories(): Flow<List<LauncherCategoryDefinition>> = combine(
        categoryDao.observeCategories(),
        categoryDao.observeMemberships()
    ) { categories, memberships ->
        val membershipsByCategory = memberships.groupBy(CategoryAppEntity::categoryId)
        categories.map { category ->
            LauncherCategoryDefinition(
                id = category.categoryId,
                name = category.name,
                position = category.position,
                appKeys = membershipsByCategory[category.categoryId]
                    .orEmpty()
                    .map(CategoryAppEntity::componentName)
            )
        }
    }

    suspend fun createCategory(name: String) {
        categoryDao.createCategory(name)
    }

    suspend fun renameCategory(categoryId: Long, name: String) {
        categoryDao.renameCategory(categoryId, name)
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteCategory(categoryId)
    }

    suspend fun addApp(categoryId: Long, app: LauncherApp) {
        categoryDao.addApp(categoryId, app.preferenceKey)
    }

    suspend fun removeApp(categoryId: Long, app: LauncherApp) {
        categoryDao.deleteMembership(categoryId, app.preferenceKey)
    }

    private val LauncherApp.preferenceKey: String
        get() = componentName.flattenToString()
}
