package com.sergioasenjo.vesperhome.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.brightness.BrightnessSettings
import com.sergioasenjo.vesperhome.databinding.DialogLauncherSettingsBinding
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettings
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.vesperhome.status.StatusBarSettings
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperState

enum class LauncherSettingsAction {
    SET_DEFAULT_HOME,
    OPEN_HOME_BUTTON_FIX,
    BACKUP_AND_RESTORE,
    ABOUT_AND_DIAGNOSTICS,
    OPEN_SYSTEM_SETTINGS,
    MANAGE_CATEGORIES,
    MANAGE_HIDDEN_APPS,
    SORT_APPLICATIONS,
    SETUP_MEDIA_SERVICES
}

enum class LauncherSettingsPanelPage {
    MAIN,
    APPEARANCE,
    WALLPAPER,
    STATUS_BAR,
    SCREENSAVER,
    BRIGHTNESS
}

class LauncherSettingsPanel(
    context: Context,
    private val onAction: (LauncherSettingsAction) -> Unit,
    private val onAppearanceAction: (AppearanceSettingAction) -> Unit,
    private val onWallpaperAction: (WallpaperSettingsAction) -> Unit,
    private val onStatusBarAction: (StatusBarSettingsAction) -> Unit,
    private val onScreensaverAction: (ScreensaverSettingsAction) -> Unit,
    private val onBrightnessAction: (BrightnessSettingsAction) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogLauncherSettingsBinding.inflate(android.view.LayoutInflater.from(context))
    private val appearanceBinder = AppearanceSettingsPanelBinder(binding, onAppearanceAction)
    private val wallpaperBinder = WallpaperSettingsPanelBinder(binding.wallpaperPage, onWallpaperAction)
    private val statusBarBinder = StatusBarSettingsPanelBinder(binding.statusBarPage, onStatusBarAction)
    private val screensaverBinder = ScreensaverSettingsPanelBinder(binding.screensaverPage, onScreensaverAction)
    private val brightnessBinder = BrightnessSettingsPanelBinder(binding.brightnessPage, onBrightnessAction)
    private val dialog = Dialog(context, R.style.Theme_VesperHome_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && !isMainPageVisible()) {
                showMainPage()
                true
            } else {
                false
            }
        }
        setOnShowListener { requestInitialFocus() }
        setOnDismissListener {
            showMainPage(requestFocus = false)
            onDismissed()
        }
    }

    init {
        bindAction(binding.setDefaultLauncher, LauncherSettingsAction.SET_DEFAULT_HOME)
        bindAction(binding.homeButtonFix, LauncherSettingsAction.OPEN_HOME_BUTTON_FIX)
        bindAction(binding.backupAndRestore, LauncherSettingsAction.BACKUP_AND_RESTORE)
        bindAction(binding.aboutAndDiagnostics, LauncherSettingsAction.ABOUT_AND_DIAGNOSTICS)
        bindAction(binding.openSystemSettings, LauncherSettingsAction.OPEN_SYSTEM_SETTINGS)
        bindAction(binding.manageCategories, LauncherSettingsAction.MANAGE_CATEGORIES)
        bindAction(binding.manageHiddenApps, LauncherSettingsAction.MANAGE_HIDDEN_APPS)
        bindAction(binding.sortApplications, LauncherSettingsAction.SORT_APPLICATIONS)
        bindAction(binding.setupMediaServices, LauncherSettingsAction.SETUP_MEDIA_SERVICES)
        binding.openAppearance.setOnClickListener { showAppearancePage() }
        binding.openWallpaper.setOnClickListener { showWallpaperPage() }
        binding.openStatusBar.setOnClickListener { showStatusBarPage() }
        binding.openScreensaver.setOnClickListener { showScreensaverPage() }
        binding.openBrightness.setOnClickListener { showBrightnessPage() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    val currentPage: LauncherSettingsPanelPage
        get() = when {
            isAppearancePageVisible() -> LauncherSettingsPanelPage.APPEARANCE
            isWallpaperPageVisible() -> LauncherSettingsPanelPage.WALLPAPER
            isStatusBarPageVisible() -> LauncherSettingsPanelPage.STATUS_BAR
            isScreensaverPageVisible() -> LauncherSettingsPanelPage.SCREENSAVER
            isBrightnessPageVisible() -> LauncherSettingsPanelPage.BRIGHTNESS
            else -> LauncherSettingsPanelPage.MAIN
        }

    fun show(
        isDefaultLauncher: Boolean?,
        isHomeButtonFixEnabled: Boolean,
        sortMode: ApplicationSortMode,
        appearance: LauncherAppearance,
        wallpaper: WallpaperState,
        statusBar: StatusBarSettings,
        screensaver: ScreensaverSettings,
        brightness: BrightnessSettings,
        hasBrightnessPermission: Boolean,
        page: LauncherSettingsPanelPage = LauncherSettingsPanelPage.MAIN
    ) {
        render(
            isDefaultLauncher,
            isHomeButtonFixEnabled,
            sortMode,
            appearance,
            wallpaper,
            statusBar,
            screensaver,
            brightness,
            hasBrightnessPermission
        )
        when (page) {
            LauncherSettingsPanelPage.MAIN -> showMainPage(requestFocus = false)
            LauncherSettingsPanelPage.APPEARANCE -> showAppearancePage(requestFocus = false)
            LauncherSettingsPanelPage.WALLPAPER -> showWallpaperPage(requestFocus = false)
            LauncherSettingsPanelPage.STATUS_BAR -> showStatusBarPage(requestFocus = false)
            LauncherSettingsPanelPage.SCREENSAVER -> showScreensaverPage(requestFocus = false)
            LauncherSettingsPanelPage.BRIGHTNESS -> showBrightnessPage(requestFocus = false)
        }
        if (!dialog.isShowing) dialog.show()
    }

    fun render(
        isDefaultLauncher: Boolean?,
        isHomeButtonFixEnabled: Boolean,
        sortMode: ApplicationSortMode,
        appearance: LauncherAppearance,
        wallpaper: WallpaperState,
        statusBar: StatusBarSettings,
        screensaver: ScreensaverSettings,
        brightness: BrightnessSettings,
        hasBrightnessPermission: Boolean
    ) {
        binding.homeStatus.setText(
            when (isDefaultLauncher) {
                null -> R.string.home_status_checking
                true -> R.string.home_status_default
                false -> R.string.home_status_not_default
            }
        )
        binding.setDefaultLauncher.visibility = if (isDefaultLauncher == false) View.VISIBLE else View.GONE
        binding.homeButtonFix.text = binding.root.context.getString(
            R.string.home_button_fix_value,
            binding.root.context.getString(if (isHomeButtonFixEnabled) R.string.setting_on else R.string.setting_off)
        )
        binding.sortApplications.text = binding.root.context.getString(
            R.string.sort_applications_value,
            binding.root.context.getString(sortMode.labelRes)
        )
        appearanceBinder.render(appearance)
        wallpaperBinder.render(wallpaper)
        statusBarBinder.render(statusBar)
        screensaverBinder.render(screensaver, statusBar)
        applyThemeColors(appearance)
        brightnessBinder.render(brightness, hasBrightnessPermission, appearance)
        binding.root.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    fun renderBrightness(brightness: BrightnessSettings, hasPermission: Boolean, appearance: LauncherAppearance) {
        brightnessBinder.render(brightness, hasPermission, appearance)
    }

    private fun bindAction(view: View, action: LauncherSettingsAction) {
        view.setOnClickListener {
            dialog.dismiss()
            onAction(action)
        }
    }

    private fun showAppearancePage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.GONE
        binding.appearancePage.visibility = View.VISIBLE
        binding.appearancePage.scrollTo(0, 0)
        if (requestFocus) binding.theme.post { binding.theme.requestFocus() }
    }

    private fun showMainPage(requestFocus: Boolean = true) {
        binding.appearancePage.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.GONE
        binding.mainPage.visibility = View.VISIBLE
        if (requestFocus) binding.openAppearance.post { binding.openAppearance.requestFocus() }
    }

    private fun isAppearancePageVisible(): Boolean = binding.appearancePage.visibility == View.VISIBLE

    private fun showWallpaperPage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.appearancePage.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.VISIBLE
        binding.wallpaperPage.root.scrollTo(0, 0)
        if (requestFocus) {
            binding.wallpaperPage.timeBasedWallpaper.post {
                binding.wallpaperPage.timeBasedWallpaper.requestFocus()
            }
        }
    }

    private fun isWallpaperPageVisible(): Boolean = binding.wallpaperPage.root.visibility == View.VISIBLE

    private fun showStatusBarPage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.appearancePage.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.VISIBLE
        binding.statusBarPage.root.scrollTo(0, 0)
        if (requestFocus) binding.statusBarPage.autoHide.post { binding.statusBarPage.autoHide.requestFocus() }
    }

    private fun isStatusBarPageVisible(): Boolean = binding.statusBarPage.root.visibility == View.VISIBLE

    private fun showScreensaverPage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.appearancePage.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.VISIBLE
        binding.screensaverPage.root.scrollTo(0, 0)
        if (requestFocus) binding.screensaverPage.clockStyle.post { binding.screensaverPage.clockStyle.requestFocus() }
    }

    private fun isScreensaverPageVisible(): Boolean = binding.screensaverPage.root.visibility == View.VISIBLE

    private fun showBrightnessPage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.appearancePage.visibility = View.GONE
        binding.wallpaperPage.root.visibility = View.GONE
        binding.statusBarPage.root.visibility = View.GONE
        binding.screensaverPage.root.visibility = View.GONE
        binding.brightnessPage.root.visibility = View.VISIBLE
        binding.brightnessPage.root.scrollTo(0, 0)
        if (requestFocus) {
            brightnessBinder.initialFocus().let { firstAction -> firstAction.post { firstAction.requestFocus() } }
        }
    }

    private fun isBrightnessPageVisible(): Boolean = binding.brightnessPage.root.visibility == View.VISIBLE

    private fun isMainPageVisible(): Boolean = binding.mainPage.visibility == View.VISIBLE

    private fun applyThemeColors(appearance: LauncherAppearance) {
        val palette = appearance.palette
        val buttonBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val buttonText = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        binding.root.setBackgroundColor(palette.panel)
        binding.homeStatus.background = GradientDrawable().apply {
            cornerRadius = dp(binding.root.context, HOME_STATUS_CORNER_RADIUS_DP).toFloat()
            setColor(palette.surface)
        }
        binding.root.forEachDescendant { view ->
            when (view) {
                is MaterialButton -> {
                    view.backgroundTintList = buttonBackground
                    view.setTextColor(buttonText)
                    view.iconTint = buttonText
                }

                is TextView -> view.setTextColor(
                    when (view.tag) {
                        SECONDARY_TEXT_TAG -> palette.secondaryText
                        ACCENT_TEXT_TAG -> palette.focus
                        else -> palette.primaryText
                    }
                )
            }
        }
    }

    private fun requestInitialFocus() {
        val firstAction = when {
            isAppearancePageVisible() -> binding.theme

            isWallpaperPageVisible() -> binding.wallpaperPage.timeBasedWallpaper

            isStatusBarPageVisible() -> binding.statusBarPage.autoHide

            isScreensaverPageVisible() -> binding.screensaverPage.clockStyle

            isBrightnessPageVisible() -> brightnessBinder.initialFocus()

            else -> binding.setDefaultLauncher.takeIf { it.visibility == View.VISIBLE }
                ?: binding.manageCategories
        }
        firstAction.post { firstAction.requestFocus() }
    }

    private companion object {
        const val PANEL_WIDTH_DP = 360
        const val BACKGROUND_DIM_AMOUNT = 0.62f
        const val HOME_STATUS_CORNER_RADIUS_DP = 8
        const val SECONDARY_TEXT_TAG = "secondary"
        const val ACCENT_TEXT_TAG = "accent"

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
