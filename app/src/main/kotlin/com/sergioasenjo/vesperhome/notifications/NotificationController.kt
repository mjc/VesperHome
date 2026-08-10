package com.sergioasenjo.vesperhome.notifications

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.status.StatusBarSettings
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction
import kotlinx.coroutines.launch

class NotificationController(
    private val activity: AppCompatActivity,
    private val binding: ActivityLauncherBinding,
    private val repository: NotificationRepository,
    private val currentSettings: () -> StatusBarSettings,
    private val setShowNotifications: (Boolean) -> Unit,
    private val setAutoHideBell: (Boolean) -> Unit,
    private val setSystemPopups: (Boolean) -> Unit
) {
    private var state = NotificationState()
    private var settings = StatusBarSettings()
    private var appearance = LauncherAppearance()
    private var panelAppearance = LauncherAppearance()
    private val panelDelegate = lazy {
        NotificationPanel(
            activity,
            onDismissNotification = { notification -> repository.dismiss(notification.key) },
            onDismissAll = repository::dismissAll,
            onDismissed = {
                if (binding.statusNotificationsContainer.visibility == View.VISIBLE) {
                    binding.statusNotifications.requestFocus()
                } else {
                    binding.openLauncherSettings.requestFocus()
                }
            }
        )
    }
    private val panel by panelDelegate

    init {
        binding.statusNotifications.setOnClickListener {
            panel.show(state, panelAppearance)
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect { state ->
                    this@NotificationController.state = state
                    renderStatus()
                    if (panelDelegate.isInitialized() && panel.isShowing) panel.render(state, panelAppearance)
                }
            }
        }
        repository.refreshPermissions()
    }

    fun render(settings: StatusBarSettings, appearance: LauncherAppearance, panelAppearance: LauncherAppearance) {
        this.settings = settings
        this.appearance = appearance
        this.panelAppearance = panelAppearance
        applyAppearance()
        renderStatus()
        if (panelDelegate.isInitialized() && panel.isShowing) panel.render(state, panelAppearance)
    }

    fun refreshPermissions() {
        repository.refreshPermissions()
    }

    fun handleSettingsAction(action: StatusBarSettingsAction): Boolean {
        val settings = currentSettings()
        return when (action) {
            StatusBarSettingsAction.ToggleNotifications -> {
                setShowNotifications(!settings.showNotifications)
                true
            }

            StatusBarSettingsAction.ToggleAutoHideNotificationBell -> {
                setAutoHideBell(!settings.autoHideNotificationBell)
                true
            }

            StatusBarSettingsAction.ConfigureNotificationAccess -> {
                openNotificationAccess()
                true
            }

            StatusBarSettingsAction.ToggleSystemNotificationPopups -> {
                if (settings.systemNotificationPopups) {
                    setSystemPopups(false)
                } else if (state.overlayEnabled) {
                    setSystemPopups(true)
                } else {
                    openOverlayAccess()
                }
                true
            }

            StatusBarSettingsAction.ConfigureOverlayAccess -> {
                openOverlayAccess()
                true
            }

            else -> false
        }
    }

    fun release() {
        if (panelDelegate.isInitialized()) panel.release()
    }

    private fun renderStatus() {
        val count = state.notifications.size
        binding.statusNotificationsContainer.visibility = if (
            settings.showNotifications &&
            state.listenerEnabled &&
            (!settings.autoHideNotificationBell || count > 0)
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.statusNotificationBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.statusNotificationBadge.text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()
    }

    private fun applyAppearance() {
        val palette = appearance.palette
        binding.statusNotifications.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val iconColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        binding.statusNotifications.setTextColor(iconColors)
        binding.statusNotifications.iconTint = iconColors
    }

    private fun openNotificationAccess() {
        try {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showNotificationAccessGuide()
        } catch (_: SecurityException) {
            showNotificationAccessGuide()
        }
    }

    private fun openOverlayAccess() {
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (_: ActivityNotFoundException) {
            showOverlayAccessGuide()
        } catch (_: SecurityException) {
            showOverlayAccessGuide()
        }
    }

    private fun showNotificationAccessGuide() {
        showAdbGuide(
            R.string.notification_access_guide_title,
            activity.getString(
                R.string.notification_access_guide,
                "adb shell cmd notification allow_listener ${activity.packageName}/${LauncherNotificationListenerService::class.java.name}"
            )
        )
    }

    private fun showOverlayAccessGuide() {
        showAdbGuide(
            R.string.overlay_access_guide_title,
            activity.getString(
                R.string.overlay_access_guide,
                "adb shell appops set ${activity.packageName} SYSTEM_ALERT_WINDOW allow"
            )
        )
    }

    private fun showAdbGuide(title: Int, message: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private companion object {
        const val MAX_BADGE_COUNT = 99
    }
}
