package com.sergioasenjo.vesperhome.settings

import android.R.attr.state_focused
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.launcher.forWallpaper
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRenderer
import com.sergioasenjo.vesperhome.wallpaper.WallpaperState

class ManagementScreenAppearanceRenderer(
    root: FrameLayout,
    wallpaper: ImageView,
    private val content: ViewGroup,
    private val workspaces: List<View>
) {
    private val wallpaperRenderer = WallpaperRenderer(root, wallpaper)

    fun render(appearance: LauncherAppearance, wallpaper: WallpaperState): LauncherAppearance {
        wallpaperRenderer.render(wallpaper, appearance.palette)
        val homeAppearance = appearance.forWallpaper(wallpaper)
        val palette = homeAppearance.palette
        workspaces.forEach { workspace ->
            workspace.background = GradientDrawable().apply {
                cornerRadius = dp(workspace, WORKSPACE_RADIUS_DP).toFloat()
                setColor(palette.surface)
                setStroke(dp(workspace, 1), palette.stroke)
            }
        }
        content.forEachDescendant { view ->
            when (view) {
                is MaterialButton -> applyButton(view, palette)

                is TextView -> view.setTextColor(
                    when (view.tag) {
                        ACCENT_TAG -> palette.focus
                        SECONDARY_TAG -> palette.secondaryText
                        else -> palette.primaryText
                    }
                )
            }
        }
        content.setSoundEffectsEnabledRecursively(homeAppearance.keyClickSounds)
        return homeAppearance
    }

    private fun applyButton(button: MaterialButton, palette: LauncherPalette) {
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
        const val WORKSPACE_RADIUS_DP = 16
    }
}
