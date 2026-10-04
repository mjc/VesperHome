package com.sergioasenjo.vesperhome.launcher

import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/** A full-width control panel or horizontal row in the vertically recycled content. */
internal class LauncherRowAdapter(val view: View) : RecyclerView.Adapter<LauncherRowAdapter.Holder>() {
    private data class NestedList(val adapter: RecyclerView.Adapter<*>, val state: Parcelable?)
    private val nestedLists = mutableMapOf<RecyclerView, NestedList>()

    init {
        (view.parent as? ViewGroup)?.removeView(view)
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = 1
    override fun getItemId(position: Int): Long = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        FrameLayout(parent.context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams =
                RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (view.parent !== holder.itemView) {
            (view.parent as? ViewGroup)?.removeView(view)
            val params = view.layoutParams
                ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val rowParams = if (params is ViewGroup.MarginLayoutParams) {
                FrameLayout.LayoutParams(params)
            } else {
                FrameLayout.LayoutParams(params)
            }
            (holder.itemView as FrameLayout).addView(view, rowParams)
        }
        nestedLists.forEach { (list, saved) ->
            if (list.adapter == null) {
                list.adapter = saved.adapter
                list.layoutManager?.onRestoreInstanceState(saved.state)
            }
        }
        nestedLists.clear()
    }

    override fun onViewRecycled(holder: Holder) {
        if (view.parent !== holder.itemView) return
        fun releaseLists(child: View) {
            if (child is RecyclerView) {
                child.adapter?.let { nestedLists[child] = NestedList(it, child.layoutManager?.onSaveInstanceState()) }
                child.adapter = null
                child.recycledViewPool.clear()
            } else if (child is ViewGroup) {
                for (index in 0 until child.childCount) releaseLists(child.getChildAt(index))
            }
        }
        releaseLists(view)
        super.onViewRecycled(holder)
    }

    fun setNestedAdapter(list: RecyclerView, adapter: RecyclerView.Adapter<*>?) {
        if (adapter == null) {
            nestedLists.remove(list)
            list.adapter = null
        } else if (nestedLists.containsKey(list)) {
            nestedLists[list] = NestedList(adapter, nestedLists[list]?.state)
        } else if (list.adapter !== adapter) {
            list.adapter = adapter
        }
    }

    /** Explicitly forget a removed row, including any adapter saved while it was offscreen. */
    fun release() {
        nestedLists.clear()
    }

    class Holder(view: FrameLayout) : RecyclerView.ViewHolder(view)
}
