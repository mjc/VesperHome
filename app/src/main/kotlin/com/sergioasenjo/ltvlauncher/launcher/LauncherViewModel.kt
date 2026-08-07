package com.sergioasenjo.ltvlauncher.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.applications.ApplicationRepository
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.data.AppPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
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
    val loading: Boolean = true
)

sealed interface LauncherEvent {
    data object LaunchFailed : LauncherEvent
    data object PreferenceUpdateFailed : LauncherEvent
}

class LauncherViewModel(
    private val applicationRepository: ApplicationRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) : ViewModel() {
    private val _events = Channel<LauncherEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        applicationRepository.observeApplications(),
        appPreferencesRepository.observePreferences()
    ) { apps, preferences ->
        apps.map { app ->
            val preference = preferences[app.componentName.flattenToString()]
            app.copy(
                isFavorite = preference?.isFavorite == true,
                isHidden = preference?.isHidden == true,
                manualOrder = preference?.manualOrder
            )
        }
    }
        .map { apps ->
            val visibleApps = apps.filterNot(LauncherApp::isHidden)
            LauncherUiState(
                favoriteApps = visibleApps.filter(LauncherApp::isFavorite).sortedForDisplay(),
                tvApps = visibleApps.filter(LauncherApp::isTvApp).sortedForDisplay(),
                nonTvApps = visibleApps.filterNot(LauncherApp::isTvApp).sortedForDisplay(),
                hiddenApps = apps.filter(LauncherApp::isHidden).sortedForDisplay(),
                loading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LauncherUiState()
        )

    fun launch(app: LauncherApp) {
        if (!applicationRepository.launch(app.componentName, app.user)) {
            viewModelScope.launch { _events.send(LauncherEvent.LaunchFailed) }
        }
    }

    fun toggleFavorite(app: LauncherApp) {
        updatePreference {
            appPreferencesRepository.setFavorite(app, !app.isFavorite)
        }
    }

    fun setHidden(app: LauncherApp, hidden: Boolean) {
        updatePreference {
            appPreferencesRepository.setHidden(app, hidden)
        }
    }

    private fun updatePreference(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(LauncherEvent.PreferenceUpdateFailed)
            }
        }
    }

    private fun List<LauncherApp>.sortedForDisplay(): List<LauncherApp> = sortedWith(
        compareBy<LauncherApp> { it.manualOrder ?: Long.MAX_VALUE }
            .thenBy { it.label.lowercase() }
    )

    companion object {
        fun factory(
            applicationRepository: ApplicationRepository,
            appPreferencesRepository: AppPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(applicationRepository, appPreferencesRepository)
            }
        }
    }
}
