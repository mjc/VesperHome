package com.sergioasenjo.vesperhome.about

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.accessibility.HomeButtonAccessibilityService
import com.sergioasenjo.vesperhome.notifications.LauncherNotificationListenerService
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class AboutController(
    private val activity: Activity,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val currentAppearance: () -> LauncherAppearance,
    onDismissed: () -> Unit
) {
    private val panelDelegate = lazy {
        AboutPanel(
            activity,
            onOpenSource = ::openSourceRepository,
            onOpenAdbGuide = ::showAdbGuide,
            onDismissed = onDismissed
        )
    }
    private val panel by panelDelegate

    fun show() {
        panel.show(diagnosticsRepository.snapshot(), currentAppearance())
    }

    fun refresh() {
        if (panelDelegate.isInitialized() && panel.isShowing) {
            panel.render(diagnosticsRepository.snapshot(), currentAppearance())
        }
    }

    fun renderAppearance() {
        refresh()
    }

    fun release() {
        if (panelDelegate.isInitialized()) panel.release()
    }

    private fun openSourceRepository() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_REPOSITORY_URL)))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.source_repository_open_failed)
        } catch (_: SecurityException) {
            showMessage(R.string.source_repository_open_failed)
        }
    }

    private fun showAdbGuide() {
        val accessibilityComponent = ComponentName(
            activity,
            HomeButtonAccessibilityService::class.java
        ).flattenToString()
        val accessibilityCommand =
            "adb shell 'enabled=\$(settings get secure enabled_accessibility_services); " +
                "if [ \"\$enabled\" = null ] || [ -z \"\$enabled\" ]; then value=\"$accessibilityComponent\"; " +
                "else value=\"\$enabled:$accessibilityComponent\"; fi; " +
                "settings put secure enabled_accessibility_services \"\$value\"; " +
                "settings put secure accessibility_enabled 1'"
        val notificationComponent = ComponentName(
            activity,
            LauncherNotificationListenerService::class.java
        ).flattenToString()
        AlertDialog.Builder(activity)
            .setTitle(R.string.restricted_device_guide_title)
            .setMessage(
                activity.getString(
                    R.string.restricted_device_guide,
                    accessibilityCommand,
                    "adb shell cmd notification allow_listener $notificationComponent",
                    "adb shell appops set ${activity.packageName} SYSTEM_ALERT_WINDOW allow",
                    "adb shell appops set ${activity.packageName} WRITE_SETTINGS allow"
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val SOURCE_REPOSITORY_URL = "https://github.com/sergio-asenjo/VesperHome"
    }
}
