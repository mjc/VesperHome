package com.sergioasenjo.vesperhome.launcher

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
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.accessibility.HomeButtonFixController
import com.sergioasenjo.vesperhome.applications.AppActionsController
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.brightness.BrightnessController
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.inputs.TvInputController
import com.sergioasenjo.vesperhome.music.JellyfinMusicController
import com.sergioasenjo.vesperhome.music.JellyfinMusicViewModel
import com.sergioasenjo.vesperhome.notifications.NotificationController
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.vesperhome.security.PinController
import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanel
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage
import com.sergioasenjo.vesperhome.status.StatusBarController
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRenderer
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettingsCoordinator
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
    private lateinit var homeButtonFixController: HomeButtonFixController
    private lateinit var brightnessController: BrightnessController
    private lateinit var backupController: BackupController
    private lateinit var aboutController: AboutController
    private lateinit var profileController: ProfileController
    private lateinit var pinController: PinController
    private lateinit var settingsActionController: LauncherSettingsActionController
    private lateinit var musicController: JellyfinMusicController
    private var settingsPanel: LauncherSettingsPanel? = null
    private var settingsPanelPageToRestore: LauncherSettingsPanelPage? = null
    private var launcherInitialized = false
    private var musicInitialized = false
    private var preserveMusicOnStop = false
    private var fullyDrawnReported = false
    private val backActionGuard = LauncherBackActionGuard()
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshHomeStatus()
    }
    private val backupPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && ::backupController.isInitialized) backupController.importBackup(uri)
    }
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        LauncherViewModel.factory(
            container.managedApplicationsRepository,
            container.categoryRepository,
            container.homeRepository,
            container.launcherSettingsRepository,
            container.wallpaperRepository
        )
    }
    private val musicViewModel: JellyfinMusicViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        JellyfinMusicViewModel.factory(
            application as VesperHomeApplication,
            { container.jellyfinApiRepository },
            container.jellyfinPreferencesRepository,
            container.dreamStateTracker
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val container = (application as VesperHomeApplication).container
        homeButtonFixController = HomeButtonFixController(this)
        pinController = PinController(this, lifecycleScope, container.pinRepository)
        brightnessController = BrightnessController(
            activity = this,
            scope = lifecycleScope,
            currentSettings = { viewModel.uiState.value.brightness },
            setEnabled = viewModel::setBrightnessEnabled,
            setBrightness = viewModel::setBrightness,
            onPeriodChanged = {
                val state = viewModel.uiState.value
                settingsPanel?.renderBrightness(
                    state.brightness,
                    brightnessController.hasPermission(),
                    state.appearance
                )
            }
        )
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
            onFavoriteChanged = { app -> protectAppManagement { viewModel.toggleFavorite(app) } },
            onHidden = { app -> protectAppManagement { viewModel.setHidden(app, true) } },
            onNameChanged = { app, name -> protectAppManagement { viewModel.setCustomAppName(app, name) } },
            onApplicationDetails = viewModel::openApplicationDetails,
            onUninstall = { app -> protectAppManagement { viewModel.uninstall(app) } },
            onCategoryMembershipChanged = { category, app, included ->
                protectAppManagement { viewModel.setCategoryMembership(category, app, included) }
            },
            onCustomBannerSelected = { app, uri ->
                protectAppManagement { viewModel.importCustomBanner(app, uri) }
            },
            onCustomBannerRemoved = { app -> protectAppManagement { viewModel.removeCustomBanner(app) } },
            isPinLocked = pinController::isAppLocked,
            onPinLockChanged = pinController::toggleAppLock,
            onReorder = { adapter, app -> protectAppManagement { adapter.startMoving(app) } },
            onPickerUnavailable = { showMessage(R.string.custom_banner_picker_unavailable) }
        )
        statusBarController = StatusBarController(
            activity = this,
            binding = binding,
            networkStatusRepository = container.networkStatusRepository,
            upcomingPreferencesRepository = container.upcomingPreferencesRepository,
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
        backupController = BackupController(
            activity = this,
            scope = lifecycleScope,
            repository = container.backupRepository,
            settingsRepository = container.safetyBackupSettingsRepository,
            currentAppearance = { viewModel.uiState.value.appearance },
            requestImport = { backupPicker.launch("*/*") },
            onRestored = {
                lifecycleScope.launch { container.profileRepository.reload() }
                binding.openLauncherSettings.requestFocus()
            }
        )
        aboutController = AboutController(
            activity = this,
            diagnosticsRepository = container.diagnosticsRepository,
            currentAppearance = { viewModel.uiState.value.appearance },
            onDismissed = { binding.openLauncherSettings.requestFocus() }
        )
        profileController = ProfileController(
            activity = this,
            scope = lifecycleScope,
            repository = container.profileRepository,
            settingsRepository = container.launcherSettingsRepository,
            currentSettings = { viewModel.uiState.value.toProfileSettings() },
            currentAppearance = { viewModel.uiState.value.appearance },
            onDismissed = { binding.openLauncherSettings.requestFocus() }
        )
        settingsActionController = LauncherSettingsActionController(
            this,
            viewModel,
            homeButtonFixController,
            backupController,
            aboutController,
            profileController,
            pinController,
            ::showSortDialog
        )
        screensaverController = createScreensaverController(
            this,
            viewModel,
            statusBarController,
            container.systemScreensaverRepository
        )
        onBackPressedDispatcher.addCallback(this) {
            if (backActionGuard.consumeIntentionalBack()) preserveMusicOnStop = screensaverController.handleBack()
        }
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
                homeButtonFixController.isEnabled(),
                state.applicationSortMode,
                state.appearance,
                state.wallpaper,
                state.statusBar,
                state.screensaver,
                state.brightness,
                brightnessController.hasPermission()
            )
        }
        binding.root.postOnAnimation(::initializeLauncher)
    }

    private fun initializeLauncher() {
        if (launcherInitialized) return
        launcherInitialized = true
        contentBinding = ViewLauncherContentBinding.bind(binding.launcherContentStub.inflate())
        contentRenderer = LauncherContentRenderer(
            context = this,
            binding = contentBinding,
            emptyStateFocusTarget = binding.openLauncherSettings,
            onAppClick = { app -> pinController.authorizeApp(app) { viewModel.launch(app) } },
            onAppLongClick = ::showAppActions,
            onManualOrderChanged = { apps -> protectAppManagement { viewModel.setManualAppOrder(apps) } },
            onCategoryOrderChanged = { categoryId, apps ->
                protectAppManagement { viewModel.setCategoryAppOrder(categoryId, apps) }
            }
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        brightnessController.render(state.brightness)
                        screensaverController.applySystemConfiguration(state.screensaver)
                        settingsPanel?.render(
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
                        if (::musicController.isInitialized) {
                            musicController.setAppearance(homeAppearance)
                            musicController.setSectionVisibility(state.showJellyfinMusic, state.showComingNext)
                        }
                        renderLauncherAppearance(binding, contentBinding, homeAppearance)
                        statusBarController.render(state.statusBar, homeAppearance)
                        tvInputController.render(state.statusBar.showInputs, homeAppearance)
                        notificationController.render(state.statusBar, homeAppearance, state.appearance)
                        backupController.renderAppearance()
                        aboutController.renderAppearance()
                        profileController.render(state.toProfileSettings(), homeAppearance)
                        wallpaperRenderer.render(state.wallpaper, state.appearance.palette)
                        contentRenderer.render(state.copy(appearance = homeAppearance))
                        if (!state.loading) {
                            settingsPanelPageToRestore?.let { page ->
                                getOrCreateSettingsPanel().show(
                                    state.isDefaultLauncher,
                                    homeButtonFixController.isEnabled(),
                                    state.applicationSortMode,
                                    state.appearance,
                                    state.wallpaper,
                                    state.statusBar,
                                    state.screensaver,
                                    state.brightness,
                                    brightnessController.hasPermission(),
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
                    viewModel.events.collect { event ->
                        handleLauncherEvent(this@LauncherActivity, settingsLauncher, event)
                    }
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
        val container = (application as VesperHomeApplication).container
        musicController = JellyfinMusicController(
            this,
            contentBinding,
            musicViewModel,
            container.upcomingRepository,
            container.networkStatusRepository
        ) {
            viewModel.uiState.value.let { it.appearance.forWallpaper(it.wallpaper) }
        }
        viewModel.uiState.value.let { state ->
            musicController.setSectionVisibility(state.showJellyfinMusic, state.showComingNext)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                musicController.collectState()
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
        backActionGuard.onResume()
        musicViewModel.onHostStarted()
        if (::notificationController.isInitialized) notificationController.refreshPermissions()
        if (::brightnessController.isInitialized) {
            brightnessController.onResume()
            val state = viewModel.uiState.value
            settingsPanel?.renderBrightness(state.brightness, brightnessController.hasPermission(), state.appearance)
        }
        if (::aboutController.isInitialized) aboutController.refresh()
        if (launcherInitialized) viewModel.refreshHomeStatus()
    }

    override fun onStop() {
        backActionGuard.onStop()
        if (musicInitialized && !preserveMusicOnStop) musicController.onHostStopped()
        preserveMusicOnStop = false
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
        brightnessController.release()
        backupController.release()
        aboutController.release()
        profileController.release()
        if (::musicController.isInitialized) musicController.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        backActionGuard.onKeyEvent(event, hasWindowFocus())
        if (::statusBarController.isInitialized && statusBarController.onKeyEvent(event)) return true
        if (::musicController.isInitialized && musicController.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun getOrCreateSettingsPanel(): LauncherSettingsPanel = settingsPanel ?: LauncherSettingsPanel(
        context = this,
        onAction = settingsActionController::handle,
        onAppearanceAction = viewModel::handleAppearanceAction,
        onWallpaperAction = ::handleWallpaperAction,
        onStatusBarAction = ::handleStatusBarAction,
        onScreensaverAction = ::handleScreensaverAction,
        onBrightnessAction = brightnessController::handleSettingsAction,
        onDismissed = { binding.openLauncherSettings.requestFocus() }
    ).also { settingsPanel = it }

    private fun handleWallpaperAction(action: WallpaperSettingsAction) = wallpaperSettingsCoordinator.handle(action)

    private fun handleStatusBarAction(action: StatusBarSettingsAction) {
        if (!notificationController.handleSettingsAction(action)) statusBarController.handleSettingsAction(action)
    }

    private fun handleScreensaverAction(action: ScreensaverSettingsAction) =
        screensaverController.handleSettingsAction(action)

    private fun showSortDialog() {
        showApplicationSortDialog(this, viewModel.uiState.value.applicationSortMode, viewModel::setApplicationSortMode)
    }

    private fun protectAppManagement(action: () -> Unit) {
        pinController.authorizeAppManagement(action)
    }

    private fun showAppActions(app: LauncherApp, adapter: AppAdapter) = appActionsController.show(app, adapter)

    private fun showMessage(messageRes: Int) = Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()

    private companion object {
        const val EMOJI_INITIALIZATION_DELAY_MS = 3_000L
        const val STATE_SETTINGS_PANEL_OPEN = "settings_panel_open"
        const val STATE_SETTINGS_PANEL_PAGE = "settings_panel_page"
    }
}

private fun LauncherUiState.toProfileSettings(): LauncherSettings = LauncherSettings(
    applicationSortMode = applicationSortMode,
    appearance = appearance,
    statusBar = statusBar,
    screensaver = screensaver,
    brightness = brightness,
    showComingNext = showComingNext,
    showJellyfinMusic = showJellyfinMusic
)
