package com.sergioasenjo.vesperhome.settings

import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogLauncherSettingsBinding

enum class AppearanceSettingAction {
    THEME,
    SHOW_APP_NAMES,
    SHOW_CATEGORY_TITLES,
    SHOW_FOCUS_OUTLINE,
    APP_CARD_FOCUS_ANIMATIONS,
    SELECTOR_TRANSITION_ANIMATIONS,
    KEY_CLICK_SOUNDS
}

class AppearanceSettingsPanelBinder(
    private val binding: DialogLauncherSettingsBinding,
    private val onAction: (AppearanceSettingAction) -> Unit
) {
    val buttons: List<MaterialButton> = listOf(
        binding.theme,
        binding.showAppNames,
        binding.showCategoryTitles,
        binding.showFocusOutline,
        binding.appCardFocusAnimations,
        binding.selectorTransitionAnimations,
        binding.keyClickSounds
    )

    init {
        binding.theme.setOnClickListener { onAction(AppearanceSettingAction.THEME) }
        binding.showAppNames.setOnClickListener { onAction(AppearanceSettingAction.SHOW_APP_NAMES) }
        binding.showCategoryTitles.setOnClickListener { onAction(AppearanceSettingAction.SHOW_CATEGORY_TITLES) }
        binding.showFocusOutline.setOnClickListener { onAction(AppearanceSettingAction.SHOW_FOCUS_OUTLINE) }
        binding.appCardFocusAnimations.setOnClickListener {
            onAction(AppearanceSettingAction.APP_CARD_FOCUS_ANIMATIONS)
        }
        binding.selectorTransitionAnimations.setOnClickListener {
            onAction(AppearanceSettingAction.SELECTOR_TRANSITION_ANIMATIONS)
        }
        binding.keyClickSounds.setOnClickListener { onAction(AppearanceSettingAction.KEY_CLICK_SOUNDS) }
    }

    fun render(appearance: LauncherAppearance) {
        binding.theme.text = binding.root.context.getString(
            R.string.theme_value,
            binding.root.context.getString(appearance.theme.labelRes)
        )
        binding.showAppNames.setBooleanLabel(R.string.show_app_names_value, appearance.showAppNames)
        binding.showCategoryTitles.setBooleanLabel(
            R.string.show_category_titles_value,
            appearance.showCategoryTitles
        )
        binding.showFocusOutline.setBooleanLabel(R.string.show_focus_outline_value, appearance.showFocusOutline)
        binding.appCardFocusAnimations.setBooleanLabel(
            R.string.app_card_focus_animations_value,
            appearance.appCardFocusAnimations
        )
        binding.selectorTransitionAnimations.setBooleanLabel(
            R.string.selector_transition_animations_value,
            appearance.selectorTransitionAnimations
        )
        binding.keyClickSounds.setBooleanLabel(R.string.key_click_sounds_value, appearance.keyClickSounds)
    }

    private fun MaterialButton.setBooleanLabel(labelRes: Int, enabled: Boolean) {
        text =
            context.getString(labelRes, context.getString(if (enabled) R.string.setting_on else R.string.setting_off))
    }
}
