package com.sergioasenjo.ltvlauncher.launcher

import androidx.core.graphics.ColorUtils
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import com.sergioasenjo.ltvlauncher.settings.LauncherTheme
import com.sergioasenjo.ltvlauncher.wallpaper.BuiltInWallpaper
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperSelection
import com.sergioasenjo.ltvlauncher.wallpaper.WallpaperState

internal fun LauncherAppearance.forWallpaper(wallpaper: WallpaperState): LauncherAppearance {
    val selection = wallpaper.activeSelection
    if (selection !is WallpaperSelection.BuiltIn || selection.wallpaper == BuiltInWallpaper.MIDNIGHT) return this
    val wallpaperTheme = if (selection.wallpaper.isLightColored) LauncherTheme.LIGHT else LauncherTheme.DARK
    return if (theme == wallpaperTheme) this else copy(theme = wallpaperTheme)
}

private val BuiltInWallpaper.isLightColored: Boolean
    get() = colors.all { ColorUtils.calculateLuminance(it) > LIGHT_WALLPAPER_LUMINANCE }

private const val LIGHT_WALLPAPER_LUMINANCE = 0.25
