package com.sergioasenjo.ltvlauncher.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.applications.ManagedApplicationsRepository
import com.sergioasenjo.ltvlauncher.data.CategoryRepository
import com.sergioasenjo.ltvlauncher.data.LauncherCategoryDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface SectionSummary : LauncherSection

data class CategorySummary(
    val id: Long,
    val name: String,
    val appCount: Int,
    override val position: Long,
    val sortMode: ApplicationSortMode,
    val layoutType: CategoryLayoutType,
    val gridColumns: Int,
    val rowHeight: Int
) : SectionSummary {
    override val stableId: Long = id
    override val kind: LauncherSectionKind = LauncherSectionKind.CATEGORY
}

data class SpacerSummary(val id: Long, val height: Int, override val position: Long) : SectionSummary {
    override val stableId: Long = Long.MIN_VALUE + id
    override val kind: LauncherSectionKind = LauncherSectionKind.SPACER
}

data class CategoryManagementUiState(val sections: List<SectionSummary> = emptyList(), val loading: Boolean = true)

class CategoryManagementViewModel(
    private val categoryRepository: CategoryRepository,
    managedApplicationsRepository: ManagedApplicationsRepository
) : ViewModel() {
    private val updateFailures = Channel<Unit>(Channel.BUFFERED)
    val failures = updateFailures.receiveAsFlow()

    val uiState = combine(
        categoryRepository.observeSections(),
        managedApplicationsRepository.observeApplications()
    ) { sections, apps ->
        val installedPackages = apps.mapTo(mutableSetOf(), LauncherApp::packageName)
        CategoryManagementUiState(
            sections = sections.map { section ->
                when (section) {
                    is LauncherCategoryDefinition -> CategorySummary(
                        id = section.id,
                        name = section.name,
                        appCount = section.appKeys.count(installedPackages::contains),
                        position = section.position,
                        sortMode = section.sortMode,
                        layoutType = section.layoutType,
                        gridColumns = section.gridColumns,
                        rowHeight = section.rowHeight
                    )

                    is LauncherSpacer -> SpacerSummary(section.id, section.height, section.position)

                    else -> error("Unsupported launcher section")
                }
            },
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CategoryManagementUiState()
    )

    fun createCategory(name: String) {
        update { categoryRepository.createCategory(name) }
    }

    fun renameCategory(categoryId: Long, name: String) {
        update { categoryRepository.renameCategory(categoryId, name) }
    }

    fun deleteCategory(categoryId: Long) {
        update { categoryRepository.deleteCategory(categoryId) }
    }

    fun updateCategory(
        category: CategorySummary,
        sortMode: ApplicationSortMode = category.sortMode,
        layoutType: CategoryLayoutType = category.layoutType,
        gridColumns: Int = category.gridColumns,
        rowHeight: Int = category.rowHeight
    ) {
        update {
            categoryRepository.updateCategoryConfiguration(
                category.id,
                sortMode,
                layoutType,
                gridColumns,
                rowHeight
            )
        }
    }

    fun createSpacer(height: Int) {
        update { categoryRepository.createSpacer(height) }
    }

    fun updateSpacer(spacerId: Long, height: Int) {
        update { categoryRepository.updateSpacer(spacerId, height) }
    }

    fun deleteSpacer(spacerId: Long) {
        update { categoryRepository.deleteSpacer(spacerId) }
    }

    fun moveSection(section: SectionSummary, offset: Int) {
        val sections = uiState.value.sections
        val oldIndex = sections.indexOfFirst { it.stableId == section.stableId }
        if (oldIndex < 0) return
        val newIndex = (oldIndex + offset).coerceIn(0, sections.lastIndex)
        if (oldIndex == newIndex) return
        val reordered = sections.toMutableList().apply { add(newIndex, removeAt(oldIndex)) }
        update { categoryRepository.setSectionOrder(reordered) }
    }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateFailures.send(Unit)
            }
        }
    }

    companion object {
        fun factory(
            categoryRepository: CategoryRepository,
            managedApplicationsRepository: ManagedApplicationsRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { CategoryManagementViewModel(categoryRepository, managedApplicationsRepository) }
        }
    }
}
