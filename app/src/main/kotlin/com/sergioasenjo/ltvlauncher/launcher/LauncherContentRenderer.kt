package com.sergioasenjo.ltvlauncher.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.applications.AppRowView
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.categories.CategoryLayoutType
import com.sergioasenjo.ltvlauncher.categories.LauncherCategory
import com.sergioasenjo.ltvlauncher.categories.LauncherSection
import com.sergioasenjo.ltvlauncher.categories.LauncherSpacer
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import kotlin.math.ceil

class LauncherContentRenderer(
    private val context: Context,
    private val binding: ViewLauncherContentBinding,
    private val emptyStateFocusTarget: View,
    private val onAppClick: (LauncherApp) -> Unit,
    private val onAppLongClick: (LauncherApp, AppAdapter) -> Unit,
    private val onManualOrderChanged: (List<LauncherApp>) -> Unit,
    private val onCategoryOrderChanged: (Long, List<LauncherApp>) -> Unit
) {
    private data class CategoryRowUi(val view: AppRowView, val adapter: AppAdapter)

    private val sectionViews = mutableMapOf<Long, View>()
    private val categoryRows = mutableMapOf<Long, CategoryRowUi>()
    private val reorderableAdapters = mutableSetOf<AppAdapter>()
    private var appearance = LauncherAppearance()
    private val favoriteAppAdapter = createAdapter(onManualOrderChanged)
    private val appAdapter = createAdapter(onManualOrderChanged)

    init {
        configureRow(binding.favoriteRow, favoriteAppAdapter, context.getString(R.string.favorites))
        configureRow(binding.applicationsRow, appAdapter, context.getString(R.string.applications))
    }

    fun render(state: LauncherUiState) {
        applyAppearance(state.appearance)
        renderBuiltInRows(state)
        val sectionsHaveApps = renderSections(
            state.sections,
            requestInitialFocus = canRequestInitialFocus(state) && state.favoriteApps.isEmpty()
        )
        submitApps(
            binding.applicationsRow.apps,
            appAdapter,
            state.apps,
            canRequestInitialFocus(state) && state.favoriteApps.isEmpty() && !sectionsHaveApps
        )
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
        listOf(favoriteAppAdapter, appAdapter).forEach { adapter ->
            adapter.setAppearance(appearance)
        }
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
        configureAppsLayout(
            binding.favoriteRow,
            favoriteAppAdapter,
            CategoryLayoutType.ROW,
            6,
            BUILT_IN_ROW_HEIGHT_DP,
            state.favoriteApps.size
        )
        configureAppsLayout(
            binding.applicationsRow,
            appAdapter,
            CategoryLayoutType.ROW,
            6,
            BUILT_IN_ROW_HEIGHT_DP,
            state.apps.size
        )
        listOf(favoriteAppAdapter, appAdapter).forEach { adapter ->
            setReorderable(adapter, state.applicationSortMode == ApplicationSortMode.MANUAL)
        }
        submitApps(
            binding.favoriteRow.apps,
            favoriteAppAdapter,
            state.favoriteApps,
            canRequestInitialFocus(state)
        )
    }

    private fun configureRow(appRow: AppRowView, appAdapter: AppAdapter, title: String) {
        appRow.title.text = title
        appRow.apps.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = appAdapter
            itemAnimator = null
        }
        updateRowAppearance(appRow)
    }

    private fun updateRowAppearance(appRow: AppRowView) {
        appRow.categoryHeader.visibility = if (appearance.showCategoryTitles) View.VISIBLE else View.GONE
        appRow.accentTick.backgroundTintList =
            android.content.res.ColorStateList.valueOf(appearance.palette.focus)
        appRow.title.setTextColor(appearance.palette.primaryText)
        appRow.appCount.setTextColor(appearance.palette.focus)
        (appRow.apps.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin =
            if (appearance.showCategoryTitles) dp(CATEGORY_APPS_MARGIN_TOP_DP) else 0
    }

    private fun configureAppsLayout(
        appRow: AppRowView,
        adapter: AppAdapter,
        layoutType: CategoryLayoutType,
        columns: Int,
        rowHeight: Int,
        appCount: Int
    ) {
        val cardHeight = dp(rowHeight + if (appearance.showAppNames) APP_NAME_AREA_HEIGHT_DP else CARD_PADDING_DP)
        val cardWidth: Int
        val recyclerHeight: Int
        if (layoutType == CategoryLayoutType.GRID) {
            val availableWidth = context.resources.displayMetrics.widthPixels - dp(96)
            cardWidth = (availableWidth / columns) - dp(12)
            val gridCardHeight = (cardWidth * 0.68f).toInt().coerceAtLeast(dp(80))
            adapter.setItemSize(cardWidth, gridCardHeight, columns)
            appRow.apps.layoutManager = GridLayoutManager(context, columns)
            recyclerHeight = ceil(appCount.toDouble() / columns).toInt() * (gridCardHeight + dp(12))
        } else {
            cardWidth = dp(rowHeight * 16 / 9)
            adapter.setItemSize(cardWidth, cardHeight)
            appRow.apps.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            recyclerHeight = cardHeight + dp(12)
        }
        appRow.apps.layoutParams = appRow.apps.layoutParams.apply {
            height = recyclerHeight.coerceAtLeast(dp(32))
        }
    }

    private fun renderSections(sections: List<LauncherSection>, requestInitialFocus: Boolean): Boolean {
        val sectionIds = sections.mapTo(mutableSetOf(), LauncherSection::stableId)
        sectionViews.keys.filterNot(sectionIds::contains).forEach { removedId ->
            sectionViews.remove(removedId)?.let(binding.categoryRows::removeView)
            categoryRows.remove(removedId)
        }

        var focusRequested = false
        sections.forEachIndexed { index, section ->
            val view = when (section) {
                is LauncherCategory -> renderCategory(section, requestInitialFocus && !focusRequested).also {
                    focusRequested = focusRequested || (requestInitialFocus && section.apps.isNotEmpty())
                }

                is LauncherSpacer -> sectionViews.getOrPut(section.stableId) { Space(context) }.apply {
                    layoutParams = layoutParams?.apply { height = dp(section.height) }
                        ?: LinearLayout.LayoutParams(1, dp(section.height))
                }

                else -> error("Unsupported launcher section")
            }
            sectionViews[section.stableId] = view
            if (binding.categoryRows.indexOfChild(view) != index) {
                binding.categoryRows.removeView(view)
                binding.categoryRows.addView(view, index)
            }
        }
        return sections.filterIsInstance<LauncherCategory>().any { it.apps.isNotEmpty() }
    }

    private fun renderCategory(category: LauncherCategory, requestInitialFocus: Boolean): AppRowView {
        val row = categoryRows.getOrPut(category.stableId) {
            val adapter = createAdapter { apps -> onCategoryOrderChanged(category.id, apps) }
            val view = AppRowView(context)
            configureRow(view, adapter, category.name)
            CategoryRowUi(view, adapter)
        }
        row.view.title.text = category.name
        row.view.appCount.text = appCount(category.apps.size)
        configureAppsLayout(
            row.view,
            row.adapter,
            category.layoutType,
            category.gridColumns,
            category.rowHeight,
            category.apps.size
        )
        setReorderable(row.adapter, category.sortMode == ApplicationSortMode.MANUAL)
        submitApps(row.view.apps, row.adapter, category.apps, requestInitialFocus)
        return row.view
    }

    private fun setReorderable(adapter: AppAdapter, reorderable: Boolean) {
        if (reorderable) reorderableAdapters += adapter else reorderableAdapters -= adapter
    }

    private fun submitApps(
        recyclerView: RecyclerView,
        appAdapter: AppAdapter,
        apps: List<LauncherApp>,
        requestInitialFocus: Boolean
    ) {
        val hadFocus = recyclerView.hasFocus()
        val focusedPosition = recyclerView.focusedChild?.let(recyclerView::getChildAdapterPosition)
            ?: RecyclerView.NO_POSITION
        val focusedApp = appAdapter.currentList.getOrNull(focusedPosition)
        appAdapter.submitList(apps) {
            if (apps.isEmpty()) return@submitList
            val targetPosition = focusedApp
                ?.let { focused -> apps.indexOfFirst { it.packageName == focused.packageName } }
                ?.takeIf { it >= 0 }
                ?: focusedPosition.coerceIn(0, apps.lastIndex)
            if (hadFocus || (requestInitialFocus && emptyStateFocusTarget.rootView.findFocus() == null)) {
                requestFocus(recyclerView, targetPosition)
            }
        }
    }

    private fun requestFocus(recyclerView: RecyclerView, position: Int) {
        recyclerView.scrollToPosition(position)
        recyclerView.post {
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    private fun canRequestInitialFocus(state: LauncherUiState): Boolean =
        !state.loading && state.isDefaultLauncher != false

    private fun allAppRowsEmpty(state: LauncherUiState): Boolean = state.favoriteApps.isEmpty() &&
        state.apps.isEmpty() &&
        state.categories.all { it.apps.isEmpty() }

    private fun appCount(count: Int): String =
        context.resources.getQuantityString(R.plurals.application_count, count, count)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val BUILT_IN_ROW_HEIGHT_DP = 96
        const val APP_NAME_AREA_HEIGHT_DP = 26
        const val CARD_PADDING_DP = 0
        const val CATEGORY_APPS_MARGIN_TOP_DP = 8
    }
}
