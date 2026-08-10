package com.sergioasenjo.vesperhome.status

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class StatusBarController(
    private val activity: AppCompatActivity,
    private val binding: ActivityLauncherBinding,
    private val networkStatusRepository: NetworkStatusRepository,
    private val currentSettings: () -> StatusBarSettings,
    private val setAutoHide: (Boolean) -> Unit,
    private val setShowDate: (Boolean) -> Unit,
    private val setShowTime: (Boolean) -> Unit,
    private val setShowNetwork: (Boolean) -> Unit,
    private val setShowInputs: (Boolean) -> Unit,
    private val setDateFormat: (String) -> Unit,
    private val setTimeFormat: (String) -> Unit
) {
    private val clockHandler = Handler(Looper.getMainLooper())
    private var settings = StatusBarSettings()
    private var appearance = LauncherAppearance()
    private var networkStatus = NetworkStatus()
    private val updateClock = object : Runnable {
        override fun run() {
            renderDateTime()
            scheduleClockUpdate()
        }
    }
    private val hideStatusBar = Runnable {
        if (settings.autoHide && !binding.statusBar.hasFocus()) {
            binding.statusBar.animate()
                .alpha(0f)
                .setDuration(STATUS_FADE_DURATION_MS)
                .withEndAction {
                    if (settings.autoHide && !binding.statusBar.hasFocus()) {
                        binding.statusBar.visibility = View.INVISIBLE
                    }
                }
                .start()
        }
    }

    init {
        binding.statusNetwork.setOnClickListener(::openWifiSettings)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkStatusRepository.observeStatus().collect { status ->
                    networkStatus = status
                    renderNetwork()
                }
            }
        }
        scheduleClockUpdate()
    }

    fun render(settings: StatusBarSettings, appearance: LauncherAppearance) {
        val autoHideChanged = this.settings.autoHide != settings.autoHide
        this.settings = settings
        this.appearance = appearance
        binding.statusDateTime.visibility =
            if (settings.showDate || settings.showTime) View.VISIBLE else View.GONE
        binding.statusNetwork.visibility = if (settings.showNetwork) View.VISIBLE else View.GONE
        applyAppearance()
        renderDateTime()
        scheduleClockUpdate()
        renderNetwork()
        if (!settings.autoHide) {
            showImmediately()
        } else if (autoHideChanged) {
            revealForInteraction()
        }
    }

    fun handleSettingsAction(action: StatusBarSettingsAction) {
        val settings = currentSettings()
        when (action) {
            StatusBarSettingsAction.ToggleAutoHide -> setAutoHide(!settings.autoHide)

            StatusBarSettingsAction.ToggleDate -> setShowDate(!settings.showDate)

            StatusBarSettingsAction.ToggleTime -> setShowTime(!settings.showTime)

            StatusBarSettingsAction.ToggleNetwork -> setShowNetwork(!settings.showNetwork)

            StatusBarSettingsAction.ToggleInputs -> setShowInputs(!settings.showInputs)

            StatusBarSettingsAction.ChooseDateFormat -> showFormatPicker(
                title = activity.getString(R.string.status_date_format_value, settings.dateFormat),
                patterns = DATE_FORMAT_PRESETS,
                current = settings.dateFormat,
                customTitle = R.string.custom_date_format,
                save = setDateFormat
            )

            StatusBarSettingsAction.ChooseTimeFormat -> showFormatPicker(
                title = activity.getString(R.string.status_time_format_value, settings.timeFormat),
                patterns = TIME_FORMAT_PRESETS,
                current = settings.timeFormat,
                customTitle = R.string.custom_time_format,
                save = setTimeFormat
            )

            StatusBarSettingsAction.ToggleNotifications,
            StatusBarSettingsAction.ToggleAutoHideNotificationBell,
            StatusBarSettingsAction.ConfigureNotificationAccess,
            StatusBarSettingsAction.ToggleSystemNotificationPopups,
            StatusBarSettingsAction.ConfigureOverlayAccess -> Unit
        }
    }

    fun onKeyEvent(event: KeyEvent) {
        if (settings.autoHide && event.action == KeyEvent.ACTION_DOWN) revealForInteraction()
    }

    fun release() {
        clockHandler.removeCallbacksAndMessages(null)
        binding.statusBar.animate().cancel()
    }

    private fun revealForInteraction() {
        clockHandler.removeCallbacks(hideStatusBar)
        binding.statusBar.animate().cancel()
        binding.statusBar.visibility = View.VISIBLE
        binding.statusBar.animate().alpha(1f).setDuration(STATUS_FADE_DURATION_MS).start()
        clockHandler.postDelayed(hideStatusBar, STATUS_VISIBLE_DURATION_MS)
    }

    private fun showImmediately() {
        clockHandler.removeCallbacks(hideStatusBar)
        binding.statusBar.animate().cancel()
        binding.statusBar.visibility = View.VISIBLE
        binding.statusBar.alpha = 1f
    }

    private fun renderDateTime() {
        if (!settings.showDate && !settings.showTime) return
        val now = Date()
        val values = buildList {
            if (settings.showDate) add(format(now, settings.dateFormat))
            if (settings.showTime) add(format(now, settings.timeFormat))
        }
        binding.statusDateTime.text = values.joinToString("  ·  ")
    }

    private fun renderNetwork() {
        if (!settings.showNetwork) return
        val wifiLevel = networkStatus.wifiLevel?.coerceIn(0, WIFI_LEVEL_ICONS.lastIndex)
        binding.statusNetwork.setIconResource(
            when {
                !networkStatus.validated -> R.drawable.ic_offline
                networkStatus.transport == NetworkTransport.WIFI && wifiLevel != null -> WIFI_LEVEL_ICONS[wifiLevel]
                networkStatus.transport == NetworkTransport.WIFI -> R.drawable.ic_wifi_4
                networkStatus.transport == NetworkTransport.ETHERNET -> R.drawable.ic_ethernet
                networkStatus.transport == NetworkTransport.OTHER -> R.drawable.ic_connected
                else -> R.drawable.ic_offline
            }
        )
        binding.statusNetwork.contentDescription = when {
            !networkStatus.validated -> activity.getString(R.string.network_no_connection)

            networkStatus.transport == NetworkTransport.WIFI && wifiLevel != null ->
                activity.getString(R.string.network_wifi_level, wifiLevel)

            networkStatus.transport == NetworkTransport.WIFI -> activity.getString(R.string.network_wifi)

            networkStatus.transport == NetworkTransport.ETHERNET -> activity.getString(R.string.network_ethernet)

            networkStatus.transport == NetworkTransport.OTHER -> activity.getString(R.string.network_connected)

            else -> activity.getString(R.string.network_no_connection)
        }
    }

    private fun applyAppearance() {
        val palette = appearance.palette
        binding.statusDateTime.setTextColor(palette.primaryText)
        binding.statusNetwork.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val iconColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        binding.statusNetwork.setTextColor(iconColors)
        binding.statusNetwork.iconTint = iconColors
    }

    private fun showFormatPicker(
        title: String,
        patterns: List<String>,
        current: String,
        customTitle: Int,
        save: (String) -> Unit
    ) {
        val now = Date()
        val labels = patterns.map { pattern -> "${format(now, pattern)}    $pattern" } +
            activity.getString(R.string.custom_format)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), patterns.indexOf(current)) { dialog, index ->
                dialog.dismiss()
                if (index ==
                    patterns.size
                ) {
                    showCustomFormatDialog(customTitle, current, save)
                } else {
                    save(patterns[index])
                }
            }
            .show()
    }

    private fun showCustomFormatDialog(title: Int, current: String, save: (String) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.format_pattern_hint)
            setText(current)
            selectAll()
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pattern = input.text.toString().trim()
                if (isValidPattern(pattern)) {
                    save(pattern)
                } else {
                    Toast.makeText(activity, R.string.invalid_date_time_format, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openWifiSettings(view: View) {
        try {
            activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleClockUpdate() {
        clockHandler.removeCallbacks(updateClock)
        val interval = if (settings.dateFormat.contains('s') || settings.timeFormat.contains('s')) {
            SECOND_MS
        } else {
            MINUTE_MS
        }
        clockHandler.postDelayed(updateClock, interval - System.currentTimeMillis() % interval)
    }

    private fun format(date: Date, pattern: String): String = try {
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    } catch (_: IllegalArgumentException) {
        ""
    }

    private fun isValidPattern(pattern: String): Boolean = pattern.isNotEmpty() && try {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    private companion object {
        const val STATUS_VISIBLE_DURATION_MS = 5_000L
        const val STATUS_FADE_DURATION_MS = 150L
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60_000L
        val DATE_FORMAT_PRESETS = listOf("EEEE d", "E d", "dd/MM/y", "MMM d, y", "d MMMM", "M/d/y")
        val TIME_FORMAT_PRESETS = listOf("H:mm", "hh:mm", "h:mm a", "hh:mm a", "HH:mm")
        val WIFI_LEVEL_ICONS = listOf(
            R.drawable.ic_wifi_0,
            R.drawable.ic_wifi_1,
            R.drawable.ic_wifi_2,
            R.drawable.ic_wifi_3,
            R.drawable.ic_wifi_4
        )
    }
}
