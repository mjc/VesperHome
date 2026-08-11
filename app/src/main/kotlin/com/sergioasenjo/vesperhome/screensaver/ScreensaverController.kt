package com.sergioasenjo.vesperhome.screensaver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R

class ScreensaverController(
    private val activity: AppCompatActivity,
    private val currentSettings: () -> ScreensaverSettings,
    private val systemRepository: SystemScreensaverRepository,
    private val setStartDelay: (ScreensaverStartDelay) -> Unit,
    private val setStandbyDelay: (ScreensaverStandbyDelay) -> Unit,
    private val setClockStyle: (ScreensaverClockStyle) -> Unit,
    private val setBackButtonAction: (BackButtonAction) -> Unit,
    private val chooseDateFormat: () -> Unit,
    private val chooseTimeFormat: () -> Unit
) {
    private var appliedSettings: ScreensaverSettings? = null
    private var permissionWarningShown = false

    fun handleSettingsAction(action: ScreensaverSettingsAction) {
        when (action) {
            ScreensaverSettingsAction.ChooseStartDelay -> showStartDelayPicker()
            ScreensaverSettingsAction.ChooseStandbyDelay -> showStandbyDelayPicker()
            ScreensaverSettingsAction.ChooseClockStyle -> showClockStylePicker()
            ScreensaverSettingsAction.ChooseBackButtonAction -> showBackActionPicker()
            ScreensaverSettingsAction.ChooseDateFormat -> chooseDateFormat()
            ScreensaverSettingsAction.ChooseTimeFormat -> chooseTimeFormat()
            ScreensaverSettingsAction.OpenSystemSettings -> openSystemSettings()
        }
    }

    fun applySystemConfiguration(settings: ScreensaverSettings) {
        if (settings == appliedSettings) return
        if (systemRepository.applyStartDelay(settings.startDelay)) {
            appliedSettings = settings
            permissionWarningShown = false
        } else if (!permissionWarningShown) {
            permissionWarningShown = true
            Toast.makeText(activity, R.string.screensaver_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    fun handleBack(): Boolean = when (currentSettings().backButtonAction) {
        BackButtonAction.NOTHING -> false

        BackButtonAction.CLOCK -> {
            activity.startActivity(Intent(activity, ClockActivity::class.java))
            true
        }

        BackButtonAction.SCREENSAVER -> startConfiguredScreensaver()
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

    private fun showStartDelayPicker() {
        val values = ScreensaverStartDelay.entries
        showChoice(
            R.string.screensaver_start_delay,
            values.map { activity.getString(it.labelRes) },
            values.indexOf(currentSettings().startDelay)
        ) {
            setStartDelay(values[it])
            applySystemConfiguration(currentSettings().copy(startDelay = values[it]))
        }
    }

    private fun showStandbyDelayPicker() {
        val values = ScreensaverStandbyDelay.entries
        showChoice(
            R.string.screensaver_standby_delay,
            values.map { activity.getString(it.labelRes) },
            values.indexOf(currentSettings().standbyDelay)
        ) { setStandbyDelay(values[it]) }
    }

    private fun showChoice(title: Int, labels: List<String>, selected: Int, choose: (Int) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, index ->
                choose(index)
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

    private fun startConfiguredScreensaver(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
        val started = tryStartActivity(intent)
        if (!started) {
            Toast.makeText(activity, R.string.screensaver_start_failed, Toast.LENGTH_SHORT).show()
        }
        return started
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

internal val ScreensaverStartDelay.labelRes: Int
    get() = when (this) {
        ScreensaverStartDelay.MINUTES_5 -> R.string.timer_5_minutes
        ScreensaverStartDelay.MINUTES_10 -> R.string.timer_10_minutes
        ScreensaverStartDelay.MINUTES_15 -> R.string.timer_15_minutes
        ScreensaverStartDelay.MINUTES_30 -> R.string.timer_30_minutes
    }

internal val ScreensaverStandbyDelay.labelRes: Int
    get() = when (this) {
        ScreensaverStandbyDelay.NEVER -> R.string.timer_never
        ScreensaverStandbyDelay.MINUTES_15 -> R.string.timer_15_minutes
        ScreensaverStandbyDelay.MINUTES_30 -> R.string.timer_30_minutes
        ScreensaverStandbyDelay.MINUTES_60 -> R.string.timer_hour
    }
