package com.sergioasenjo.ltvlauncher.screensaver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.ltvlauncher.R

class ScreensaverController(
    private val activity: AppCompatActivity,
    private val currentSettings: () -> ScreensaverSettings,
    private val setClockStyle: (ScreensaverClockStyle) -> Unit,
    private val setBackButtonAction: (BackButtonAction) -> Unit,
    private val chooseDateFormat: () -> Unit,
    private val chooseTimeFormat: () -> Unit
) {
    fun handleSettingsAction(action: ScreensaverSettingsAction) {
        when (action) {
            ScreensaverSettingsAction.ChooseClockStyle -> showClockStylePicker()
            ScreensaverSettingsAction.ChooseBackButtonAction -> showBackActionPicker()
            ScreensaverSettingsAction.ChooseDateFormat -> chooseDateFormat()
            ScreensaverSettingsAction.ChooseTimeFormat -> chooseTimeFormat()
            ScreensaverSettingsAction.OpenSystemSettings -> openSystemSettings()
        }
    }

    fun handleBack() {
        when (currentSettings().backButtonAction) {
            BackButtonAction.NOTHING -> Unit
            BackButtonAction.CLOCK -> activity.startActivity(Intent(activity, ClockActivity::class.java))
            BackButtonAction.SCREENSAVER -> startConfiguredScreensaver()
        }
    }

    private fun showClockStylePicker() {
        val styles = ScreensaverClockStyle.entries
        AlertDialog.Builder(activity)
            .setTitle(R.string.screensaver_clock_style)
            .setSingleChoiceItems(
                styles.map { activity.getString(it.labelRes) }.toTypedArray(),
                styles.indexOf(currentSettings().clockStyle)
            ) { dialog, index ->
                setClockStyle(styles[index])
                dialog.dismiss()
            }
            .show()
    }

    private fun showBackActionPicker() {
        val actions = BackButtonAction.entries
        AlertDialog.Builder(activity)
            .setTitle(R.string.back_button_action)
            .setSingleChoiceItems(
                actions.map { activity.getString(it.labelRes) }.toTypedArray(),
                actions.indexOf(currentSettings().backButtonAction)
            ) { dialog, index ->
                setBackButtonAction(actions[index])
                dialog.dismiss()
            }
            .show()
    }

    private fun openSystemSettings() {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).setClassName(
                "com.android.tv.settings",
                "com.android.tv.settings.device.display.daydream.DaydreamActivity"
            ),
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        if (intents.none(::tryStartActivity)) {
            Toast.makeText(activity, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startConfiguredScreensaver() {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        if (!tryStartActivity(intent)) {
            Toast.makeText(activity, R.string.screensaver_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal val ScreensaverClockStyle.labelRes: Int
    get() = when (this) {
        ScreensaverClockStyle.MINIMAL -> R.string.clock_style_minimal
        ScreensaverClockStyle.BOLD -> R.string.clock_style_bold
    }

internal val BackButtonAction.labelRes: Int
    get() = when (this) {
        BackButtonAction.NOTHING -> R.string.back_action_nothing
        BackButtonAction.CLOCK -> R.string.back_action_clock
        BackButtonAction.SCREENSAVER -> R.string.back_action_screensaver
    }
