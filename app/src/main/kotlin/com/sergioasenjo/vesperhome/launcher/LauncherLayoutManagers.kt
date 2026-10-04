package com.sergioasenjo.vesperhome.launcher

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Bind one additional row/card in the scroll direction, so its artwork can load before entry. */
internal class LauncherGridLayoutManager(context: Context) : GridLayoutManager(context, 1) {
    var preloadHeight = 0
    private var direction = 1

    init {
        // The extra layout row already provides preloading; avoid also caching a second row.
        isItemPrefetchEnabled = false
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (dy != 0) direction = if (dy < 0) -1 else 1
        return super.scrollVerticallyBy(dy, recycler, state)
    }

    override fun scrollToPosition(position: Int) {
        if (position < findFirstVisibleItemPosition()) {
            direction = -1
        } else if (position > findLastVisibleItemPosition()) {
            direction = 1
        }
        super.scrollToPosition(position)
    }

    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        val position = if (direction < 0) findFirstVisibleItemPosition() else findLastVisibleItemPosition()
        val height = findViewByPosition(position)?.let(::getDecoratedMeasuredHeight)?.takeIf { it > 0 }
            ?: preloadHeight
        extraLayoutSpace[0] = if (direction < 0) height else 0
        extraLayoutSpace[1] = if (direction > 0) height else 0
    }
}

internal class LauncherHorizontalLayoutManager(context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {
    var preloadWidth = 0
    private var direction = 1

    init {
        isItemPrefetchEnabled = false
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (dx != 0) direction = if (dx < 0) -1 else 1
        return super.scrollHorizontallyBy(dx, recycler, state)
    }

    override fun scrollToPosition(position: Int) {
        if (position < findFirstVisibleItemPosition()) {
            direction = -1
        } else if (position > findLastVisibleItemPosition()) {
            direction = 1
        }
        super.scrollToPosition(position)
    }

    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = if (direction < 0) preloadWidth else 0
        extraLayoutSpace[1] = if (direction > 0) preloadWidth else 0
    }
}
