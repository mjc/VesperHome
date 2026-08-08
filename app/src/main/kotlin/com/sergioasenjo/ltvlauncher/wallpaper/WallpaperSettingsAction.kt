package com.sergioasenjo.ltvlauncher.wallpaper

sealed interface WallpaperSettingsAction {
    data object ToggleSchedule : WallpaperSettingsAction

    data class ChooseBuiltIn(val target: WallpaperTarget) : WallpaperSettingsAction

    data class PickCustom(val target: WallpaperTarget) : WallpaperSettingsAction
}
