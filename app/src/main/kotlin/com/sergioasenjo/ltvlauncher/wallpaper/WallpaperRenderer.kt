package com.sergioasenjo.ltvlauncher.wallpaper

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import coil3.load

class WallpaperRenderer(private val root: FrameLayout, private val image: ImageView) {
    private var renderedState: WallpaperState? = null

    fun render(state: WallpaperState) {
        if (renderedState == state) return
        renderedState = state
        when (val selection = state.activeSelection) {
            is WallpaperSelection.BuiltIn -> renderBuiltIn(selection.wallpaper)
            WallpaperSelection.Custom -> renderCustom(state)
        }
    }

    private fun renderBuiltIn(wallpaper: BuiltInWallpaper) {
        image.visibility = View.GONE
        image.setImageDrawable(null)
        root.background = GradientDrawable(orientation(wallpaper.angle), wallpaper.colors.copyOf())
    }

    private fun renderCustom(state: WallpaperState) {
        val file = state.activeCustomFile ?: return
        root.setBackgroundColor(BuiltInWallpaper.MIDNIGHT.colors.last())
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
