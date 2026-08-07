package com.sergioasenjo.ltvlauncher.launcher

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.applications.ManagedApplicationsRepository
import com.sergioasenjo.ltvlauncher.categories.LauncherCategory
import com.sergioasenjo.ltvlauncher.data.CategoryRepository
import com.sergioasenjo.ltvlauncher.platform.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LauncherUiState(
    val favoriteApps: List<LauncherApp> = emptyList(),
    val tvApps: List<LauncherApp> = emptyList(),
    val nonTvApps: List<LauncherApp> = emptyList(),
    val hiddenApps: List<LauncherApp> = emptyList(),
    val categories: List<LauncherCategory> = emptyList(),
    val isDefaultLauncher: Boolean? = null,
    val loading: Boolean = true
)

sealed interface LauncherEvent {
    data object LaunchFailed : LauncherEvent
    data object PreferenceUpdateFailed : LauncherEvent
    data object CategoryUpdateFailed : LauncherEvent
    data class OpenIntent(val intent: Intent) : LauncherEvent
}

class LauncherViewModel(
    private val managedApplicationsRepository: ManagedApplicationsRepository,
    private val categoryRepository: CategoryRepository,
    private val homeRepository: HomeRepository
) : ViewModel() {
    private val _events = Channel<LauncherEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val isDefaultLauncher = MutableStateFlow<Boolean?>(null)

    private val appState = combine(
        managedApplicationsRepository.observeApplications(),
        categoryRepository.observeCategories()
    ) { apps, categoryDefinitions ->
        val appsByKey = apps.associateBy(LauncherApp::packageName)
        val visibleApps = apps.filterNot(LauncherApp::isHidden)
        LauncherUiState(
            favoriteApps = visibleApps.filter(LauncherApp::isFavorite).sortedForDisplay(),
            tvApps = visibleApps.filter(LauncherApp::isTvApp).sortedForDisplay(),
            nonTvApps = visibleApps.filterNot(LauncherApp::isTvApp).sortedForDisplay(),
            hiddenApps = apps.filter(LauncherApp::isHidden).sortedForDisplay(),
            categories = categoryDefinitions.map { definition ->
                LauncherCategory(
                    id = definition.id,
                    name = definition.name,
                    apps = definition.appKeys
                        .mapNotNull(appsByKey::get)
                        .filterNot(LauncherApp::isHidden)
                )
            },
            loading = false
        )
    }
    val uiState = combine(appState, isDefaultLauncher) { state, isDefault ->
        state.copy(isDefaultLauncher = isDefault)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LauncherUiState()
    )

    fun launch(app: LauncherApp) {
        if (!managedApplicationsRepository.launch(app)) {
            viewModelScope.launch { _events.send(LauncherEvent.LaunchFailed) }
        }
    }

    fun refreshHomeStatus() {
        isDefaultLauncher.value = homeRepository.isDefaultLauncher()
    }

    fun requestDefaultLauncher() {
        _events.trySend(LauncherEvent.OpenIntent(homeRepository.createDefaultLauncherIntent()))
    }

    fun openSystemSettings() {
        _events.trySend(LauncherEvent.OpenIntent(homeRepository.createSystemSettingsIntent()))
    }

    fun toggleFavorite(app: LauncherApp) {
        updatePreference {
            managedApplicationsRepository.setFavorite(app, !app.isFavorite)
        }
    }

    fun setHidden(app: LauncherApp, hidden: Boolean) {
        updatePreference {
            managedApplicationsRepository.setHidden(app, hidden)
        }
    }

    fun createCategory(name: String) {
        updateCategory { categoryRepository.createCategory(name.trim()) }
    }

    fun renameCategory(categoryId: Long, name: String) {
        updateCategory { categoryRepository.renameCategory(categoryId, name.trim()) }
    }

    fun deleteCategory(categoryId: Long) {
        updateCategory { categoryRepository.deleteCategory(categoryId) }
    }

    fun setCategoryMembership(category: LauncherCategory, app: LauncherApp, included: Boolean) {
        updateCategory {
            if (included) {
                categoryRepository.addApp(category.id, app)
            } else {
                categoryRepository.removeApp(category.id, app)
            }
        }
    }

    private fun updatePreference(update: suspend () -> Unit) {
        updateData(LauncherEvent.PreferenceUpdateFailed, update)
    }

    private fun updateCategory(update: suspend () -> Unit) {
        updateData(LauncherEvent.CategoryUpdateFailed, update)
    }

    private fun updateData(failureEvent: LauncherEvent, update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(failureEvent)
            }
        }
    }

    private fun List<LauncherApp>.sortedForDisplay(): List<LauncherApp> = sortedWith(
        compareBy<LauncherApp> { it.manualOrder ?: Long.MAX_VALUE }
            .thenBy { it.label.lowercase() }
    )

    companion object {
        fun factory(
            managedApplicationsRepository: ManagedApplicationsRepository,
            categoryRepository: CategoryRepository,
            homeRepository: HomeRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    managedApplicationsRepository,
                    categoryRepository,
                    homeRepository
                )
            }
        }
    }
}
