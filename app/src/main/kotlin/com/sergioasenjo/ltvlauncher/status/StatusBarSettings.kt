package com.sergioasenjo.ltvlauncher.status

data class StatusBarSettings(
    val autoHide: Boolean = false,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showNetwork: Boolean = true,
    val showInputs: Boolean = true,
    val showNotifications: Boolean = true,
    val autoHideNotificationBell: Boolean = true,
    val systemNotificationPopups: Boolean = false,
    val dateFormat: String = "EEE, MMM d",
    val timeFormat: String = "HH:mm"
)

sealed interface StatusBarSettingsAction {
    data object ToggleAutoHide : StatusBarSettingsAction

    data object ToggleDate : StatusBarSettingsAction

    data object ToggleTime : StatusBarSettingsAction

    data object ToggleNetwork : StatusBarSettingsAction

    data object ToggleInputs : StatusBarSettingsAction

    data object ToggleNotifications : StatusBarSettingsAction

    data object ToggleAutoHideNotificationBell : StatusBarSettingsAction

    data object ConfigureNotificationAccess : StatusBarSettingsAction

    data object ToggleSystemNotificationPopups : StatusBarSettingsAction

    data object ConfigureOverlayAccess : StatusBarSettingsAction

    data object ChooseDateFormat : StatusBarSettingsAction

    data object ChooseTimeFormat : StatusBarSettingsAction
}
