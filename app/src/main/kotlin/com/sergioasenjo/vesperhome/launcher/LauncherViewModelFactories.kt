package com.sergioasenjo.vesperhome.launcher

import androidx.lifecycle.ViewModelProvider
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.music.MusicViewModel

internal fun launcherViewModelFactory(application: VesperHomeApplication): ViewModelProvider.Factory {
    val container = application.container
    return LauncherViewModel.factory(
        container.managedApplicationsRepository,
        container.categoryRepository,
        container.homeRepository,
        container.launcherSettingsRepository,
        container.wallpaperRepository
    )
}

internal fun musicViewModelFactory(application: VesperHomeApplication): ViewModelProvider.Factory {
    val container = application.container
    return MusicViewModel.factory(
        application,
        { container.musicApiRepository },
        container.musicPreferencesRepository,
        container.dreamStateTracker
    )
}
