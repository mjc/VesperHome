package com.sergioasenjo.vesperhome.livetv

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

internal val LIVE_TV_DIRECTIONAL_DPAD_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT
)

internal class LiveTvChannelGridNavigator(
    private val recyclerView: RecyclerView,
    private val closeButton: View,
    private val columns: Int
) {
    private var pendingPosition: Int? = null

    fun onKey(position: Int, event: KeyEvent): Boolean {
        if (event.keyCode !in LIVE_TV_DIRECTIONAL_DPAD_KEYS) return false
        if (event.action == KeyEvent.ACTION_UP) {
            pendingPosition = null
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val current = pendingPosition?.takeIf { event.repeatCount > 0 } ?: position
        val target = targetPosition(current, event.keyCode)
        if (target == null) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && current < columns && event.repeatCount == 0) {
                closeButton.requestFocus()
            }
            return true
        }
        pendingPosition = target
        focusPendingPosition()
        return true
    }

    fun focusFirst() {
        reset()
        if (itemCount == 0) {
            closeButton.requestFocus()
            return
        }
        pendingPosition = 0
        recyclerView.scrollToPosition(0)
        recyclerView.post(::focusPendingPosition)
    }

    fun reset() {
        pendingPosition = null
    }

    private fun targetPosition(current: Int, keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> (current - columns).takeIf { it >= 0 }

        KeyEvent.KEYCODE_DPAD_DOWN -> (current + columns).coerceAtMost(itemCount - 1).takeIf { it > current }

        KeyEvent.KEYCODE_DPAD_LEFT -> (current - 1).takeIf { current % columns > 0 }

        KeyEvent.KEYCODE_DPAD_RIGHT ->
            (current + 1).takeIf { current % columns < columns - 1 && it < itemCount }

        else -> null
    }

    private fun focusPendingPosition() {
        val position = pendingPosition ?: return
        if (position !in 0 until itemCount) return
        val item = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
        if (item != null) {
            item.requestFocus()
        } else {
            recyclerView.scrollToPosition(position)
            recyclerView.postOnAnimation(::focusPendingPosition)
        }
    }

    private val itemCount: Int
        get() = recyclerView.adapter?.itemCount ?: 0
}
