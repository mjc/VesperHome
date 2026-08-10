package com.sergioasenjo.vesperhome.wallpaper

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import coil3.load
import com.sergioasenjo.vesperhome.settings.LauncherPalette

class WallpaperRenderer(private val root: FrameLayout, private val image: ImageView) {
    private var renderedState: WallpaperState? = null
    private var renderedPalette: LauncherPalette? = null

    fun render(state: WallpaperState, palette: LauncherPalette) {
        if (renderedState == state && renderedPalette == palette) return
        renderedState = state
        renderedPalette = palette
        when (val selection = state.activeSelection) {
            is WallpaperSelection.BuiltIn -> renderBuiltIn(selection.wallpaper, palette)
            WallpaperSelection.Custom -> renderCustom(state, palette)
        }
    }

    private fun renderBuiltIn(wallpaper: BuiltInWallpaper, palette: LauncherPalette) {
        image.visibility = View.GONE
        image.setImageDrawable(null)
        val colors = if (wallpaper == BuiltInWallpaper.MIDNIGHT) {
            intArrayOf(palette.backgroundStart, palette.backgroundCenter, palette.backgroundEnd)
        } else {
            wallpaper.colors.copyOf()
        }
        root.background = GradientDrawable(orientation(wallpaper.angle), colors)
    }

    private fun renderCustom(state: WallpaperState, palette: LauncherPalette) {
        val file = state.activeCustomFile ?: return
        root.setBackgroundColor(palette.backgroundEnd)
        image.visibility = View.VISIBLE
        image.load(file) {
            memoryCacheKey("${file.path}:${state.settings.revision}")
        }
    }

    private fun orientation(angle: Int): GradientDrawable.Orientation = when (angle) {
        0 -> GradientDrawable.Orientation.LEFT_RIGHT
        45 -> GradientDrawable.Orientation.BL_TR
        90 -> GradientDrawable.Orientation.BOTTOM_TOP
        135 -> GradientDrawable.Orientation.BR_TL
        180 -> GradientDrawable.Orientation.RIGHT_LEFT
        225 -> GradientDrawable.Orientation.TR_BL
        270 -> GradientDrawable.Orientation.TOP_BOTTOM
        else -> GradientDrawable.Orientation.TL_BR
    }
}
