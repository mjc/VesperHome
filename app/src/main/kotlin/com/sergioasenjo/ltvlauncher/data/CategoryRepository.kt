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
                    .map { it.componentName.substringBefore('/') }
            )
        }
    }

    suspend fun createCategory(name: String) {
        validateName(name, excludedCategoryId = 0)
        categoryDao.createCategory(name)
    }

    suspend fun renameCategory(categoryId: Long, name: String) {
        validateName(name, excludedCategoryId = categoryId)
        categoryDao.renameCategory(categoryId, name)
    }

    suspend fun deleteCategory(categoryId: Long) {
        categoryDao.deleteCategory(categoryId)
    }

    suspend fun addApp(categoryId: Long, app: LauncherApp) {
        categoryDao.addApp(categoryId, app.packageName)
    }

    suspend fun removeApp(categoryId: Long, app: LauncherApp) {
        categoryDao.deleteMembership(categoryId, app.packageName)
    }

    private suspend fun validateName(name: String, excludedCategoryId: Long) {
        require(name.isNotBlank() && name.lowercase() !in RESERVED_CATEGORY_NAMES)
        require(!categoryDao.categoryNameExists(name, excludedCategoryId))
    }

    private companion object {
        val RESERVED_CATEGORY_NAMES = setOf("favorites", "tv apps", "non-tv apps")
    }
}
