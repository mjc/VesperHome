package com.sergioasenjo.ltvlauncher.data

import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.categories.CategoryLayoutType
import com.sergioasenjo.ltvlauncher.categories.LauncherSection
import com.sergioasenjo.ltvlauncher.categories.LauncherSectionKind
import com.sergioasenjo.ltvlauncher.categories.LauncherSpacer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class LauncherCategoryDefinition(
    val id: Long,
    val name: String,
    override val position: Long,
    val sortMode: ApplicationSortMode,
    val layoutType: CategoryLayoutType,
    val gridColumns: Int,
    val rowHeight: Int,
    val appKeys: List<String>
) : LauncherSection {
    override val stableId: Long = id
    override val kind: LauncherSectionKind = LauncherSectionKind.CATEGORY
}

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun observeSections(): Flow<List<LauncherSection>> = combine(
        categoryDao.observeCategories(),
        categoryDao.observeMemberships(),
        categoryDao.observeSpacers()
    ) { categories, memberships, spacers ->
        val membershipsByCategory = memberships.groupBy(CategoryAppEntity::categoryId)
        val categorySections = categories.map { category ->
            LauncherCategoryDefinition(
                id = category.categoryId,
                name = category.name,
                position = category.position,
                sortMode = category.sortMode.toSortMode(),
                layoutType = category.layoutType.toLayoutType(),
                gridColumns = category.gridColumns.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS),
                rowHeight = category.rowHeight.coerceIn(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT),
                appKeys = membershipsByCategory[category.categoryId]
                    .orEmpty()
                    .map { it.componentName.substringBefore('/') }
            )
        }
        (categorySections + spacers.map { LauncherSpacer(it.spacerId, it.position, it.height) })
            .sortedBy(LauncherSection::position)
    }

    fun observeCategories(): Flow<List<LauncherCategoryDefinition>> = observeSections().map { sections ->
        sections.filterIsInstance<LauncherCategoryDefinition>()
    }

    suspend fun createCategory(name: String) {
        validateName(name, excludedCategoryId = 0)
        categoryDao.insertCategory(CategoryEntity(name = name, position = categoryDao.nextSectionPosition()))
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

    suspend fun setAppOrder(categoryId: Long, apps: List<LauncherApp>) {
        categoryDao.setAppOrder(categoryId, apps.map(LauncherApp::packageName))
    }

    suspend fun updateCategoryConfiguration(
        categoryId: Long,
        sortMode: ApplicationSortMode,
        layoutType: CategoryLayoutType,
        gridColumns: Int,
        rowHeight: Int
    ) {
        require(gridColumns in MIN_GRID_COLUMNS..MAX_GRID_COLUMNS)
        require(rowHeight in MIN_ROW_HEIGHT..MAX_ROW_HEIGHT)
        categoryDao.updateCategoryConfiguration(
            categoryId,
            sortMode.name,
            layoutType.name,
            gridColumns,
            rowHeight
        )
    }

    suspend fun createSpacer(height: Int) {
        require(height in SPACER_HEIGHTS)
        categoryDao.insertSpacer(SpacerEntity(position = categoryDao.nextSectionPosition(), height = height))
    }

    suspend fun updateSpacer(spacerId: Long, height: Int) {
        require(height in SPACER_HEIGHTS)
        categoryDao.updateSpacerHeight(spacerId, height)
    }

    suspend fun deleteSpacer(spacerId: Long) {
        categoryDao.deleteSpacer(spacerId)
    }

    suspend fun setSectionOrder(sections: List<LauncherSection>) {
        val categoryIds = sections.filter { it.kind == LauncherSectionKind.CATEGORY }.map { it.stableId }
        val spacerIds = sections.filter { it.kind == LauncherSectionKind.SPACER }
            .map { it.stableId - Long.MIN_VALUE }
        val keys = sections.map { section ->
            when (section.kind) {
                LauncherSectionKind.CATEGORY -> "category:${section.stableId}"
                LauncherSectionKind.SPACER -> "spacer:${section.stableId - Long.MIN_VALUE}"
            }
        }
        categoryDao.setSectionOrder(categoryIds, spacerIds, keys)
    }

    private suspend fun validateName(name: String, excludedCategoryId: Long) {
        require(name.isNotBlank() && name.lowercase() !in RESERVED_CATEGORY_NAMES)
        require(!categoryDao.categoryNameExists(name, excludedCategoryId))
    }

    private companion object {
        val RESERVED_CATEGORY_NAMES = setOf(
            "favorites",
            "tv apps",
            "non-tv apps",
            "favoritos",
            "aplicaciones de tv",
            "otras aplicaciones"
        )
        const val MIN_GRID_COLUMNS = 5
        const val MAX_GRID_COLUMNS = 10
        const val MIN_ROW_HEIGHT = 80
        const val MAX_ROW_HEIGHT = 150
        val SPACER_HEIGHTS = setOf(10, 20, 30, 40, 50, 75, 100, 150)
    }
}

private fun String.toSortMode(): ApplicationSortMode =
    ApplicationSortMode.entries.firstOrNull { it.name == this } ?: ApplicationSortMode.MANUAL

private fun String.toLayoutType(): CategoryLayoutType =
    CategoryLayoutType.entries.firstOrNull { it.name == this } ?: CategoryLayoutType.ROW
