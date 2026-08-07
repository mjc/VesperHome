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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryMembershipItem(val app: LauncherApp, val included: Boolean)

data class CategoryMembershipUiState(val apps: List<CategoryMembershipItem> = emptyList(), val loading: Boolean = true)

class CategoryMembershipViewModel(
    private val categoryId: Long,
    private val categoryRepository: CategoryRepository,
    managedApplicationsRepository: ManagedApplicationsRepository
) : ViewModel() {
    private val updateFailures = Channel<Unit>(Channel.BUFFERED)
    val failures = updateFailures.receiveAsFlow()

    val uiState = combine(
        categoryRepository.observeCategories(),
        managedApplicationsRepository.observeApplications()
    ) { categories, apps ->
        val categoryApps = categories.firstOrNull { it.id == categoryId }?.appKeys.orEmpty().toSet()
        CategoryMembershipUiState(
            apps = apps.sortedBy { it.label.lowercase() }.map { app ->
                CategoryMembershipItem(app, app.packageName in categoryApps)
            },
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CategoryMembershipUiState()
    )

    fun toggle(item: CategoryMembershipItem) {
        viewModelScope.launch {
            try {
                if (item.included) {
                    categoryRepository.removeApp(categoryId, item.app)
                } else {
                    categoryRepository.addApp(categoryId, item.app)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateFailures.send(Unit)
            }
        }
    }

    companion object {
        fun factory(
            categoryId: Long,
            categoryRepository: CategoryRepository,
            managedApplicationsRepository: ManagedApplicationsRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CategoryMembershipViewModel(categoryId, categoryRepository, managedApplicationsRepository)
            }
        }
    }
}
