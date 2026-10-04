package com.sergioasenjo.vesperhome.launcher

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.AppRowView
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.categories.CategoryLayoutType
import com.sergioasenjo.vesperhome.categories.LauncherCategory
import com.sergioasenjo.vesperhome.categories.LauncherSection
import com.sergioasenjo.vesperhome.categories.LauncherSpacer
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.setSoundEffectsEnabledRecursively

class LauncherContentRenderer(
    private val context: Context,
    private val binding: ViewLauncherContentBinding,
    private val emptyStateFocusTarget: View,
    private val onAppClick: (LauncherApp) -> Unit,
    private val onAppLongClick: (LauncherApp, AppAdapter) -> Unit,
    private val onManualOrderChanged: (List<LauncherApp>) -> Unit,
    private val onCategoryOrderChanged: (Long, List<LauncherApp>) -> Unit
) {
    private data class CategoryRowUi(val view: AppRowView, val adapter: AppAdapter, val row: LauncherRowAdapter)
    private data class FocusedApp(val adapter: AppAdapter, val app: LauncherApp)

    private val sectionRows = mutableMapOf<Long, LauncherRowAdapter>()
    private val categoryRows = mutableMapOf<Long, CategoryRowUi>()
    private val gridColumns = mutableMapOf<AppAdapter, Int>()
    private val attachedViewTypes = mutableMapOf<RecyclerView.Adapter<*>, Int>()
    private val reorderableAdapters = mutableSetOf<AppAdapter>()
    private var appearance = LauncherAppearance()
    private var renderedState: LauncherUiState? = null
    private val favoriteAppAdapter = createAdapter(onManualOrderChanged)
    private val appAdapter = createAdapter(onManualOrderChanged)
    private val musicRow = LauncherRowAdapter(binding.jellyfinPanel)
    private val upcomingRow = LauncherRowAdapter(binding.upcomingSection)
    private val favoriteRow = LauncherRowAdapter(binding.favoriteRow)
    private val applicationsRow = LauncherRowAdapter(binding.applicationsRow)
    private val contentAdapter = ConcatAdapter(
        ConcatAdapter.Config.Builder().setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS).build(),
        musicRow,
        upcomingRow,
        favoriteRow,
        applicationsRow
    )
    private val manager = LauncherGridLayoutManager(context)

    init {
        binding.root.removeView(binding.contentTemplates)
        binding.contentRows.apply {
            layoutManager = manager
            adapter = contentAdapter
            itemAnimator = null
            setItemViewCacheSize(0)
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val holder = getChildViewHolder(view)
                    holder.bindingAdapter?.let { attachedViewTypes[it] = holder.itemViewType }
                }
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: Rect,
                    view: View,
                    parent: RecyclerView,
                    state: RecyclerView.State
                ) {
                    val adapter = parent.getChildViewHolder(view).bindingAdapter
                    if (adapter in gridColumns) outRect.set(0, 0, dp(12), dp(12))
                }
            })
        }
        manager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = contentAdapter.getWrappedAdapterAndPosition(position).first
                return gridColumns[adapter]?.let { manager.spanCount / it } ?: manager.spanCount
            }
        }
        manager.spanSizeLookup.isSpanIndexCacheEnabled = true
        manager.spanSizeLookup.isSpanGroupIndexCacheEnabled = true
        configureRow(binding.favoriteRow, favoriteAppAdapter, context.getString(R.string.favorites))
        configureRow(binding.applicationsRow, appAdapter, context.getString(R.string.applications))
    }

    fun render(state: LauncherUiState) {
        renderedState?.let { previous ->
            if (previous.favoriteApps == state.favoriteApps && previous.apps == state.apps &&
                previous.sections == state.sections && previous.appearance == state.appearance &&
                previous.applicationSortMode == state.applicationSortMode &&
                previous.loading == state.loading && previous.isDefaultLauncher == state.isDefaultLauncher
            ) {
                return
            }
        }
        renderedState = state
        val focused = focusedApp()
        applyAppearance(state.appearance)
        renderBuiltInRows(state)
        renderSections(state.sections)
        val initial = canRequestInitialFocus(state) && emptyStateFocusTarget.rootView.findFocus() == null
        submitApps(binding.favoriteRow.apps, favoriteAppAdapter, state.favoriteApps, initial, focused)
        var focusRequested = state.favoriteApps.isNotEmpty()
        state.sections.filterIsInstance<LauncherCategory>().forEach { category ->
            val row = categoryRows.getValue(category.stableId)
            val list = if (category.layoutType == CategoryLayoutType.GRID) binding.contentRows else row.view.apps
            submitApps(list, row.adapter, category.apps, initial && !focusRequested, focused)
            focusRequested = focusRequested || category.apps.isNotEmpty()
        }
        submitApps(binding.applicationsRow.apps, appAdapter, state.apps, initial && !focusRequested, focused)
        if (!state.loading && allAppRowsEmpty(state) && emptyStateFocusTarget.rootView.findFocus() == null) {
            emptyStateFocusTarget.post { emptyStateFocusTarget.requestFocus() }
        }
    }

    fun isReorderable(adapter: AppAdapter): Boolean = adapter in reorderableAdapters

    private fun createAdapter(onOrderChanged: (List<LauncherApp>) -> Unit): AppAdapter =
        AppAdapter(onAppClick, onAppLongClick, onOrderChanged).apply { setAppearance(appearance) }

    private fun applyAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        listOf(favoriteAppAdapter, appAdapter).forEach { it.setAppearance(appearance) }
        listOf(binding.favoriteRow, binding.applicationsRow).forEach(::updateRowAppearance)
        categoryRows.values.forEach { row ->
            row.adapter.setAppearance(appearance)
            updateRowAppearance(row.view)
        }
    }

    private fun renderBuiltInRows(state: LauncherUiState) {
        binding.favoriteRow.visibility = if (state.favoriteApps.isEmpty()) View.GONE else View.VISIBLE
        binding.favoriteRow.appCount.text = appCount(state.favoriteApps.size)
        binding.applicationsRow.appCount.text = appCount(state.apps.size)
        configureHorizontalLayout(binding.favoriteRow, favoriteAppAdapter, BUILT_IN_ROW_HEIGHT_DP)
        configureHorizontalLayout(binding.applicationsRow, appAdapter, BUILT_IN_ROW_HEIGHT_DP)
        listOf(favoriteAppAdapter, appAdapter).forEach {
            setReorderable(it, state.applicationSortMode == ApplicationSortMode.MANUAL)
        }
    }

    private fun configureRow(appRow: AppRowView, adapter: AppAdapter, title: String) {
        appRow.title.text = title
        appRow.apps.apply {
            layoutManager = LauncherHorizontalLayoutManager(context)
            this.adapter = adapter
            itemAnimator = null
            setItemViewCacheSize(0)
        }
        updateRowAppearance(appRow)
    }

    private fun updateRowAppearance(appRow: AppRowView) {
        appRow.setSoundEffectsEnabledRecursively(appearance.keyClickSounds)
        appRow.categoryHeader.visibility = if (appearance.showCategoryTitles) View.VISIBLE else View.GONE
        appRow.accentTick.backgroundTintList = android.content.res.ColorStateList.valueOf(appearance.palette.focus)
        appRow.title.setTextColor(appearance.palette.primaryText)
        appRow.appCount.setTextColor(appearance.palette.focus)
        (appRow.apps.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            if (appearance.showCategoryTitles) dp(CATEGORY_APPS_MARGIN_TOP_DP) else 0
    }

    private fun configureHorizontalLayout(appRow: AppRowView, adapter: AppAdapter, rowHeight: Int) {
        val width = dp(rowHeight * 16 / 9)
        val height = dp(rowHeight + if (appearance.showAppNames) APP_NAME_AREA_HEIGHT_DP else 0)
        adapter.setItemSize(width, height)
        (appRow.apps.layoutManager as LauncherHorizontalLayoutManager).preloadWidth = width
        if (appRow.apps.layoutParams.height != height + dp(12)) {
            appRow.apps.layoutParams = appRow.apps.layoutParams.apply { this.height = height + dp(12) }
        }
    }

    private fun renderSections(sections: List<LauncherSection>) {
        val previousGridColumns = gridColumns.toMap()
        val sectionIds = sections.mapTo(mutableSetOf(), LauncherSection::stableId)
        sectionRows.keys.filterNot(sectionIds::contains).forEach { id ->
            sectionRows.remove(id)?.release()
            categoryRows.remove(id)?.let { row ->
                reorderableAdapters.remove(row.adapter)
                row.view.apps.adapter = null
            }
        }
        gridColumns.clear()
        val desired = mutableListOf<RecyclerView.Adapter<*>>(musicRow, upcomingRow, favoriteRow)
        var spanCount = 1
        var preloadHeight = dp(BUILT_IN_ROW_HEIGHT_DP + APP_NAME_AREA_HEIGHT_DP + 40)
        var gridPreloadHeight = 0
        for (section in sections) {
            when (section) {
                is LauncherCategory -> {
                    val row = categoryRows.getOrPut(section.stableId) {
                        val id = section.id
                        val adapter = createAdapter { apps -> onCategoryOrderChanged(id, apps) }
                        val view = AppRowView(context)
                        configureRow(view, adapter, section.name)
                        CategoryRowUi(view, adapter, LauncherRowAdapter(view)).also {
                            sectionRows[section.stableId] =
                                it.row
                        }
                    }
                    row.view.title.text = section.name
                    row.view.appCount.text = appCount(section.apps.size)
                    desired += row.row
                    if (section.layoutType == CategoryLayoutType.GRID) {
                        row.row.setNestedAdapter(row.view.apps, null)
                        row.view.apps.visibility = View.GONE
                        val columns = section.gridColumns.coerceIn(1, 10)
                        val width = (context.resources.displayMetrics.widthPixels - dp(96)) / columns - dp(12)
                        val height = (width * 0.68f).toInt().coerceAtLeast(dp(80))
                        row.adapter.setItemSize(width, height, columns)
                        gridColumns[row.adapter] = columns
                        spanCount = lcm(spanCount, columns)
                        gridPreloadHeight = maxOf(gridPreloadHeight, height + dp(12))
                        desired += row.adapter
                    } else {
                        row.view.apps.visibility = View.VISIBLE
                        configureHorizontalLayout(row.view, row.adapter, section.rowHeight)
                        preloadHeight = maxOf(preloadHeight, row.view.apps.layoutParams.height + dp(40))
                    }
                    setReorderable(row.adapter, section.sortMode == ApplicationSortMode.MANUAL)
                }

                is LauncherSpacer -> {
                    val row = sectionRows.getOrPut(section.stableId) { LauncherRowAdapter(Space(context)) }
                    row.view.layoutParams =
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(section.height))
                    desired += row
                }

                else -> error("Unsupported launcher section")
            }
        }
        desired += applicationsRow
        // Keep unchanged child adapters and stable IDs, including during list and appearance updates.
        contentAdapter.adapters.filterNot(desired::contains).forEach(::removeContentAdapter)
        desired.forEachIndexed { index, adapter ->
            if (contentAdapter.adapters.getOrNull(index) !== adapter) {
                removeContentAdapter(adapter)
                contentAdapter.addAdapter(index, adapter)
            }
        }
        categoryRows.values.filter { it.adapter !in gridColumns }.forEach { row ->
            row.row.setNestedAdapter(row.view.apps, row.adapter)
        }
        manager.spanCount = spanCount
        manager.spanSizeLookup.invalidateSpanIndexCache()
        manager.spanSizeLookup.invalidateSpanGroupIndexCache()
        manager.preloadHeight = if (gridPreloadHeight > 0) gridPreloadHeight else preloadHeight
        if (previousGridColumns != gridColumns) binding.contentRows.invalidateItemDecorations()
    }

    private fun removeContentAdapter(adapter: RecyclerView.Adapter<*>) {
        // Isolated view types become unusable after removal; their pooled holders still capture this adapter.
        attachedViewTypes.remove(adapter)?.let { binding.contentRows.recycledViewPool.setMaxRecycledViews(it, 0) }
        contentAdapter.removeAdapter(adapter)
    }

    private fun setReorderable(adapter: AppAdapter, reorderable: Boolean) {
        if (reorderable) reorderableAdapters += adapter else reorderableAdapters -= adapter
    }

    private fun focusedApp(): FocusedApp? {
        var child = binding.root.findFocus() ?: return null
        while (child.parent !is RecyclerView) child = child.parent as? View ?: return null
        val holder = (child.parent as RecyclerView).getChildViewHolder(child)
        val adapter = holder.bindingAdapter as? AppAdapter ?: return null
        return adapter.currentList.getOrNull(holder.bindingAdapterPosition)?.let { FocusedApp(adapter, it) }
    }

    private fun submitApps(
        recyclerView: RecyclerView,
        adapter: AppAdapter,
        apps: List<LauncherApp>,
        initial: Boolean,
        focused: FocusedApp?
    ) {
        val hadFocus = focused?.adapter === adapter
        adapter.submitList(apps) {
            if (apps.isEmpty()) return@submitList
            val targetPosition = if (hadFocus) {
                apps.indexOfFirst {
                    it.componentName == focused.app.componentName && it.user == focused.app.user
                }.coerceAtLeast(0)
            } else {
                0
            }
            if (hadFocus || (initial && emptyStateFocusTarget.rootView.findFocus() == null)) {
                val target = recyclerView.absolutePosition(adapter, targetPosition)
                if (recyclerView.findViewHolderForAdapterPosition(target)?.itemView?.hasFocus() != true) {
                    requestFocus(recyclerView, adapter, targetPosition)
                }
            }
        }
    }

    private fun requestFocus(list: RecyclerView, adapter: AppAdapter, position: Int) {
        if (list !== binding.contentRows) {
            val rowAdapter = if (adapter ===
                favoriteAppAdapter
            ) {
                favoriteRow
            } else if (adapter === appAdapter) {
                applicationsRow
            } else {
                categoryRows.values.firstOrNull { it.adapter === adapter }?.row ?: return
            }
            binding.contentRows.scrollToPosition(binding.contentRows.absolutePosition(rowAdapter, 0))
        }
        list.post {
            val target = list.absolutePosition(adapter, position)
            if (target != RecyclerView.NO_POSITION) {
                list.scrollToPosition(target)
                list.post { list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
            }
        }
    }

    private fun canRequestInitialFocus(state: LauncherUiState): Boolean =
        !state.loading && state.isDefaultLauncher != false
    private fun allAppRowsEmpty(state: LauncherUiState): Boolean =
        state.favoriteApps.isEmpty() && state.apps.isEmpty() && state.categories.all { it.apps.isEmpty() }
    private fun appCount(count: Int): String =
        context.resources.getQuantityString(R.plurals.application_count, count, count)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun lcm(a: Int, b: Int): Int {
        fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)
        return a / gcd(a, b) * b
    }

    private companion object {
        const val BUILT_IN_ROW_HEIGHT_DP = 96
        const val APP_NAME_AREA_HEIGHT_DP = 26
        const val CATEGORY_APPS_MARGIN_TOP_DP = 8
    }
}
