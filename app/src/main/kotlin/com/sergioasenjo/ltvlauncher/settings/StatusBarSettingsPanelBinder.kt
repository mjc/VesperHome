package com.sergioasenjo.ltvlauncher.settings

import com.google.android.material.button.MaterialButton
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ViewStatusBarSettingsBinding
import com.sergioasenjo.ltvlauncher.status.StatusBarSettings
import com.sergioasenjo.ltvlauncher.status.StatusBarSettingsAction

class StatusBarSettingsPanelBinder(
    private val binding: ViewStatusBarSettingsBinding,
    private val onAction: (StatusBarSettingsAction) -> Unit
) {
    val buttons: List<MaterialButton> = listOf(
        binding.autoHide,
        binding.showDate,
        binding.dateFormat,
        binding.showTime,
        binding.timeFormat,
        binding.showNetwork,
        binding.showInputs,
        binding.showNotifications,
        binding.autoHideNotificationBell,
        binding.notificationAccess,
        binding.systemNotificationPopups,
        binding.overlayAccess
    )

    init {
        binding.autoHide.setOnClickListener { onAction(StatusBarSettingsAction.ToggleAutoHide) }
        binding.showDate.setOnClickListener { onAction(StatusBarSettingsAction.ToggleDate) }
        binding.dateFormat.setOnClickListener { onAction(StatusBarSettingsAction.ChooseDateFormat) }
        binding.showTime.setOnClickListener { onAction(StatusBarSettingsAction.ToggleTime) }
        binding.timeFormat.setOnClickListener { onAction(StatusBarSettingsAction.ChooseTimeFormat) }
        binding.showNetwork.setOnClickListener { onAction(StatusBarSettingsAction.ToggleNetwork) }
        binding.showInputs.setOnClickListener { onAction(StatusBarSettingsAction.ToggleInputs) }
        binding.showNotifications.setOnClickListener { onAction(StatusBarSettingsAction.ToggleNotifications) }
        binding.autoHideNotificationBell.setOnClickListener {
            onAction(StatusBarSettingsAction.ToggleAutoHideNotificationBell)
        }
        binding.notificationAccess.setOnClickListener { onAction(StatusBarSettingsAction.ConfigureNotificationAccess) }
        binding.systemNotificationPopups.setOnClickListener {
            onAction(StatusBarSettingsAction.ToggleSystemNotificationPopups)
        }
        binding.overlayAccess.setOnClickListener { onAction(StatusBarSettingsAction.ConfigureOverlayAccess) }
    }

    fun render(settings: StatusBarSettings) {
        binding.autoHide.setBooleanLabel(R.string.status_auto_hide_value, settings.autoHide)
        binding.showDate.setBooleanLabel(R.string.status_show_date_value, settings.showDate)
        binding.showTime.setBooleanLabel(R.string.status_show_time_value, settings.showTime)
        binding.showNetwork.setBooleanLabel(R.string.status_show_network_value, settings.showNetwork)
        binding.showInputs.setBooleanLabel(R.string.status_show_inputs_value, settings.showInputs)
        binding.showNotifications.setBooleanLabel(
            R.string.status_show_notifications_value,
            settings.showNotifications
        )
        binding.autoHideNotificationBell.setBooleanLabel(
            R.string.status_auto_hide_notification_bell_value,
            settings.autoHideNotificationBell
        )
        binding.systemNotificationPopups.setBooleanLabel(
            R.string.system_notification_popups_value,
            settings.systemNotificationPopups
        )
        binding.autoHideNotificationBell.isEnabled = settings.showNotifications
        binding.dateFormat.text = binding.root.context.getString(R.string.status_date_format_value, settings.dateFormat)
        binding.timeFormat.text = binding.root.context.getString(R.string.status_time_format_value, settings.timeFormat)
        binding.dateFormat.isEnabled = settings.showDate
        binding.timeFormat.isEnabled = settings.showTime
    }

    private fun MaterialButton.setBooleanLabel(labelRes: Int, enabled: Boolean) {
        text =
            context.getString(labelRes, context.getString(if (enabled) R.string.setting_on else R.string.setting_off))
    }
}
