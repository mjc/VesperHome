package com.sergioasenjo.vesperhome.launcher

import com.sergioasenjo.vesperhome.settings.AppearanceSettingAction
import com.sergioasenjo.vesperhome.settings.LauncherTheme

fun LauncherViewModel.handleAppearanceAction(action: AppearanceSettingAction) {
    val appearance = uiState.value.appearance
    when (action) {
        AppearanceSettingAction.THEME -> setTheme(
            if (appearance.theme == LauncherTheme.DARK) LauncherTheme.LIGHT else LauncherTheme.DARK
        )

        AppearanceSettingAction.SHOW_APP_NAMES -> setShowAppNames(!appearance.showAppNames)

        AppearanceSettingAction.SHOW_CATEGORY_TITLES -> setShowCategoryTitles(!appearance.showCategoryTitles)

        AppearanceSettingAction.SHOW_FOCUS_OUTLINE -> setShowFocusOutline(!appearance.showFocusOutline)

        AppearanceSettingAction.APP_CARD_FOCUS_ANIMATIONS ->
            setAppCardFocusAnimations(!appearance.appCardFocusAnimations)

        AppearanceSettingAction.SELECTOR_TRANSITION_ANIMATIONS ->
            setSelectorTransitionAnimations(!appearance.selectorTransitionAnimations)

        AppearanceSettingAction.KEY_CLICK_SOUNDS -> setKeyClickSounds(!appearance.keyClickSounds)
    }
}
