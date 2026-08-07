package com.sergioasenjo.ltvlauncher.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.applications.ManagedApplicationsRepository
import com.sergioasenjo.ltvlauncher.data.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySummary(val id: Long, val name: String, val appCount: Int)

data class CategoryManagementUiState(val categories: List<CategorySummary> = emptyList(), val loading: Boolean = true)

class CategoryManagementViewModel(
    private val categoryRepository: CategoryRepository,
    private val managedApplicationsRepository: ManagedApplicationsRepository
) : ViewModel() {
    private val updateFailures = Channel<Unit>(Channel.BUFFERED)
    val failures = updateFailures.receiveAsFlow()

    val uiState = combine(
        categoryRepository.observeCategories(),
        managedApplicationsRepository.observeApplications()
    ) { categories, apps ->
        val installedPackages = apps.mapTo(mutableSetOf(), LauncherApp::packageName)
        CategoryManagementUiState(
            categories = categories.map { category ->
                CategorySummary(
                    category.id,
                    category.name,
                    category.appKeys.count(installedPackages::contains)
                )
            },
            loading = false
        )
    }
        .stateIn(
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
