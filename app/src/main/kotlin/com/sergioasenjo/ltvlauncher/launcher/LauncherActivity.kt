package com.sergioasenjo.ltvlauncher.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.categories.CategoryManagementActivity
import com.sergioasenjo.ltvlauncher.categories.LauncherCategory
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.music.JellyfinMusicViewModel
import com.sergioasenjo.ltvlauncher.music.JellyfinSetupActivity
import com.sergioasenjo.ltvlauncher.music.renderJellyfinMusic
import com.sergioasenjo.ltvlauncher.settings.AppearanceSettingAction
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsAction
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsPanel
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsPanelPage
import com.sergioasenjo.ltvlauncher.settings.LauncherTheme
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherBinding
    private lateinit var contentBinding: ViewLauncherContentBinding
    private lateinit var contentRenderer: LauncherContentRenderer
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
            container.launcherSettingsRepository
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
        settingsPanelPageToRestore = savedInstanceState
            ?.takeIf { it.getBoolean(STATE_SETTINGS_PANEL_OPEN) }
            ?.getString(STATE_SETTINGS_PANEL_PAGE)
            ?.let { storedPage -> LauncherSettingsPanelPage.entries.firstOrNull { it.name == storedPage } }

        binding.openLauncherSettings.setOnClickListener {
            initializeLauncher()
            viewModel.refreshHomeStatus()
            val state = viewModel.uiState.value
            getOrCreateSettingsPanel().show(state.isDefaultLauncher, state.applicationSortMode, state.appearance)
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
                        settingsPanel?.render(state.isDefaultLauncher, state.applicationSortMode, state.appearance)
                        renderAppearance(state.appearance)
                        contentRenderer.render(state)
                        if (!state.loading) {
                            settingsPanelPageToRestore?.let { page ->
                                getOrCreateSettingsPanel().show(
                                    state.isDefaultLauncher,
                                    state.applicationSortMode,
                                    state.appearance,
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
        super.onDestroy()
    }

    private fun getOrCreateSettingsPanel(): LauncherSettingsPanel = settingsPanel ?: LauncherSettingsPanel(
        context = this,
        onAction = ::handleSettingsAction,
        onAppearanceAction = ::handleAppearanceAction,
        onDismissed = { binding.openLauncherSettings.requestFocus() }
    ).also { settingsPanel = it }

    private fun handleEvent(event: LauncherEvent) {
        when (event) {
            LauncherEvent.LaunchFailed -> showMessage(R.string.launch_failed)
            LauncherEvent.PreferenceUpdateFailed -> showMessage(R.string.preference_update_failed)
            LauncherEvent.CategoryUpdateFailed -> showMessage(R.string.category_update_failed)
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

    private fun renderAppearance(appearance: LauncherAppearance) {
        val palette = appearance.palette
        binding.root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.backgroundStart, palette.backgroundEnd)
        )
        contentBinding.jellyfinPanel.background = roundedBackground(palette.surface, palette.stroke)
        binding.title.setTextColor(palette.primaryText)
        contentBinding.musicTitle.setTextColor(palette.primaryText)
        contentBinding.musicArtist.setTextColor(palette.secondaryText)
        val strokeColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focus, Color.TRANSPARENT)
        )
        val buttonBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        val buttonText = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        listOf(
            binding.openLauncherSettings,
            contentBinding.musicPlayPause,
            contentBinding.musicNext
        ).forEach { button ->
            button.backgroundTintList = buttonBackground
            button.setTextColor(buttonText)
            button.strokeColor = strokeColors
            button.strokeWidth = dp(2)
        }
        contentBinding.musicLoading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
        binding.root.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
    }

    private fun roundedBackground(color: Int, strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(color)
        setStroke(dp(1), strokeColor)
    }

    private fun showSortDialog() {
        val modes = ApplicationSortMode.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_applications)
            .setSingleChoiceItems(
                modes.map { getString(it.labelRes) }.toTypedArray(),
                modes.indexOf(viewModel.uiState.value.applicationSortMode)
            ) { dialog, selection ->
                viewModel.setApplicationSortMode(modes[selection])
                dialog.dismiss()
            }
            .show()
    }

    private fun showAppActions(app: LauncherApp, adapter: AppAdapter) {
        val favoriteAction = if (app.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
        val actions = mutableListOf(
            getString(favoriteAction) to { viewModel.toggleFavorite(app) },
            getString(R.string.hide_app) to { viewModel.setHidden(app, true) },
            getString(R.string.application_info) to { viewModel.openApplicationDetails(app) },
            getString(R.string.uninstall_application) to { viewModel.uninstall(app) }
        )
        if (contentRenderer.isReorderable(adapter)) {
            actions += getString(R.string.reorder_application) to { adapter.startMoving(app) }
        }
        viewModel.uiState.value.categories.forEach { category ->
            val included = category.apps.any { it.packageName == app.packageName }
            val label = if (included) {
                getString(R.string.remove_from_category, category.name)
            } else {
                getString(R.string.add_to_category, category.name)
            }
            actions += label to { viewModel.setCategoryMembership(category, app, included = !included) }
        }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, action -> actions[action].second() }
            .show()
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val EMOJI_INITIALIZATION_DELAY_MS = 3_000L
        const val STATE_SETTINGS_PANEL_OPEN = "settings_panel_open"
        const val STATE_SETTINGS_PANEL_PAGE = "settings_panel_page"
    }
}

private fun View.setSoundEffectsEnabledRecursively(enabled: Boolean) {
    isSoundEffectsEnabled = enabled
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).setSoundEffectsEnabledRecursively(enabled)
    }
}

private val ApplicationSortMode.labelRes: Int
    get() = when (this) {
        ApplicationSortMode.MANUAL -> R.string.sort_manual
        ApplicationSortMode.ALPHABETICAL -> R.string.sort_alphabetical
        ApplicationSortMode.LAST_USED -> R.string.sort_last_used
    }
