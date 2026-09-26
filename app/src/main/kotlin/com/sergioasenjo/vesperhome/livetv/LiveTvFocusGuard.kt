package com.sergioasenjo.vesperhome.livetv

import android.view.View
import android.view.ViewGroup

internal class LiveTvFocusGuard(private val launcherRoot: ViewGroup) {
    private val blockedViews = mutableSetOf<View>()

    fun blockOutside(overlay: View) {
        if (blockedViews.isNotEmpty()) return
        fun block(view: View) {
            if (view === overlay) return
            if (view.isFocusable) {
                blockedViews += view
                view.isFocusable = false
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) block(view.getChildAt(index))
            }
        }
        for (index in 0 until launcherRoot.childCount) block(launcherRoot.getChildAt(index))
    }

    fun restore() {
        blockedViews.forEach { it.isFocusable = true }
        blockedViews.clear()
    }
}
