package com.sergioasenjo.vesperhome.launcher

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.applications.ManagedApplicationsRepository
import com.sergioasenjo.vesperhome.applications.sortedForDisplay
import com.sergioasenjo.vesperhome.brightness.BrightnessPeriod
import com.sergioasenjo.vesperhome.brightness.BrightnessSettings
import com.sergioasenjo.vesperhome.categories.LauncherCategory
import com.sergioasenjo.vesperhome.categories.LauncherSection
import com.sergioasenjo.vesperhome.categories.LauncherSpacer
import com.sergioasenjo.vesperhome.data.CategoryRepository
import com.sergioasenjo.vesperhome.data.LauncherCategoryDefinition
import com.sergioasenjo.vesperhome.platform.HomeRepository
import com.sergioasenjo.vesperhome.screensaver.BackButtonAction
import com.sergioasenjo.vesperhome.screensaver.ScreensaverClockStyle
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettings
import com.sergioasenjo.vesperhome.screensaver.ScreensaverStandbyDelay
import com.sergioasenjo.vesperhome.screensaver.ScreensaverStartDelay
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherSettingsRepository
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import com.sergioasenjo.vesperhome.status.StatusBarSettings
import com.sergioasenjo.vesperhome.wallpaper.BuiltInWallpaper
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperState
import com.sergioasenjo.vesperhome.wallpaper.WallpaperTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LauncherUiState(
    val favoriteApps: List<LauncherApp> = emptyList(),
    val apps: List<LauncherApp> = emptyList(),
    val sections: List<LauncherSection> = emptyList(),
    val applicationSortMode: ApplicationSortMode = ApplicationSortMode.MANUAL,
    val appearance: LauncherAppearance = LauncherAppearance(),
    val statusBar: StatusBarSettings = StatusBarSettings(),
    val screensaver: ScreensaverSettings = ScreensaverSettings(),
    val brightness: BrightnessSettings = BrightnessSettings(),
    val wallpaper: WallpaperState = WallpaperState(),
    val showComingNext: Boolean = true,
    val showJellyfinMusic: Boolean = true,
    val isDefaultLauncher: Boolean? = null,
    val loading: Boolean = true
) {
    val categories: List<LauncherCategory>
        get() = sections.filterIsInstance<LauncherCategory>()
}

sealed interface LauncherEvent {
    data object LaunchFailed : LauncherEvent
    data object PreferenceUpdateFailed : LauncherEvent
    data object CategoryUpdateFailed : LauncherEvent
    data object WallpaperUpdateFailed : LauncherEvent
    data object CustomBannerUpdateFailed : LauncherEvent
    data class OpenIntent(val intent: Intent) : LauncherEvent
}

