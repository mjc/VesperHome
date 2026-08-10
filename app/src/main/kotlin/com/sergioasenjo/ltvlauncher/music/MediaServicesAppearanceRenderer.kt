package com.sergioasenjo.ltvlauncher.music

import android.R.attr.state_focused
import android.R.attr.state_selected
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.ltvlauncher.databinding.ActivityJellyfinSetupBinding
import com.sergioasenjo.ltvlauncher.launcher.forWallpaper
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import com.sergioasenjo.ltvlauncher.settings.LauncherPalette
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperRenderer
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperState

class MediaServicesAppearanceRenderer(private val binding: ActivityJellyfinSetupBinding) {
    private val wallpaperRenderer = WallpaperRenderer(binding.root, binding.wallpaper)

    fun render(appearance: LauncherAppearance, wallpaper: WallpaperState): LauncherAppearance {
        wallpaperRenderer.render(wallpaper, appearance.palette)
        val homeAppearance = appearance.forWallpaper(wallpaper)
        val palette = homeAppearance.palette
        applyWorkspace(binding.jellyfinPage.root, palette)
        applyWorkspace(binding.sonarrPage, palette)
        applyWorkspace(binding.radarrPage, palette)
        binding.content.forEachDescendant { view -> applyView(view, palette) }
        applyNavigation(binding.showJellyfin, palette)
        applyNavigation(binding.showSonarr, palette)
        applyNavigation(binding.showRadarr, palette)
        binding.content.setSoundEffectsEnabledRecursively(homeAppearance.keyClickSounds)
        return homeAppearance
    }

    private fun applyView(view: View, palette: LauncherPalette) {
        when (view) {
            is EditText -> {
                view.setTextColor(palette.primaryText)
                view.setHintTextColor(palette.secondaryText)
                view.background = inputBackground(view, palette)
            }

            is MaterialButton -> applyActionButton(view, palette)

            is ProgressBar -> view.indeterminateTintList = ColorStateList.valueOf(palette.focus)

            is TextView -> {
                view.setTextColor(
                    when (view.tag) {
                        ACCENT_TAG -> palette.focus
                        SECONDARY_TAG, STATUS_TAG -> palette.secondaryText
                        else -> palette.primaryText
                    }
                )
                if (view.tag == STATUS_TAG) view.background = roundedSurface(view, palette, STATUS_RADIUS_DP)
            }
        }
    }

    private fun applyActionButton(button: MaterialButton, palette: LauncherPalette) {
        val background = ColorStateList(
            arrayOf(intArrayOf(state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val foreground = ColorStateList(
            arrayOf(intArrayOf(state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        button.backgroundTintList = background
        button.setTextColor(foreground)
        button.iconTint = foreground
    }

    private fun applyNavigation(button: MaterialButton, palette: LauncherPalette) {
        button.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(state_focused), intArrayOf(state_selected), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface, Color.TRANSPARENT)
        )
        button.strokeWidth = dp(button, 1)
        button.strokeColor = ColorStateList(
            arrayOf(intArrayOf(state_focused), intArrayOf(state_selected), intArrayOf()),
            intArrayOf(palette.focus, palette.stroke, Color.TRANSPARENT)
        )
    }

    private fun applyWorkspace(view: View, palette: LauncherPalette) {
        view.background = GradientDrawable().apply {
            cornerRadius = dp(view, WORKSPACE_RADIUS_DP).toFloat()
            setColor(palette.surface)
            setStroke(dp(view, 1), palette.stroke)
        }
    }

    private fun inputBackground(view: View, palette: LauncherPalette): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(state_focused),
            roundedSurface(view, palette, INPUT_RADIUS_DP, dp(view, 2), palette.focus)
        )
        addState(
            intArrayOf(),
            roundedSurface(view, palette, INPUT_RADIUS_DP, dp(view, 1), palette.stroke)
        )
    }

    private fun roundedSurface(
        view: View,
        palette: LauncherPalette,
        radiusDp: Int,
        strokeWidth: Int = dp(view, 1),
        strokeColor: Int = palette.stroke
    ): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(view, radiusDp).toFloat()
        setColor(palette.surface)
        setStroke(strokeWidth, strokeColor)
    }

    private fun View.forEachDescendant(action: (View) -> Unit) {
        action(this)
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).forEachDescendant(action)
        }
    }

    private fun View.setSoundEffectsEnabledRecursively(enabled: Boolean) {
        isSoundEffectsEnabled = enabled
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).setSoundEffectsEnabledRecursively(enabled)
        }
    }

    private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

    private companion object {
        const val ACCENT_TAG = "accent"
        const val SECONDARY_TAG = "secondary"
        const val STATUS_TAG = "status"
        const val WORKSPACE_RADIUS_DP = 16
        const val INPUT_RADIUS_DP = 9
        const val STATUS_RADIUS_DP = 17
    }
}
