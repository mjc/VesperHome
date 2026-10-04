package com.sergioasenjo.vesperhome.launcher

import android.content.Context
import android.os.Bundle
import com.sergioasenjo.vesperhome.brightness.BrightnessController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.vesperhome.settings.AppearanceSettingAction
import com.sergioasenjo.vesperhome.settings.LauncherSettingsAction
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanel
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettingsAction

internal class LauncherSettingsHost(
    context: Context,
    private val homeButtonFixEnabled: () -> Boolean,
    private val brightnessController: BrightnessController,
    onAction: (LauncherSettingsAction) -> Unit,
    onAppearanceAction: (AppearanceSettingAction) -> Unit,
    onWallpaperAction: (WallpaperSettingsAction) -> Unit,
    onStatusBarAction: (StatusBarSettingsAction) -> Unit,
    onScreensaverAction: (ScreensaverSettingsAction) -> Unit,
    onDismissed: () -> Unit
) {
    private val panelDelegate = lazy {
        LauncherSettingsPanel(
            context = context,
            onAction = onAction,
            onAppearanceAction = onAppearanceAction,
            onWallpaperAction = onWallpaperAction,
            onStatusBarAction = onStatusBarAction,
            onScreensaverAction = onScreensaverAction,
            onBrightnessAction = brightnessController::handleSettingsAction,
            onDismissed = onDismissed
        )
    }

    val panel: LauncherSettingsPanel?
        get() = if (panelDelegate.isInitialized()) panelDelegate.value else null

    fun show(state: LauncherUiState, page: LauncherSettingsPanelPage = LauncherSettingsPanelPage.MAIN) {
        panelDelegate.value.show(
            state.isDefaultLauncher,
            homeButtonFixEnabled(),
            state.applicationSortMode,
            state.appearance,
            state.wallpaper,
            state.statusBar,
            state.screensaver,
            state.brightness,
            brightnessController.hasPermission(),
            page
        )
    }

    fun release() {
        panel?.release()
    }
}

internal fun restoredSettingsPage(savedInstanceState: Bundle?): LauncherSettingsPanelPage? = savedInstanceState
    ?.takeIf { it.getBoolean(STATE_SETTINGS_PANEL_OPEN) }
    ?.getString(STATE_SETTINGS_PANEL_PAGE)
    ?.let { storedPage -> LauncherSettingsPanelPage.entries.firstOrNull { it.name == storedPage } }

internal const val STATE_SETTINGS_PANEL_OPEN = "settings_panel_open"
internal const val STATE_SETTINGS_PANEL_PAGE = "settings_panel_page"
