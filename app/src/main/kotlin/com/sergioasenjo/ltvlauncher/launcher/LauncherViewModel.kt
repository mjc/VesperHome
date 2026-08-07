package com.sergioasenjo.ltvlauncher.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.applications.ApplicationRepository
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val apps: List<LauncherApp> = emptyList(),
    val loading: Boolean = true,
)

class LauncherViewModel(
    private val applicationRepository: ApplicationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState = _uiState.asStateFlow()

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val apps = applicationRepository.getApplications()
            _uiState.value = LauncherUiState(apps = apps, loading = false)
        }
    }

    fun launch(app: LauncherApp) {
        applicationRepository.launch(app.componentName, app.user)
    }

    companion object {
        fun factory(repository: ApplicationRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { LauncherViewModel(repository) }
            }
    }
}
