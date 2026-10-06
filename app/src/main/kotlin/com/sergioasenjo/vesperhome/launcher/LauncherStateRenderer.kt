package com.sergioasenjo.vesperhome.launcher

import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.accessibility.HomeButtonFixController
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.brightness.BrightnessController
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.inputs.TvInputController
import com.sergioasenjo.vesperhome.livetv.LiveTvController
import com.sergioasenjo.vesperhome.music.JellyfinMusicController
import com.sergioasenjo.vesperhome.notifications.NotificationController
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverController
import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanel
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage
import com.sergioasenjo.vesperhome.status.StatusBarController
import com.sergioasenjo.vesperhome.update.ReleaseUpdateController
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRenderer

internal class LauncherStateRenderer(
    private val activity: AppCompatActivity,
    private val binding: ActivityLauncherBinding,
    private val contentBinding: ViewLauncherContentBinding,
    private val contentRenderer: LauncherContentRenderer,
    private val wallpaperRenderer: WallpaperRenderer,
    private val brightnessController: BrightnessController,
    private val screensaverController: ScreensaverController,
    private val homeButtonFixController: HomeButtonFixController,
    private val statusBarController: StatusBarController,
    private val tvInputController: TvInputController,
    private val notificationController: NotificationController,
    private val backupController: BackupController,
    private val aboutController: AboutController,
    private val profileController: ProfileController,
    private val releaseUpdateController: ReleaseUpdateController,
    private val currentSettingsPanel: () -> LauncherSettingsPanel?,
    private val currentMusicController: () -> JellyfinMusicController?,
    private val currentLiveTvController: () -> LiveTvController?,
    private val showSettingsPanel: (LauncherUiState, LauncherSettingsPanelPage) -> Unit,
    initialPageToRestore: LauncherSettingsPanelPage?
) {
    private var pageToRestore = initialPageToRestore
    private var fullyDrawnReported = false
    private val appearanceRenderer = LauncherAppearanceRenderer(binding, contentBinding)

    fun render(state: LauncherUiState) {
        brightnessController.render(state.brightness)
        screensaverController.applySystemConfiguration(state.screensaver)
        currentSettingsPanel()?.takeIf { it.isShowing }?.render(
            state.isDefaultLauncher,
            homeButtonFixController.isEnabled(),
            state.applicationSortMode,
            state.appearance,
            state.wallpaper,
            state.statusBar,
            state.screensaver,
            state.brightness,
            brightnessController.hasPermission()
        )
        val homeAppearance = state.appearance.forWallpaper(state.wallpaper)
        currentMusicController()?.let { controller ->
            controller.setAppearance(homeAppearance)
            controller.setSectionVisibility(state.showJellyfinMusic, state.showComingNext)
        }
        currentLiveTvController()?.setAppearance(homeAppearance)
        appearanceRenderer.render(homeAppearance)
        statusBarController.render(state.statusBar, homeAppearance)
        tvInputController.render(state.statusBar.showInputs, homeAppearance)
        notificationController.render(state.statusBar, homeAppearance, state.appearance)
        backupController.renderAppearance()
        aboutController.renderAppearance()
        profileController.render(state.toLauncherSettings(), state.appearance)
        releaseUpdateController.renderAppearance()
        wallpaperRenderer.render(state.wallpaper, state.appearance.palette)
        contentRenderer.render(state.copy(appearance = homeAppearance))
        if (!state.loading) {
            pageToRestore?.let { page ->
                showSettingsPanel(state, page)
                pageToRestore = null
            }
        }
        if (!state.loading && state.isDefaultLauncher == false && activity.currentFocus == null) {
            binding.openLauncherSettings.post { binding.openLauncherSettings.requestFocus() }
        }
        if (!state.loading && !fullyDrawnReported) {
            fullyDrawnReported = true
            binding.root.postOnAnimation {
                LauncherEmojiInitializer.reportFullyDrawnAndInitialize(activity, binding.root)
            }
        }
    }
}

internal fun LauncherUiState.toLauncherSettings(): LauncherSettings = LauncherSettings(
    applicationSortMode = applicationSortMode,
    appearance = appearance,
    statusBar = statusBar,
    screensaver = screensaver,
    brightness = brightness,
    showComingNext = showComingNext,
    showJellyfinMusic = showJellyfinMusic
)
