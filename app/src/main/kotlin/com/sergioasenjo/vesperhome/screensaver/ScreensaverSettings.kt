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

enum class ScreensaverStartDelay(val milliseconds: Int) {
    MINUTES_5(5 * 60 * 1000),
    MINUTES_10(10 * 60 * 1000),
    MINUTES_15(15 * 60 * 1000),
    MINUTES_30(30 * 60 * 1000)
}

enum class ScreensaverStandbyDelay(val milliseconds: Long?) {
    NEVER(null),
    MINUTES_15(15 * 60 * 1000L),
    MINUTES_30(30 * 60 * 1000L),
    MINUTES_60(60 * 60 * 1000L)
}

data class ScreensaverSettings(
    val clockStyle: ScreensaverClockStyle = ScreensaverClockStyle.MINIMAL,
    val backButtonAction: BackButtonAction = BackButtonAction.NOTHING,
    val startDelay: ScreensaverStartDelay = ScreensaverStartDelay.MINUTES_10,
    val standbyDelay: ScreensaverStandbyDelay = ScreensaverStandbyDelay.MINUTES_30
)

sealed interface ScreensaverSettingsAction {
    data object ChooseStartDelay : ScreensaverSettingsAction

    data object ChooseStandbyDelay : ScreensaverSettingsAction

    data object ChooseClockStyle : ScreensaverSettingsAction

    data object ChooseBackButtonAction : ScreensaverSettingsAction

    data object ChooseDateFormat : ScreensaverSettingsAction

    data object ChooseTimeFormat : ScreensaverSettingsAction

    data object OpenSystemSettings : ScreensaverSettingsAction
}
