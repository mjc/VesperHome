package com.sergioasenjo.vesperhome.settings

import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewScreensaverSettingsBinding
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettings
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettingsAction
import com.sergioasenjo.vesperhome.screensaver.labelRes
import com.sergioasenjo.vesperhome.status.StatusBarSettings

class ScreensaverSettingsPanelBinder(
    private val binding: ViewScreensaverSettingsBinding,
    private val onAction: (ScreensaverSettingsAction) -> Unit
) {
    val buttons: List<MaterialButton> = listOf(
        binding.clockStyle,
        binding.backButtonAction,
        binding.dateFormat,
        binding.timeFormat,
        binding.systemSettings
    )

    init {
        binding.clockStyle.setOnClickListener { onAction(ScreensaverSettingsAction.ChooseClockStyle) }
        binding.backButtonAction.setOnClickListener { onAction(ScreensaverSettingsAction.ChooseBackButtonAction) }
        binding.dateFormat.setOnClickListener { onAction(ScreensaverSettingsAction.ChooseDateFormat) }
        binding.timeFormat.setOnClickListener { onAction(ScreensaverSettingsAction.ChooseTimeFormat) }
        binding.systemSettings.setOnClickListener { onAction(ScreensaverSettingsAction.OpenSystemSettings) }
    }

    fun render(screensaver: ScreensaverSettings, dateTime: StatusBarSettings) {
        val context = binding.root.context
        binding.clockStyle.text = context.getString(
            R.string.screensaver_clock_style_value,
            context.getString(screensaver.clockStyle.labelRes)
        )
        binding.backButtonAction.text = context.getString(
            R.string.back_button_action_value,
            context.getString(screensaver.backButtonAction.labelRes)
        )
        binding.dateFormat.text = context.getString(R.string.status_date_format_value, dateTime.dateFormat)
        binding.timeFormat.text = context.getString(R.string.status_time_format_value, dateTime.timeFormat)
    }
}
