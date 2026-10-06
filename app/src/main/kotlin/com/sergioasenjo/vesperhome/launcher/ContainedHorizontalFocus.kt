package com.sergioasenjo.vesperhome.launcher

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView

internal fun handleContainedHorizontalFocus(event: KeyEvent, focused: View?, views: List<View>): Boolean {
    if (!event.isHorizontalDpadKey) return false
    val visibleViews = views.filter { it.isShown && it.isFocusable }
    val position = visibleViews.indexOfFirst { it === focused }
    if (position == -1) return false
    if (event.action == KeyEvent.ACTION_DOWN) {
        visibleViews.getOrNull(position + event.horizontalOffset)?.requestFocus()
    }
    return true
}

internal fun RecyclerView.handleContainedHorizontalFocus(
    event: KeyEvent,
    source: View,
    columns: Int? = null,
    onEdge: (Int) -> Unit = {}
): Boolean {
    if (!event.isHorizontalDpadKey) return false
    val holder = getChildViewHolder(source)
    val position = holder.bindingAdapterPosition
    if (position == RecyclerView.NO_POSITION) return true
    if (event.action == KeyEvent.ACTION_DOWN) {
        val targetPosition = position + event.horizontalOffset
        val sameRow = columns == null || position / columns == targetPosition / columns
        if (targetPosition in 0 until (holder.bindingAdapter?.itemCount ?: 0) && sameRow) {
            val target = holder.absoluteAdapterPosition + event.horizontalOffset
            scrollToPosition(target)
            post { findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        } else if (event.repeatCount == 0) {
            onEdge(event.horizontalOffset)
        }
    }
    return true
}

internal fun RecyclerView.absolutePosition(child: RecyclerView.Adapter<*>, position: Int): Int {
    if (adapter === child) return position
    val concat = adapter as? ConcatAdapter ?: return RecyclerView.NO_POSITION
    var offset = 0
    for (adapter in concat.adapters) {
        if (adapter === child) return offset + position
        offset += adapter.itemCount
    }
    return RecyclerView.NO_POSITION
}

private val KeyEvent.isHorizontalDpadKey: Boolean
    get() = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

private val KeyEvent.horizontalOffset: Int
    get() = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
