package com.sergioasenjo.vesperhome.settings

import android.view.View
import android.view.ViewGroup
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode

internal fun View.forEachDescendant(action: (View) -> Unit) {
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).let { child ->
                action(child)
                child.forEachDescendant(action)
            }
        }
    }
}

internal fun View.setSoundEffectsEnabledRecursively(enabled: Boolean) {
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

internal val LauncherTheme.labelRes: Int
    get() = when (this) {
        LauncherTheme.DARK -> R.string.theme_dark
        LauncherTheme.LIGHT -> R.string.theme_light
    }
