package com.sergioasenjo.ltvlauncher.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HiddenAppsUiState(val apps: List<LauncherApp> = emptyList(), val loading: Boolean = true)

class HiddenAppsViewModel(private val managedApplicationsRepository: ManagedApplicationsRepository) : ViewModel() {
    private val updateFailures = Channel<Unit>(Channel.BUFFERED)
    val failures = updateFailures.receiveAsFlow()

    val uiState = managedApplicationsRepository.observeApplications()
        .map { apps ->
            HiddenAppsUiState(
                apps = apps.filter(LauncherApp::isHidden).sortedBy { it.label.lowercase() },
                loading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HiddenAppsUiState()
        )

    fun restore(app: LauncherApp) {
        viewModelScope.launch {
            try {
                managedApplicationsRepository.setHidden(app, false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateFailures.send(Unit)
            }
        }
    }

    companion object {
        fun factory(repository: ManagedApplicationsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HiddenAppsViewModel(repository) }
        }
    }
}
