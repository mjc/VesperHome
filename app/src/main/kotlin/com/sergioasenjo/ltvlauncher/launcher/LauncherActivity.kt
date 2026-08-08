package com.sergioasenjo.ltvlauncher.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.AppActionsController
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.categories.CategoryManagementActivity
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.inputs.TvInputController
import com.sergioasenjo.ltvlauncher.music.JellyfinMusicViewModel
import com.sergioasenjo.ltvlauncher.music.JellyfinSetupActivity
import com.sergioasenjo.ltvlauncher.music.renderJellyfinMusic
import com.sergioasenjo.ltvlauncher.notifications.NotificationController
import com.sergioasenjo.ltvlauncher.screensaver.ScreensaverController
import com.sergioasenjo.ltvlauncher.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.ltvlauncher.settings.AppearanceSettingAction
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsAction
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsPanel
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsPanelPage
import com.sergioasenjo.ltvlauncher.settings.LauncherTheme
import com.sergioasenjo.ltvlauncher.status.StatusBarController
import com.sergioasenjo.ltvlauncher.status.StatusBarSettingsAction
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperRenderer
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperSettingsAction
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperSettingsCoordinator
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherBinding
    private lateinit var contentBinding: ViewLauncherContentBinding
    private lateinit var contentRenderer: LauncherContentRenderer
    private lateinit var wallpaperRenderer: WallpaperRenderer
    private lateinit var wallpaperSettingsCoordinator: WallpaperSettingsCoordinator
    private lateinit var appActionsController: AppActionsController
    private lateinit var statusBarController: StatusBarController
    private lateinit var tvInputController: TvInputController
    private lateinit var notificationController: NotificationController
    private lateinit var screensaverController: ScreensaverController
    private var settingsPanel: LauncherSettingsPanel? = null
    private var settingsPanelPageToRestore: LauncherSettingsPanelPage? = null
    private var launcherInitialized = false
    private var musicInitialized = false
    private var fullyDrawnReported = false
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshHomeStatus()
    }
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(
            container.managedApplicationsRepository,
            container.categoryRepository,
            container.homeRepository,
            container.launcherSettingsRepository,
            container.wallpaperRepository
        )
    }
    private val musicViewModel: JellyfinMusicViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        JellyfinMusicViewModel.factory(
            application as LtvLauncherApplication,
            { container.jellyfinApiRepository },
            container.jellyfinPreferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        wallpaperRenderer = WallpaperRenderer(binding.root, binding.wallpaper)
        wallpaperSettingsCoordinator = WallpaperSettingsCoordinator(
            activity = this,
            currentState = { viewModel.uiState.value.wallpaper },
            onScheduleChanged = viewModel::setTimeBasedWallpaperEnabled,
            onBuiltInSelected = viewModel::setBuiltInWallpaper,
            onCustomSelected = viewModel::importCustomWallpaper,
            onPickerUnavailable = { showMessage(R.string.wallpaper_picker_unavailable) }
        )
        appActionsController = AppActionsController(
            activity = this,
            categories = { viewModel.uiState.value.categories },
            isReorderable = { contentRenderer.isReorderable(it) },
            onFavoriteChanged = viewModel::toggleFavorite,
            onHidden = { viewModel.setHidden(it, true) },
            onApplicationDetails = viewModel::openApplicationDetails,
            onUninstall = viewModel::uninstall,
            onCategoryMembershipChanged = viewModel::setCategoryMembership,
            onCustomBannerSelected = viewModel::importCustomBanner,
            onCustomBannerRemoved = viewModel::removeCustomBanner,
            onPickerUnavailable = { showMessage(R.string.custom_banner_picker_unavailable) }
        )
        val container = (application as LtvLauncherApplication).container
        statusBarController = StatusBarController(
            activity = this,
            binding = binding,
            networkStatusRepository = container.networkStatusRepository,
            currentSettings = { viewModel.uiState.value.statusBar },
            setAutoHide = viewModel::setStatusBarAutoHide,
            setShowDate = viewModel::setStatusBarShowDate,
            setShowTime = viewModel::setStatusBarShowTime,
            setShowNetwork = viewModel::setStatusBarShowNetwork,
            setShowInputs = viewModel::setStatusBarShowInputs,
            setDateFormat = viewModel::setStatusBarDateFormat,
            setTimeFormat = viewModel::setStatusBarTimeFormat
        )
        tvInputController = TvInputController(this, binding, container.tvInputRepository)
        notificationController = NotificationController(
            this,
            binding,
            container.notificationRepository,
            currentSettings = { viewModel.uiState.value.statusBar },
            setShowNotifications = viewModel::setStatusBarShowNotifications,
            setAutoHideBell = viewModel::setStatusBarAutoHideNotificationBell,
            setSystemPopups = viewModel::setSystemNotificationPopups
        )
        screensaverController = ScreensaverController(
            this,
            currentSettings = { viewModel.uiState.value.screensaver },
            setClockStyle = viewModel::setScreensaverClockStyle,
            setBackButtonAction = viewModel::setBackButtonAction,
            chooseDateFormat = {
                statusBarController.handleSettingsAction(StatusBarSettingsAction.ChooseDateFormat)
            },
            chooseTimeFormat = {
                statusBarController.handleSettingsAction(StatusBarSettingsAction.ChooseTimeFormat)
            }
        )
        onBackPressedDispatcher.addCallback(this) { screensaverController.handleBack() }
        settingsPanelPageToRestore = savedInstanceState
            ?.takeIf { it.getBoolean(STATE_SETTINGS_PANEL_OPEN) }
            ?.getString(STATE_SETTINGS_PANEL_PAGE)
            ?.let { storedPage -> LauncherSettingsPanelPage.entries.firstOrNull { it.name == storedPage } }

        binding.openLauncherSettings.setOnClickListener {
            initializeLauncher()
            viewModel.refreshHomeStatus()
            val state = viewModel.uiState.value
            getOrCreateSettingsPanel().show(
                state.isDefaultLauncher,
                state.applicationSortMode,
                state.appearance,
                state.wallpaper,
                state.statusBar,
                state.screensaver
            )
        }
        binding.root.postOnAnimation(::initializeLauncher)
    }

    private fun initializeLauncher() {
        if (launcherInitialized) return
        launcherInitialized = true
        contentBinding = ViewLauncherContentBinding.bind(binding.launcherContentStub.inflate())
        contentBinding.musicPlayPause.setOnClickListener { musicViewModel.playPause() }
        contentBinding.musicNext.setOnClickListener { musicViewModel.next() }
        contentRenderer = LauncherContentRenderer(
            context = this,
            binding = contentBinding,
            emptyStateFocusTarget = binding.openLauncherSettings,
            onAppClick = viewModel::launch,
            onAppLongClick = ::showAppActions,
            onManualOrderChanged = viewModel::setManualAppOrder,
            onCategoryOrderChanged = viewModel::setCategoryAppOrder
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        settingsPanel?.render(
                            state.isDefaultLauncher,
                            state.applicationSortMode,
                            state.appearance,
                            state.wallpaper,
                            state.statusBar,
                            state.screensaver
                        )
                        renderLauncherAppearance(binding, contentBinding, state.appearance)
                        statusBarController.render(state.statusBar, state.appearance)
                        tvInputController.render(state.statusBar.showInputs, state.appearance)
                        notificationController.render(state.statusBar, state.appearance)
                        wallpaperRenderer.render(state.wallpaper)
                        contentRenderer.render(state)
                        if (!state.loading) {
                            settingsPanelPageToRestore?.let { page ->
                                getOrCreateSettingsPanel().show(
                                    state.isDefaultLauncher,
                                    state.applicationSortMode,
                                    state.appearance,
                                    state.wallpaper,
                                    state.statusBar,
                                    state.screensaver,
                                    page
                                )
                                settingsPanelPageToRestore = null
                            }
                        }
                        if (!state.loading && state.isDefaultLauncher == false && currentFocus == null) {
                            binding.openLauncherSettings.post { binding.openLauncherSettings.requestFocus() }
                        }
                        if (!state.loading && !fullyDrawnReported) {
                            fullyDrawnReported = true
                            binding.root.postOnAnimation(::reportFullyDrawnAndInitializeEmojiCompat)
                        }
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.refreshHomeStatus()
        Looper.myQueue().addIdleHandler {
            initializeMusic()
            false
        }
    }

    private fun initializeMusic() {
        if (musicInitialized || isFinishing || isDestroyed) return
        musicInitialized = true
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicViewModel.uiState.collect { contentBinding.renderJellyfinMusic(this@LauncherActivity, it) }
            }
        }
    }

    private fun reportFullyDrawnAndInitializeEmojiCompat() {
        reportFullyDrawn()
        binding.root.postDelayed(::initializeEmojiCompat, EMOJI_INITIALIZATION_DELAY_MS)
    }

    private fun initializeEmojiCompat() {
        if (isFinishing || isDestroyed) return
        val config = DefaultEmojiCompatConfig.create(applicationContext) ?: return
        config.setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
        EmojiCompat.init(config)
        EmojiCompat.get().load()
    }

    override fun onResume() {
        super.onResume()
        if (::notificationController.isInitialized) notificationController.refreshPermissions()
        if (launcherInitialized) viewModel.refreshHomeStatus()
    }

    override fun onStop() {
        if (musicInitialized) musicViewModel.onHostStopped()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        settingsPanel?.takeIf { it.isShowing }?.let { panel ->
            outState.putBoolean(STATE_SETTINGS_PANEL_OPEN, true)
            outState.putString(STATE_SETTINGS_PANEL_PAGE, panel.currentPage.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        settingsPanel?.release()
        statusBarController.release()
        notificationController.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::statusBarController.isInitialized) statusBarController.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    private fun getOrCreateSettingsPanel(): LauncherSettingsPanel = settingsPanel ?: LauncherSettingsPanel(
        context = this,
        onAction = ::handleSettingsAction,
        onAppearanceAction = ::handleAppearanceAction,
        onWallpaperAction = ::handleWallpaperAction,
        onStatusBarAction = ::handleStatusBarAction,
        onScreensaverAction = ::handleScreensaverAction,
        onDismissed = { binding.openLauncherSettings.requestFocus() }
    ).also { settingsPanel = it }

    private fun handleEvent(event: LauncherEvent) {
        when (event) {
            LauncherEvent.LaunchFailed -> showMessage(R.string.launch_failed)
            LauncherEvent.PreferenceUpdateFailed -> showMessage(R.string.preference_update_failed)
            LauncherEvent.CategoryUpdateFailed -> showMessage(R.string.category_update_failed)
            LauncherEvent.WallpaperUpdateFailed -> showMessage(R.string.wallpaper_update_failed)
            LauncherEvent.CustomBannerUpdateFailed -> showMessage(R.string.custom_banner_update_failed)
            is LauncherEvent.OpenIntent -> openIntent(event.intent)
        }
    }

    private fun openIntent(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.settings_open_failed)
        }
    }

    private fun handleSettingsAction(action: LauncherSettingsAction) {
        when (action) {
            LauncherSettingsAction.SET_DEFAULT_HOME -> viewModel.requestDefaultLauncher()

            LauncherSettingsAction.OPEN_SYSTEM_SETTINGS -> viewModel.openSystemSettings()

            LauncherSettingsAction.MANAGE_CATEGORIES ->
                startActivity(Intent(this, CategoryManagementActivity::class.java))

            LauncherSettingsAction.MANAGE_HIDDEN_APPS ->
                startActivity(Intent(this, HiddenAppsActivity::class.java))

            LauncherSettingsAction.SORT_APPLICATIONS -> showSortDialog()

            LauncherSettingsAction.SETUP_JELLYFIN ->
                startActivity(Intent(this, JellyfinSetupActivity::class.java))
        }
    }

    private fun handleAppearanceAction(action: AppearanceSettingAction) {
        val appearance = viewModel.uiState.value.appearance
        when (action) {
            AppearanceSettingAction.THEME -> viewModel.setTheme(
                if (appearance.theme == LauncherTheme.DARK) LauncherTheme.LIGHT else LauncherTheme.DARK
            )

            AppearanceSettingAction.SHOW_APP_NAMES -> viewModel.setShowAppNames(!appearance.showAppNames)

            AppearanceSettingAction.SHOW_CATEGORY_TITLES ->
                viewModel.setShowCategoryTitles(!appearance.showCategoryTitles)

            AppearanceSettingAction.SHOW_FOCUS_OUTLINE ->
                viewModel.setShowFocusOutline(!appearance.showFocusOutline)

            AppearanceSettingAction.APP_CARD_FOCUS_ANIMATIONS ->
                viewModel.setAppCardFocusAnimations(!appearance.appCardFocusAnimations)

            AppearanceSettingAction.SELECTOR_TRANSITION_ANIMATIONS ->
                viewModel.setSelectorTransitionAnimations(!appearance.selectorTransitionAnimations)

            AppearanceSettingAction.KEY_CLICK_SOUNDS -> viewModel.setKeyClickSounds(!appearance.keyClickSounds)
        }
    }

    private fun handleWallpaperAction(action: WallpaperSettingsAction) {
        wallpaperSettingsCoordinator.handle(action)
    }

    private fun handleStatusBarAction(action: StatusBarSettingsAction) {
        if (!notificationController.handleSettingsAction(action)) statusBarController.handleSettingsAction(action)
    }

    private fun handleScreensaverAction(action: ScreensaverSettingsAction) {
        screensaverController.handleSettingsAction(action)
    }

    private fun showSortDialog() {
        showApplicationSortDialog(this, viewModel.uiState.value.applicationSortMode, viewModel::setApplicationSortMode)
    }

    private fun showAppActions(app: LauncherApp, adapter: AppAdapter) {
        appActionsController.show(app, adapter)
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val EMOJI_INITIALIZATION_DELAY_MS = 3_000L
        const val STATE_SETTINGS_PANEL_OPEN = "settings_panel_open"
        const val STATE_SETTINGS_PANEL_PAGE = "settings_panel_page"
    }
}
