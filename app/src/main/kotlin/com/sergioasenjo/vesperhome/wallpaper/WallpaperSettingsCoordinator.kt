package com.sergioasenjo.vesperhome.wallpaper

import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R

class WallpaperSettingsCoordinator(
    private val activity: AppCompatActivity,
    private val currentState: () -> WallpaperState,
    private val onScheduleChanged: (Boolean) -> Unit,
    private val onBuiltInSelected: (WallpaperTarget, BuiltInWallpaper) -> Unit,
    private val onCustomSelected: (WallpaperTarget, android.net.Uri) -> Unit,
    private val onPickerUnavailable: () -> Unit
) {
    private var pendingTarget: WallpaperTarget? = null
    private val imagePicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingTarget
        pendingTarget = null
        if (target != null && uri != null) onCustomSelected(target, uri)
    }

    fun handle(action: WallpaperSettingsAction) {
        when (action) {
            WallpaperSettingsAction.ToggleSchedule -> {
                onScheduleChanged(!currentState().settings.timeBasedEnabled)
            }

            is WallpaperSettingsAction.ChooseBuiltIn -> showBuiltInPicker(action.target)

            is WallpaperSettingsAction.PickCustom -> openImagePicker(action.target)
        }
    }

    private fun showBuiltInPicker(target: WallpaperTarget) {
        val wallpapers = BuiltInWallpaper.entries
        val selected = (currentState().settings.selection(target) as? WallpaperSelection.BuiltIn)?.wallpaper
        AlertDialog.Builder(activity)
            .setTitle(target.titleRes)
            .setSingleChoiceItems(
                wallpapers.map { wallpaper -> activity.getString(wallpaper.labelRes) }.toTypedArray(),
                wallpapers.indexOf(selected)
            ) { dialog, index ->
                onBuiltInSelected(target, wallpapers[index])
                dialog.dismiss()
            }
            .show()
    }

    private fun openImagePicker(target: WallpaperTarget) {
        pendingTarget = target
        try {
            imagePicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            pendingTarget = null
            onPickerUnavailable()
        }
    }

    private val WallpaperTarget.titleRes: Int
        get() = when (this) {
            WallpaperTarget.MAIN -> R.string.wallpaper
            WallpaperTarget.DAY -> R.string.day_wallpaper_title
            WallpaperTarget.NIGHT -> R.string.night_wallpaper_title
        }
}
