package com.sergioasenjo.ltvlauncher.settings

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
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.databinding.DialogLauncherSettingsBinding

enum class LauncherSettingsAction {
    SET_DEFAULT_HOME,
    OPEN_SYSTEM_SETTINGS,
    MANAGE_CATEGORIES,
    MANAGE_HIDDEN_APPS,
    SORT_APPLICATIONS,
    SETUP_JELLYFIN
}

enum class AppearanceSettingAction {
    THEME,
    SHOW_APP_NAMES,
    SHOW_CATEGORY_TITLES,
    SHOW_FOCUS_OUTLINE,
    APP_CARD_FOCUS_ANIMATIONS,
    SELECTOR_TRANSITION_ANIMATIONS,
    KEY_CLICK_SOUNDS
}

enum class LauncherSettingsPanelPage {
    MAIN,
    APPEARANCE
}

class LauncherSettingsPanel(
    context: Context,
    private val onAction: (LauncherSettingsAction) -> Unit,
    private val onAppearanceAction: (AppearanceSettingAction) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogLauncherSettingsBinding.inflate(android.view.LayoutInflater.from(context))
    private val dialog = Dialog(context, R.style.Theme_LtvLauncher_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isAppearancePageVisible()) {
                showMainPage()
                true
            } else {
                false
            }
        }
        setOnShowListener {
            window?.apply {
                setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.START)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
            }
            requestInitialFocus()
        }
        setOnDismissListener {
            showMainPage(requestFocus = false)
            onDismissed()
        }
    }

    init {
        bindAction(binding.setDefaultLauncher, LauncherSettingsAction.SET_DEFAULT_HOME)
        bindAction(binding.openSystemSettings, LauncherSettingsAction.OPEN_SYSTEM_SETTINGS)
        bindAction(binding.manageCategories, LauncherSettingsAction.MANAGE_CATEGORIES)
        bindAction(binding.manageHiddenApps, LauncherSettingsAction.MANAGE_HIDDEN_APPS)
        bindAction(binding.sortApplications, LauncherSettingsAction.SORT_APPLICATIONS)
        bindAction(binding.setupJellyfin, LauncherSettingsAction.SETUP_JELLYFIN)
        binding.openAppearance.setOnClickListener { showAppearancePage() }
        bindAppearanceAction(binding.theme, AppearanceSettingAction.THEME)
        bindAppearanceAction(binding.showAppNames, AppearanceSettingAction.SHOW_APP_NAMES)
        bindAppearanceAction(binding.showCategoryTitles, AppearanceSettingAction.SHOW_CATEGORY_TITLES)
        bindAppearanceAction(binding.showFocusOutline, AppearanceSettingAction.SHOW_FOCUS_OUTLINE)
        bindAppearanceAction(binding.appCardFocusAnimations, AppearanceSettingAction.APP_CARD_FOCUS_ANIMATIONS)
        bindAppearanceAction(
            binding.selectorTransitionAnimations,
            AppearanceSettingAction.SELECTOR_TRANSITION_ANIMATIONS
        )
        bindAppearanceAction(binding.keyClickSounds, AppearanceSettingAction.KEY_CLICK_SOUNDS)
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    val currentPage: LauncherSettingsPanelPage
        get() = if (isAppearancePageVisible()) {
            LauncherSettingsPanelPage.APPEARANCE
        } else {
            LauncherSettingsPanelPage.MAIN
        }

    fun show(
        isDefaultLauncher: Boolean?,
        sortMode: ApplicationSortMode,
        appearance: LauncherAppearance,
        page: LauncherSettingsPanelPage = LauncherSettingsPanelPage.MAIN
    ) {
        render(isDefaultLauncher, sortMode, appearance)
        when (page) {
            LauncherSettingsPanelPage.MAIN -> showMainPage(requestFocus = false)
            LauncherSettingsPanelPage.APPEARANCE -> showAppearancePage(requestFocus = false)
        }
        if (!dialog.isShowing) dialog.show()
    }

    fun render(isDefaultLauncher: Boolean?, sortMode: ApplicationSortMode, appearance: LauncherAppearance) {
        binding.homeStatus.setText(
            when (isDefaultLauncher) {
                null -> R.string.home_status_checking
                true -> R.string.home_status_default
                false -> R.string.home_status_not_default
            }
        )
        binding.setDefaultLauncher.visibility = if (isDefaultLauncher == false) View.VISIBLE else View.GONE
        binding.sortApplications.text = binding.root.context.getString(
            R.string.sort_applications_value,
            binding.root.context.getString(sortMode.labelRes)
        )
        binding.theme.text = binding.root.context.getString(
            R.string.theme_value,
            binding.root.context.getString(appearance.theme.labelRes)
        )
        binding.showAppNames.setBooleanLabel(R.string.show_app_names_value, appearance.showAppNames)
        binding.showCategoryTitles.setBooleanLabel(
            R.string.show_category_titles_value,
            appearance.showCategoryTitles
        )
        binding.showFocusOutline.setBooleanLabel(
            R.string.show_focus_outline_value,
            appearance.showFocusOutline
        )
        binding.appCardFocusAnimations.setBooleanLabel(
            R.string.app_card_focus_animations_value,
            appearance.appCardFocusAnimations
        )
        binding.selectorTransitionAnimations.setBooleanLabel(
            R.string.selector_transition_animations_value,
            appearance.selectorTransitionAnimations
        )
        binding.keyClickSounds.setBooleanLabel(R.string.key_click_sounds_value, appearance.keyClickSounds)
        applyThemeColors(appearance)
        applyFocusColor(appearance.palette.focus)
        binding.root.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun bindAction(view: View, action: LauncherSettingsAction) {
        view.setOnClickListener {
            dialog.dismiss()
            onAction(action)
        }
    }

    private fun bindAppearanceAction(view: View, action: AppearanceSettingAction) {
        view.setOnClickListener { onAppearanceAction(action) }
    }

    private fun showAppearancePage(requestFocus: Boolean = true) {
        binding.mainPage.visibility = View.GONE
        binding.appearancePage.visibility = View.VISIBLE
        binding.appearancePage.scrollTo(0, 0)
        if (requestFocus) binding.theme.post { binding.theme.requestFocus() }
    }

    private fun showMainPage(requestFocus: Boolean = true) {
        binding.appearancePage.visibility = View.GONE
        binding.mainPage.visibility = View.VISIBLE
        if (requestFocus) binding.openAppearance.post { binding.openAppearance.requestFocus() }
    }

    private fun isAppearancePageVisible(): Boolean = binding.appearancePage.visibility == View.VISIBLE

    private fun applyFocusColor(focusColor: Int) {
        val strokeColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(focusColor, Color.TRANSPARENT)
        )
        panelButtons.forEach { button ->
            button.strokeColor = strokeColors
            button.strokeWidth = dp(binding.root.context, FOCUS_STROKE_WIDTH_DP)
        }
    }

    private fun applyThemeColors(appearance: LauncherAppearance) {
        val context = binding.root.context
        val palette = appearance.palette
        val buttonBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        val buttonText = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        binding.root.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                0f,
                0f,
                dp(context, PANEL_CORNER_RADIUS_DP).toFloat(),
                dp(context, PANEL_CORNER_RADIUS_DP).toFloat(),
                dp(context, PANEL_CORNER_RADIUS_DP).toFloat(),
                dp(context, PANEL_CORNER_RADIUS_DP).toFloat(),
                0f,
                0f
            )
            setColor(palette.panel)
            setStroke(dp(context, DEFAULT_STROKE_WIDTH_DP), palette.stroke)
        }
        binding.homeStatus.background = roundedBackground(context, palette.surface, palette.stroke)
        binding.root.forEachDescendant { view ->
            when (view) {
                is MaterialButton -> {
                    view.backgroundTintList = buttonBackground
                    view.setTextColor(buttonText)
                }

                is TextView -> view.setTextColor(
                    if (view.tag == SECONDARY_TEXT_TAG) palette.secondaryText else palette.primaryText
                )
            }
        }
    }

    private fun roundedBackground(context: Context, color: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, CARD_CORNER_RADIUS_DP).toFloat()
            setColor(color)
            setStroke(dp(context, DEFAULT_STROKE_WIDTH_DP), strokeColor)
        }

    private fun MaterialButton.setBooleanLabel(labelRes: Int, enabled: Boolean) {
        text =
            context.getString(labelRes, context.getString(if (enabled) R.string.setting_on else R.string.setting_off))
    }

    private val panelButtons: List<MaterialButton>
        get() = listOf(
            binding.setDefaultLauncher,
            binding.openAppearance,
            binding.manageCategories,
            binding.manageHiddenApps,
            binding.sortApplications,
            binding.setupJellyfin,
            binding.openSystemSettings,
            binding.theme,
            binding.showAppNames,
            binding.showCategoryTitles,
            binding.showFocusOutline,
            binding.appCardFocusAnimations,
            binding.selectorTransitionAnimations,
            binding.keyClickSounds
        )

    private fun requestInitialFocus() {
        val firstAction = if (isAppearancePageVisible()) {
            binding.theme
        } else {
            binding.setDefaultLauncher.takeIf { it.visibility == View.VISIBLE }
                ?: binding.manageCategories
        }
        firstAction.post { firstAction.requestFocus() }
    }

    private companion object {
        const val PANEL_WIDTH_DP = 420
        const val BACKGROUND_DIM_AMOUNT = 0.62f
        const val FOCUS_STROKE_WIDTH_DP = 2
        const val DEFAULT_STROKE_WIDTH_DP = 1
        const val CARD_CORNER_RADIUS_DP = 12
        const val PANEL_CORNER_RADIUS_DP = 24
        const val SECONDARY_TEXT_TAG = "secondary"

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}

private fun View.forEachDescendant(action: (View) -> Unit) {
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).let { child ->
                action(child)
                child.forEachDescendant(action)
            }
        }
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

private val LauncherTheme.labelRes: Int
    get() = when (this) {
        LauncherTheme.DARK -> R.string.theme_dark
        LauncherTheme.LIGHT -> R.string.theme_light
    }
