package com.sergioasenjo.ltvlauncher.brightness

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.settings.BrightnessSettingsAction
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BrightnessController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val currentSettings: () -> BrightnessSettings,
    private val setEnabled: (Boolean) -> Unit,
    private val setBrightness: (BrightnessPeriod, Int) -> Unit,
    private val onPeriodChanged: () -> Unit
) {
    private var settings = BrightnessSettings()
    private var schedulerJob: Job? = null
    private var enableAfterPermission = false

    fun hasPermission(): Boolean = Settings.System.canWrite(activity)

    fun render(updatedSettings: BrightnessSettings) {
        val settingsChanged = settings != updatedSettings
        settings = updatedSettings
        if (!settings.enabled || !hasPermission()) {
            stopScheduler()
            return
        }

        if (schedulerJob == null) {
            schedulerJob = scope.launch {
                while (isActive) {
                    applyCurrentPeriod()
                    onPeriodChanged()
                    delay(SCHEDULER_INTERVAL_MS)
                }
            }
        } else if (settingsChanged) {
            applyCurrentPeriod()
        }
    }

    fun onResume() {
        settings = currentSettings()
        if (enableAfterPermission) {
            enableAfterPermission = false
            if (hasPermission()) setEnabled(true)
        }
        if (settings.enabled && hasPermission()) applyCurrentPeriod()
        render(settings)
    }

    fun handleSettingsAction(action: BrightnessSettingsAction) {
        when (action) {
            BrightnessSettingsAction.ToggleScheduler -> {
                if (settings.enabled) {
                    setEnabled(false)
                } else if (hasPermission()) {
                    setEnabled(true)
                } else {
                    enableAfterPermission = true
                    openPermissionSettings()
                }
            }

            BrightnessSettingsAction.ConfigurePermission -> {
                enableAfterPermission = false
                openPermissionSettings()
            }

            is BrightnessSettingsAction.SetPeriodBrightness -> setBrightness(action.period, action.percentage)
        }
    }

    fun release() {
        stopScheduler()
    }

    private fun applyCurrentPeriod() {
        if (!hasPermission()) {
            stopScheduler()
            return
        }
        val percentage = settings.percentageFor(BrightnessPeriod.current())
        val systemValue = (percentage / 100f * MAX_SYSTEM_BRIGHTNESS).roundToInt()
        try {
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            if (!Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, systemValue)) {
                Log.e(TAG, "The device rejected the system brightness update")
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "System brightness permission was revoked", error)
            stopScheduler()
        }
    }

    private fun openPermissionSettings() {
        val packageIntent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${activity.packageName}")
        )
        if (startSettings(packageIntent) || startSettings(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))) return

        AlertDialog.Builder(activity)
            .setTitle(R.string.brightness_permission_guide_title)
            .setMessage(
                activity.getString(
                    R.string.brightness_permission_guide,
                    "adb shell appops set ${activity.packageName} WRITE_SETTINGS allow"
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun startSettings(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun stopScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
    }

    private companion object {
        const val TAG = "BrightnessController"
        const val MAX_SYSTEM_BRIGHTNESS = 255
        const val SCHEDULER_INTERVAL_MS = 60_000L
    }
}
