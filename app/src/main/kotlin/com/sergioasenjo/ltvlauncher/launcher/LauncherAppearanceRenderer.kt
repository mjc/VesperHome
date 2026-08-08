package com.sergioasenjo.ltvlauncher.launcher

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

internal fun renderLauncherAppearance(
    binding: ActivityLauncherBinding,
    contentBinding: ViewLauncherContentBinding,
    appearance: LauncherAppearance
) {
    val palette = appearance.palette
    contentBinding.jellyfinPanel.background = roundedBackground(binding.root, palette.surface, palette.stroke)
    binding.title.setTextColor(palette.primaryText)
    contentBinding.musicTitle.setTextColor(palette.primaryText)
    contentBinding.musicArtist.setTextColor(palette.secondaryText)
    val strokeColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(palette.focus, Color.TRANSPARENT)
    )
    val buttonBackground = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(palette.focusedSurface, palette.surface)
    )
    val buttonText = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(palette.focusedText, palette.primaryText)
    )
    listOf(
        binding.openLauncherSettings,
        contentBinding.musicPlayPause,
        contentBinding.musicNext
    ).forEach { button ->
        button.backgroundTintList = buttonBackground
        button.setTextColor(buttonText)
        button.strokeColor = strokeColors
        button.strokeWidth = dp(binding.root, 2)
    }
    contentBinding.musicLoading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
    binding.root.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
}

private fun roundedBackground(view: View, color: Int, strokeColor: Int): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dp(view, 12).toFloat()
    setColor(color)
    setStroke(dp(view, 1), strokeColor)
}

private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

private fun View.setSoundEffectsEnabledRecursively(enabled: Boolean) {
    isSoundEffectsEnabled = enabled
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).setSoundEffectsEnabledRecursively(enabled)
    }
}

internal val ApplicationSortMode.labelRes: Int
    get() = when (this) {
        ApplicationSortMode.MANUAL -> R.string.sort_manual
        ApplicationSortMode.ALPHABETICAL -> R.string.sort_alphabetical
        ApplicationSortMode.LAST_USED -> R.string.sort_last_used
    }
