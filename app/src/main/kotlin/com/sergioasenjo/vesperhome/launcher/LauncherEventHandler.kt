package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.sergioasenjo.vesperhome.R

fun handleLauncherEvent(activity: Activity, settingsLauncher: ActivityResultLauncher<Intent>, event: LauncherEvent) {
    val message = when (event) {
        LauncherEvent.LaunchFailed -> R.string.launch_failed

        LauncherEvent.PreferenceUpdateFailed -> R.string.preference_update_failed

        LauncherEvent.CategoryUpdateFailed -> R.string.category_update_failed

        LauncherEvent.WallpaperUpdateFailed -> R.string.wallpaper_update_failed

        LauncherEvent.CustomBannerUpdateFailed -> R.string.custom_banner_update_failed

        is LauncherEvent.OpenIntent -> {
            try {
                settingsLauncher.launch(event.intent)
                return
            } catch (_: ActivityNotFoundException) {
                R.string.settings_open_failed
            }
        }
    }
    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
