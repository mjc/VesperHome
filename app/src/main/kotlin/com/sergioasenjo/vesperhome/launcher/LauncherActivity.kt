package com.sergioasenjo.vesperhome.launcher

import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.accessibility.HomeButtonFixController
import com.sergioasenjo.vesperhome.applications.AppActionsController
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.brightness.BrightnessController
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.inputs.TvInputController
import com.sergioasenjo.vesperhome.livetv.LiveTvController
import com.sergioasenjo.vesperhome.music.JellyfinMusicController
import com.sergioasenjo.vesperhome.music.JellyfinMusicViewModel
import com.sergioasenjo.vesperhome.notifications.NotificationController
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverController
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.vesperhome.security.PinController
import com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage
import com.sergioasenjo.vesperhome.status.StatusBarController
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction
import com.sergioasenjo.vesperhome.update.ReleaseUpdateController
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
    private lateinit var releaseUpdateController: ReleaseUpdateController
    private lateinit var settingsActionController: LauncherSettingsActionController
    private lateinit var stateRenderer: LauncherStateRenderer
    private lateinit var musicController: JellyfinMusicController
    private lateinit var liveTvController: LiveTvController
    private lateinit var settingsHost: LauncherSettingsHost
    private var settingsPanelPageToRestore: LauncherSettingsPanelPage? = null
    private var launcherInitialized = false
    private var musicInitialized = false
    private var preserveMusicOnStop = false
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
        launcherViewModelFactory(application as VesperHomeApplication)
    }
    private val musicViewModel: JellyfinMusicViewModel by viewModels {
        jellyfinMusicViewModelFactory(application as VesperHomeApplication)
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
                if (::settingsHost.isInitialized) {
                    settingsHost.panel.renderBrightness(
                        state.brightness,
                        brightnessController.hasPermission(),
                        state.appearance
                    )
                }
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
        liveTvController = LiveTvController(
            this,
            binding,
            container.liveTvPluginRepository,
            container.liveTvPreferencesRepository,
            { viewModel.uiState.value.appearance.forWallpaper(viewModel.uiState.value.wallpaper) },
            { if (::musicController.isInitialized) musicController.onHostStopped() }
        ).also(LiveTvController::start)
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
            currentSettings = { viewModel.uiState.value.toLauncherSettings() },
            currentAppearance = { viewModel.uiState.value.appearance },
            onDismissed = { binding.openLauncherSettings.requestFocus() }
        )
        releaseUpdateController = ReleaseUpdateController(
            activity = this,
            scope = lifecycleScope,
            repository = container.releaseUpdateRepository,
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
            releaseUpdateController,
            ::showSortDialog
        )
        settingsHost = LauncherSettingsHost(
            context = this,
            homeButtonFixEnabled = homeButtonFixController::isEnabled,
            brightnessController = brightnessController,
            onAction = settingsActionController::handle,
            onAppearanceAction = viewModel::handleAppearanceAction,
            onWallpaperAction = ::handleWallpaperAction,
            onStatusBarAction = ::handleStatusBarAction,
            onScreensaverAction = ::handleScreensaverAction,
            onDismissed = { binding.openLauncherSettings.requestFocus() }
        )
        screensaverController = createScreensaverController(
            this,
            viewModel,
            statusBarController,
            container.systemScreensaverRepository
        )
        onBackPressedDispatcher.addCallback(this) {
            if (liveTvController.handleBack()) return@addCallback
            if (backActionGuard.consumeIntentionalBack()) preserveMusicOnStop = screensaverController.handleBack()
        }
        settingsPanelPageToRestore = restoredSettingsPage(savedInstanceState)

        binding.openLauncherSettings.setOnClickListener {
            initializeLauncher()
            viewModel.refreshHomeStatus()
            val state = viewModel.uiState.value
            settingsHost.show(state)
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
            onAppLongClick = appActionsController::show,
            onManualOrderChanged = { apps -> protectAppManagement { viewModel.setManualAppOrder(apps) } },
            onCategoryOrderChanged = { categoryId, apps ->
                protectAppManagement { viewModel.setCategoryAppOrder(categoryId, apps) }
            }
        )
        stateRenderer = LauncherStateRenderer(
            activity = this,
            binding = binding,
            contentBinding = contentBinding,
            contentRenderer = contentRenderer,
            wallpaperRenderer = wallpaperRenderer,
            brightnessController = brightnessController,
            screensaverController = screensaverController,
            homeButtonFixController = homeButtonFixController,
            statusBarController = statusBarController,
            tvInputController = tvInputController,
            notificationController = notificationController,
            backupController = backupController,
            aboutController = aboutController,
            profileController = profileController,
            releaseUpdateController = releaseUpdateController,
            currentSettingsPanel = { settingsHost.panel },
            currentMusicController = { if (::musicController.isInitialized) musicController else null },
            currentLiveTvController = { liveTvController },
            showSettingsPanel = settingsHost::show,
            initialPageToRestore = settingsPanelPageToRestore
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(stateRenderer::render)
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

    override fun onResume() {
        super.onResume()
        (application as VesperHomeApplication).sizedArtworkCache.setForeground(hasWindowFocus())
        backActionGuard.onResume()
        musicViewModel.onHostStarted()
        if (::notificationController.isInitialized) notificationController.refreshPermissions()
        if (::brightnessController.isInitialized) {
            brightnessController.onResume()
            val state = viewModel.uiState.value
            settingsHost.panel.renderBrightness(
                state.brightness,
                brightnessController.hasPermission(),
                state.appearance
            )
        }
        if (::aboutController.isInitialized) aboutController.refresh()
        if (launcherInitialized) viewModel.refreshHomeStatus()
        liveTvController.refresh()
    }

    override fun onPause() {
        (application as VesperHomeApplication).sizedArtworkCache.setForeground(false)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        (application as VesperHomeApplication).sizedArtworkCache.setForeground(
            hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        (application as VesperHomeApplication).sizedArtworkCache.onInput()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        (application as VesperHomeApplication).sizedArtworkCache.onInput()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onStop() {
        backActionGuard.onStop()
        if (musicInitialized && !preserveMusicOnStop) musicController.onHostStopped()
        liveTvController.onHostStopped()
        preserveMusicOnStop = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        settingsHost.panel.takeIf { it.isShowing }?.let { panel ->
            outState.putBoolean(STATE_SETTINGS_PANEL_OPEN, true)
            outState.putString(STATE_SETTINGS_PANEL_PAGE, panel.currentPage.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        settingsHost.release()
        statusBarController.release()
        notificationController.release()
        brightnessController.release()
        backupController.release()
        aboutController.release()
        profileController.release()
        releaseUpdateController.release()
        if (::musicController.isInitialized) musicController.release()
        liveTvController.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        (application as VesperHomeApplication).sizedArtworkCache.onInput()
        backActionGuard.onKeyEvent(event, hasWindowFocus())
        if (liveTvController.onKeyEvent(event)) return true
        if (::statusBarController.isInitialized && statusBarController.onKeyEvent(event)) return true
        if (::musicController.isInitialized && musicController.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

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
}
