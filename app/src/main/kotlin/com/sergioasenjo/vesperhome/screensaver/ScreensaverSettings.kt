package com.sergioasenjo.vesperhome.screensaver

enum class ScreensaverClockStyle {
    MINIMAL,
    BOLD
}

enum class BackButtonAction {
    NOTHING,
    CLOCK,
    SCREENSAVER
}

data class ScreensaverSettings(
    val clockStyle: ScreensaverClockStyle = ScreensaverClockStyle.MINIMAL,
    val backButtonAction: BackButtonAction = BackButtonAction.NOTHING
)

sealed interface ScreensaverSettingsAction {
    data object ChooseClockStyle : ScreensaverSettingsAction

    data object ChooseBackButtonAction : ScreensaverSettingsAction

    data object ChooseDateFormat : ScreensaverSettingsAction

    data object ChooseTimeFormat : ScreensaverSettingsAction

    data object OpenSystemSettings : ScreensaverSettingsAction
}
