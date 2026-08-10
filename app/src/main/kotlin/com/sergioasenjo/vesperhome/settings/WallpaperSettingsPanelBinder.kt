package com.sergioasenjo.vesperhome.settings

import android.view.View
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewWallpaperSettingsBinding
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSelection
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettingsAction
import com.sergioasenjo.vesperhome.wallpaper.WallpaperState
import com.sergioasenjo.vesperhome.wallpaper.WallpaperTarget

class WallpaperSettingsPanelBinder(
    private val binding: ViewWallpaperSettingsBinding,
    private val onAction: (WallpaperSettingsAction) -> Unit
) {
    val buttons: List<MaterialButton> = listOf(
        binding.timeBasedWallpaper,
        binding.mainWallpaperStyle,
        binding.pickMainWallpaper,
        binding.dayWallpaperStyle,
        binding.pickDayWallpaper,
        binding.nightWallpaperStyle,
        binding.pickNightWallpaper
    )

    init {
        binding.timeBasedWallpaper.setOnClickListener { onAction(WallpaperSettingsAction.ToggleSchedule) }
        bind(binding.mainWallpaperStyle, WallpaperTarget.MAIN, chooseBuiltIn = true)
        bind(binding.pickMainWallpaper, WallpaperTarget.MAIN, chooseBuiltIn = false)
        bind(binding.dayWallpaperStyle, WallpaperTarget.DAY, chooseBuiltIn = true)
        bind(binding.pickDayWallpaper, WallpaperTarget.DAY, chooseBuiltIn = false)
        bind(binding.nightWallpaperStyle, WallpaperTarget.NIGHT, chooseBuiltIn = true)
        bind(binding.pickNightWallpaper, WallpaperTarget.NIGHT, chooseBuiltIn = false)
    }

    fun render(state: WallpaperState) {
        binding.timeBasedWallpaper.setBooleanLabel(R.string.time_based_wallpaper_value, state.settings.timeBasedEnabled)
        binding.mainWallpaperStyle.setWallpaperLabel(state.settings.main)
        binding.dayWallpaperStyle.setWallpaperLabel(state.settings.day)
        binding.nightWallpaperStyle.setWallpaperLabel(state.settings.night)
        binding.mainWallpaperActions.visibility = if (state.settings.timeBasedEnabled) View.GONE else View.VISIBLE
        binding.scheduledWallpaperActions.visibility = if (state.settings.timeBasedEnabled) View.VISIBLE else View.GONE
    }

    private fun bind(view: View, target: WallpaperTarget, chooseBuiltIn: Boolean) {
        view.setOnClickListener {
            onAction(
                if (chooseBuiltIn) {
                    WallpaperSettingsAction.ChooseBuiltIn(target)
                } else {
                    WallpaperSettingsAction.PickCustom(target)
                }
            )
        }
    }

    private fun MaterialButton.setBooleanLabel(labelRes: Int, enabled: Boolean) {
        text =
            context.getString(labelRes, context.getString(if (enabled) R.string.setting_on else R.string.setting_off))
    }

    private fun MaterialButton.setWallpaperLabel(selection: WallpaperSelection) {
        text = when (selection) {
            is WallpaperSelection.BuiltIn -> context.getString(
                R.string.wallpaper_style_value,
                context.getString(selection.wallpaper.labelRes)
            )

            WallpaperSelection.Custom -> context.getString(R.string.custom_wallpaper_value)
        }
    }
}
