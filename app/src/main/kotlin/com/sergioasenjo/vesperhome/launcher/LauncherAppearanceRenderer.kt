package com.sergioasenjo.vesperhome.launcher

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.setSoundEffectsEnabledRecursively

internal class LauncherAppearanceRenderer(
    private val binding: ActivityLauncherBinding,
    private val contentBinding: ViewLauncherContentBinding
) {
    private var renderedAppearance: LauncherAppearance? = null

    fun render(appearance: LauncherAppearance) {
        if (renderedAppearance == appearance) return
        renderedAppearance = appearance
        val palette = appearance.palette
        contentBinding.jellyfinPanel.background = null
        contentBinding.musicArtwork.clipToOutline = true
        contentBinding.musicArtwork.background = GradientDrawable().apply {
            cornerRadius = dp(binding.root, ARTWORK_CORNER_RADIUS_DP).toFloat()
            setColor(palette.surface)
        }
        contentBinding.musicArtwork.setOnFocusChangeListener { view, focused ->
            view.foreground = GradientDrawable().apply {
                cornerRadius = dp(view, ARTWORK_CORNER_RADIUS_DP).toFloat()
                setColor(if (focused) palette.focusedSurface.withAlpha(ARTWORK_FOCUS_FILL_ALPHA) else Color.TRANSPARENT)
                setStroke(
                    if (focused && appearance.showFocusOutline) dp(view, ARTWORK_FOCUS_STROKE_DP) else 0,
                    palette.focus
                )
            }
            val scale = if (focused) ARTWORK_FOCUSED_SCALE else 1f
            view.animate().cancel()
            if (appearance.selectorTransitionAnimations) {
                view.animate().scaleX(scale).scaleY(scale).setDuration(SELECTOR_TRANSITION_MS).start()
            } else {
                view.scaleX = scale
                view.scaleY = scale
            }
        }
        contentBinding.musicArtwork.isSelected = contentBinding.musicArtwork.hasFocus()
        contentBinding.musicArtwork.onFocusChangeListener?.onFocusChange(
            contentBinding.musicArtwork,
            contentBinding.musicArtwork.hasFocus()
        )
        binding.title.setTextColor(palette.secondaryText)
        contentBinding.musicTitle.setTextColor(palette.primaryText)
        contentBinding.musicArtist.setTextColor(palette.secondaryText)
        val buttonBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
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
            button.iconTint = buttonText
        }
        contentBinding.musicLoading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
        binding.root.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
    }
}

private const val ARTWORK_CORNER_RADIUS_DP = 10
private const val ARTWORK_FOCUS_STROKE_DP = 3
private const val ARTWORK_FOCUS_FILL_ALPHA = 96
private const val ARTWORK_FOCUSED_SCALE = 1.1f
private const val SELECTOR_TRANSITION_MS = 140L

private fun dp(view: View, value: Int): Int = (value * view.resources.displayMetrics.density).toInt()

private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

internal val ApplicationSortMode.labelRes: Int
    get() = when (this) {
        ApplicationSortMode.MANUAL -> R.string.sort_manual
        ApplicationSortMode.ALPHABETICAL -> R.string.sort_alphabetical
        ApplicationSortMode.LAST_USED -> R.string.sort_last_used
    }
