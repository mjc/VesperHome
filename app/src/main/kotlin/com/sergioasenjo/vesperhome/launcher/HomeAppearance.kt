package com.sergioasenjo.vesperhome.launcher

import androidx.core.graphics.ColorUtils
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import com.sergioasenjo.vesperhome.wallpaper.BuiltInWallpaper
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSelection
import com.sergioasenjo.vesperhome.wallpaper.WallpaperState

internal fun LauncherAppearance.forWallpaper(wallpaper: WallpaperState): LauncherAppearance {
    val selection = wallpaper.activeSelection
    if (selection !is WallpaperSelection.BuiltIn || selection.wallpaper == BuiltInWallpaper.MIDNIGHT) return this
    val wallpaperTheme = if (selection.wallpaper.isLightColored) LauncherTheme.LIGHT else LauncherTheme.DARK
    return if (theme == wallpaperTheme) this else copy(theme = wallpaperTheme)
}

private val BuiltInWallpaper.isLightColored: Boolean
    get() = colors.all { ColorUtils.calculateLuminance(it) > LIGHT_WALLPAPER_LUMINANCE }

private const val LIGHT_WALLPAPER_LUMINANCE = 0.25
