package com.sergioasenjo.ltvlauncher.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.sergioasenjo.ltvlauncher.R

class HomeButtonFixController(private val activity: Activity) {
    private val serviceComponent = ComponentName(activity, HomeButtonAccessibilityService::class.java)
    private val accessibilityManager = activity.getSystemService(AccessibilityManager::class.java)

    fun isEnabled(): Boolean = accessibilityManager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            val serviceInfo = service.resolveInfo.serviceInfo
            ComponentName(serviceInfo.packageName, serviceInfo.name) == serviceComponent
        }

    fun openAccessibilitySettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showAdbGuide()
        } catch (_: SecurityException) {
            showAdbGuide()
        }
    }

    private fun showAdbGuide() {
        val component = serviceComponent.flattenToString()
        val command =
            "adb shell 'enabled=\$(settings get secure enabled_accessibility_services); " +
                "if [ \"\$enabled\" = null ] || [ -z \"\$enabled\" ]; then value=\"$component\"; " +
                "else value=\"\$enabled:$component\"; fi; " +
                "settings put secure enabled_accessibility_services \"\$value\"; " +
                "settings put secure accessibility_enabled 1'"
        AlertDialog.Builder(activity)
            .setTitle(R.string.home_button_fix_accessibility_title)
            .setMessage(activity.getString(R.string.home_button_fix_accessibility_guide, command))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