class LauncherViewModel(
    private val managedApplicationsRepository: ManagedApplicationsRepository,
    private val categoryRepository: CategoryRepository,
    private val homeRepository: HomeRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository,
    private val wallpaperRepository: WallpaperRepository
) : ViewModel() {
    private val eventsChannel = Channel<LauncherEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val isDefaultLauncher = MutableStateFlow<Boolean?>(null)

    private val appState = combine(
        managedApplicationsRepository.observeApplications(),
        categoryRepository.observeSections(),
        launcherSettingsRepository.settings,
        wallpaperRepository.state
    ) { apps, sectionDefinitions, settings, wallpaper ->
        val appsByKey = apps.associateBy(LauncherApp::packageName)
        val visibleApps = apps.filterNot(LauncherApp::isHidden)
        val applicationSortMode = settings.applicationSortMode
        LauncherUiState(
            favoriteApps = visibleApps.filter(LauncherApp::isFavorite).sortedForDisplay(applicationSortMode),
            apps = visibleApps.sortedForDisplay(applicationSortMode),
            sections = sectionDefinitions.map { definition ->
                when (definition) {
                    is LauncherCategoryDefinition -> definition.toLauncherCategory(appsByKey)
                    is LauncherSpacer -> definition
                    else -> error("Unsupported launcher section")
                }
            },
            applicationSortMode = applicationSortMode,
            appearance = settings.appearance,
            statusBar = settings.statusBar,
            screensaver = settings.screensaver,
            brightness = settings.brightness,
            wallpaper = wallpaper,
            showComingNext = settings.showComingNext,
            showJellyfinMusic = settings.showJellyfinMusic,
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
        if (managedApplicationsRepository.launch(app)) {
            updatePreference { managedApplicationsRepository.recordLaunch(app) }
        } else {
            viewModelScope.launch { eventsChannel.send(LauncherEvent.LaunchFailed) }
        }
    }

    fun openApplicationDetails(app: LauncherApp) {
        eventsChannel.trySend(
            LauncherEvent.OpenIntent(managedApplicationsRepository.createApplicationDetailsIntent(app))
        )
    }

    fun uninstall(app: LauncherApp) {
        eventsChannel.trySend(LauncherEvent.OpenIntent(managedApplicationsRepository.createUninstallIntent(app)))
    }

    fun refreshHomeStatus() {
        isDefaultLauncher.value = homeRepository.isDefaultLauncher()
    }

    fun requestDefaultLauncher() {
        eventsChannel.trySend(LauncherEvent.OpenIntent(homeRepository.createDefaultLauncherIntent()))
    }

    fun openSystemSettings() {
        eventsChannel.trySend(LauncherEvent.OpenIntent(homeRepository.createSystemSettingsIntent()))
    }

    fun toggleFavorite(app: LauncherApp) {
        updatePreference { managedApplicationsRepository.setFavorite(app, !app.isFavorite) }
    }

    fun setHidden(app: LauncherApp, hidden: Boolean) {
        updatePreference { managedApplicationsRepository.setHidden(app, hidden) }
    }

    fun setCustomAppName(app: LauncherApp, customName: String?) {
        updatePreference { managedApplicationsRepository.setCustomName(app, customName) }
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

    fun setApplicationSortMode(sortMode: ApplicationSortMode) {
        updatePreference { launcherSettingsRepository.setApplicationSortMode(sortMode) }
    }

    fun setTheme(theme: LauncherTheme) {
        updatePreference { launcherSettingsRepository.setTheme(theme) }
    }

    fun setShowAppNames(show: Boolean) {
        updatePreference { launcherSettingsRepository.setShowAppNames(show) }
    }

    fun setShowCategoryTitles(show: Boolean) {
        updatePreference { launcherSettingsRepository.setShowCategoryTitles(show) }
    }

    fun setShowFocusOutline(show: Boolean) {
        updatePreference { launcherSettingsRepository.setShowFocusOutline(show) }
    }

    fun setAppCardFocusAnimations(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setAppCardFocusAnimations(enabled) }
    }

    fun setSelectorTransitionAnimations(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setSelectorTransitionAnimations(enabled) }
    }

    fun setKeyClickSounds(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setKeyClickSounds(enabled) }
    }

    fun setStatusBarAutoHide(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarAutoHide(enabled) }
    }

    fun setStatusBarShowDate(show: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarShowDate(show) }
    }

    fun setStatusBarShowTime(show: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarShowTime(show) }
    }

    fun setStatusBarShowNetwork(show: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarShowNetwork(show) }
    }

    fun setStatusBarShowInputs(show: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarShowInputs(show) }
    }

    fun setStatusBarShowNotifications(show: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarShowNotifications(show) }
    }

    fun setStatusBarAutoHideNotificationBell(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setStatusBarAutoHideNotificationBell(enabled) }
    }

    fun setSystemNotificationPopups(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setSystemNotificationPopups(enabled) }
    }

    fun setStatusBarDateFormat(format: String) {
        updatePreference { launcherSettingsRepository.setStatusBarDateFormat(format) }
    }

    fun setStatusBarTimeFormat(format: String) {
        updatePreference { launcherSettingsRepository.setStatusBarTimeFormat(format) }
    }

    fun setScreensaverClockStyle(style: ScreensaverClockStyle) {
        updatePreference { launcherSettingsRepository.setScreensaverClockStyle(style) }
    }

    fun setBackButtonAction(action: BackButtonAction) {
        updatePreference { launcherSettingsRepository.setBackButtonAction(action) }
    }

    fun setScreensaverStartDelay(delay: ScreensaverStartDelay) {
        updatePreference { launcherSettingsRepository.setScreensaverStartDelay(delay) }
    }

    fun setScreensaverStandbyDelay(delay: ScreensaverStandbyDelay) {
        updatePreference { launcherSettingsRepository.setScreensaverStandbyDelay(delay) }
    }

    fun setBrightnessEnabled(enabled: Boolean) {
        updatePreference { launcherSettingsRepository.setBrightnessEnabled(enabled) }
    }

    fun setBrightness(period: BrightnessPeriod, percentage: Int) {
        updatePreference { launcherSettingsRepository.setBrightness(period, percentage) }
    }

    fun setShowComingNext(show: Boolean) {
        updatePreference { launcherSettingsRepository.setShowComingNext(show) }
    }

    fun setShowJellyfinMusic(show: Boolean) {
        updatePreference { launcherSettingsRepository.setShowJellyfinMusic(show) }
    }

    fun setTimeBasedWallpaperEnabled(enabled: Boolean) {
        updateWallpaper { wallpaperRepository.setTimeBasedEnabled(enabled) }
    }

    fun setBuiltInWallpaper(target: WallpaperTarget, wallpaper: BuiltInWallpaper) {
        updateWallpaper { wallpaperRepository.setBuiltIn(target, wallpaper) }
    }

    fun importCustomWallpaper(target: WallpaperTarget, source: Uri) {
        updateWallpaper { wallpaperRepository.importCustom(target, source) }
    }

    fun importCustomBanner(app: LauncherApp, source: Uri) {
        updateData(LauncherEvent.CustomBannerUpdateFailed) {
            managedApplicationsRepository.importCustomBanner(app, source)
        }
    }

    fun removeCustomBanner(app: LauncherApp) {
        updateData(LauncherEvent.CustomBannerUpdateFailed) {
            managedApplicationsRepository.removeCustomBanner(app)
        }
    }

    fun setManualAppOrder(apps: List<LauncherApp>) {
        updatePreference { managedApplicationsRepository.setManualOrder(apps) }
    }

    fun setCategoryAppOrder(categoryId: Long, apps: List<LauncherApp>) {
        updateCategory { categoryRepository.setAppOrder(categoryId, apps) }
    }

    private fun updatePreference(update: suspend () -> Unit) {
        updateData(LauncherEvent.PreferenceUpdateFailed, update)
    }

    private fun updateCategory(update: suspend () -> Unit) {
        updateData(LauncherEvent.CategoryUpdateFailed, update)
    }

    private fun updateWallpaper(update: suspend () -> Unit) {
        updateData(LauncherEvent.WallpaperUpdateFailed, update)
    }

    private fun updateData(failureEvent: LauncherEvent, update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                eventsChannel.send(failureEvent)
            }
        }
    }

    private fun LauncherCategoryDefinition.toLauncherCategory(appsByKey: Map<String, LauncherApp>): LauncherCategory {
        val categoryApps = appKeys.mapNotNull(appsByKey::get).filterNot(LauncherApp::isHidden)
        return LauncherCategory(
            id = id,
            name = name,
            position = position,
            sortMode = sortMode,
            layoutType = layoutType,
            gridColumns = gridColumns,
            rowHeight = rowHeight,
            apps = if (sortMode == ApplicationSortMode.MANUAL) categoryApps else categoryApps.sortedForDisplay(sortMode)
        )
    }

    companion object {
        fun factory(
            managedApplicationsRepository: ManagedApplicationsRepository,
            categoryRepository: CategoryRepository,
            homeRepository: HomeRepository,
            launcherSettingsRepository: LauncherSettingsRepository,
            wallpaperRepository: WallpaperRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    managedApplicationsRepository,
                    categoryRepository,
                    homeRepository,
                    launcherSettingsRepository,
                    wallpaperRepository
                )
            }
        }
    }
}
