package com.sergioasenjo.ltvlauncher.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Space
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.applications.AppRowView
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.categories.CategoryLayoutType
import com.sergioasenjo.ltvlauncher.categories.CategoryManagementActivity
import com.sergioasenjo.ltvlauncher.categories.LauncherCategory
import com.sergioasenjo.ltvlauncher.categories.LauncherSection
import com.sergioasenjo.ltvlauncher.categories.LauncherSpacer
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import kotlin.math.ceil
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private data class CategoryRowUi(val view: AppRowView, val adapter: AppAdapter)

    private lateinit var binding: ActivityLauncherBinding
    private val sectionViews = mutableMapOf<Long, View>()
    private val categoryRows = mutableMapOf<Long, CategoryRowUi>()
    private val reorderableAdapters = mutableSetOf<AppAdapter>()
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshHomeStatus()
    }
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(
            container.managedApplicationsRepository,
            container.categoryRepository,
            container.homeRepository,
            container.launcherSettingsRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val favoriteAppAdapter = createAdapter(viewModel::setManualAppOrder)
        val tvAppAdapter = createAdapter(viewModel::setManualAppOrder)
        val nonTvAppAdapter = createAdapter(viewModel::setManualAppOrder)
        configureRow(binding.favoriteRow, favoriteAppAdapter, getString(R.string.favorites))
        configureRow(binding.tvRow, tvAppAdapter, getString(R.string.tv_apps))
        configureRow(binding.nonTvRow, nonTvAppAdapter, getString(R.string.non_tv_apps))

        binding.manageHiddenApps.setOnClickListener {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        binding.manageCategories.setOnClickListener {
            startActivity(Intent(this, CategoryManagementActivity::class.java))
        }
        binding.sortApplications.setOnClickListener { showSortDialog() }
        binding.setDefaultLauncher.setOnClickListener { viewModel.requestDefaultLauncher() }
        binding.openSystemSettings.setOnClickListener { viewModel.openSystemSettings() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderHomeStatus(state.isDefaultLauncher)
                        renderBuiltInRows(state, favoriteAppAdapter, tvAppAdapter, nonTvAppAdapter)
                        val sectionsHaveApps = renderSections(
                            state.sections,
                            requestInitialFocus = canRequestInitialFocus(state) && state.favoriteApps.isEmpty()
                        )
                        submitApps(
                            binding.tvRow.apps,
                            tvAppAdapter,
                            state.tvApps,
                            canRequestInitialFocus(state) && state.favoriteApps.isEmpty() && !sectionsHaveApps
                        )
                        submitApps(
                            binding.nonTvRow.apps,
                            nonTvAppAdapter,
                            state.nonTvApps,
                            canRequestInitialFocus(state) &&
                                state.favoriteApps.isEmpty() &&
                                !sectionsHaveApps &&
                                state.tvApps.isEmpty()
                        )
                        if (!state.loading && allAppRowsEmpty(state)) {
                            binding.manageHiddenApps.post { binding.manageHiddenApps.requestFocus() }
                        }
                    }
                }
                launch {
                    viewModel.events.collect(::handleEvent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHomeStatus()
    }

    private fun createAdapter(onManualOrderChanged: (List<LauncherApp>) -> Unit): AppAdapter =
        AppAdapter(viewModel::launch, ::showAppActions, onManualOrderChanged)

    private fun renderBuiltInRows(
        state: LauncherUiState,
        favoriteAdapter: AppAdapter,
        tvAdapter: AppAdapter,
        nonTvAdapter: AppAdapter
    ) {
        binding.favoriteRow.visibility = if (state.favoriteApps.isEmpty()) View.GONE else View.VISIBLE
        binding.favoriteRow.appCount.text = appCount(state.favoriteApps.size)
        binding.tvRow.appCount.text = appCount(state.tvApps.size)
        binding.nonTvRow.appCount.text = appCount(state.nonTvApps.size)
        configureAppsLayout(
            binding.favoriteRow,
            favoriteAdapter,
            CategoryLayoutType.ROW,
            6,
            130,
            state.favoriteApps.size
        )
        configureAppsLayout(binding.tvRow, tvAdapter, CategoryLayoutType.ROW, 6, 130, state.tvApps.size)
        configureAppsLayout(binding.nonTvRow, nonTvAdapter, CategoryLayoutType.ROW, 6, 130, state.nonTvApps.size)
        listOf(favoriteAdapter, tvAdapter, nonTvAdapter).forEach { adapter ->
            setReorderable(adapter, state.applicationSortMode == ApplicationSortMode.MANUAL)
        }
        submitApps(
            binding.favoriteRow.apps,
            favoriteAdapter,
            state.favoriteApps,
            canRequestInitialFocus(state)
        )
    }

    private fun renderHomeStatus(isDefaultLauncher: Boolean?) {
        binding.homeStatus.setText(
            when (isDefaultLauncher) {
                null -> R.string.home_status_checking
                true -> R.string.home_status_default
                false -> R.string.home_status_not_default
            }
        )
        binding.setDefaultLauncher.visibility = if (isDefaultLauncher == false) View.VISIBLE else View.GONE
        if (isDefaultLauncher == false && currentFocus == null) {
            binding.setDefaultLauncher.post { binding.setDefaultLauncher.requestFocus() }
        }
    }

    private fun handleEvent(event: LauncherEvent) {
        when (event) {
            LauncherEvent.LaunchFailed -> showMessage(R.string.launch_failed)
            LauncherEvent.PreferenceUpdateFailed -> showMessage(R.string.preference_update_failed)
            LauncherEvent.CategoryUpdateFailed -> showMessage(R.string.category_update_failed)
            is LauncherEvent.OpenIntent -> openIntent(event.intent)
        }
    }

    private fun openIntent(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.settings_open_failed)
        }
    }

    private fun showSortDialog() {
        val modes = ApplicationSortMode.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_applications)
            .setSingleChoiceItems(
                modes.map { getString(it.labelRes) }.toTypedArray(),
                modes.indexOf(viewModel.uiState.value.applicationSortMode)
            ) { dialog, selection ->
                viewModel.setApplicationSortMode(modes[selection])
                dialog.dismiss()
            }
            .show()
    }

    private fun showAppActions(app: LauncherApp, adapter: AppAdapter) {
        val favoriteAction = if (app.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
        val actions = mutableListOf(
            getString(favoriteAction) to { viewModel.toggleFavorite(app) },
            getString(R.string.hide_app) to { viewModel.setHidden(app, true) },
            getString(R.string.application_info) to { viewModel.openApplicationDetails(app) },
            getString(R.string.uninstall_application) to { viewModel.uninstall(app) }
        )
        if (adapter in reorderableAdapters) {
            actions += getString(R.string.reorder_application) to { adapter.startMoving(app) }
        }
        viewModel.uiState.value.categories.forEach { category ->
            val included = category.apps.any { it.packageName == app.packageName }
            val label = if (included) {
                getString(R.string.remove_from_category, category.name)
            } else {
                getString(R.string.add_to_category, category.name)
            }
            actions += label to { viewModel.setCategoryMembership(category, app, included = !included) }
        }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, action -> actions[action].second() }
            .show()
    }

    private fun configureRow(appRow: AppRowView, appAdapter: AppAdapter, title: String) {
        appRow.title.text = title
        appRow.apps.apply {
            layoutManager = LinearLayoutManager(this@LauncherActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = appAdapter
            itemAnimator = null
        }
    }

    private fun configureAppsLayout(
        appRow: AppRowView,
        adapter: AppAdapter,
        layoutType: CategoryLayoutType,
        columns: Int,
        rowHeight: Int,
        appCount: Int
    ) {
        val cardHeight = dp(rowHeight + 46)
        val cardWidth: Int
        val recyclerHeight: Int
        if (layoutType == CategoryLayoutType.GRID) {
            val availableWidth = resources.displayMetrics.widthPixels - dp(120)
            cardWidth = (availableWidth / columns) - dp(16)
            val gridCardHeight = (cardWidth * 0.72f).toInt().coerceAtLeast(dp(96))
            adapter.setItemSize(cardWidth, gridCardHeight, columns)
            appRow.apps.layoutManager = GridLayoutManager(this, columns)
            recyclerHeight = ceil(appCount.toDouble() / columns).toInt() * (gridCardHeight + dp(16))
        } else {
            cardWidth = dp((rowHeight * 16 / 9) + 12)
            adapter.setItemSize(cardWidth, cardHeight)
            appRow.apps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recyclerHeight = cardHeight + dp(14)
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

                is LauncherSpacer -> sectionViews.getOrPut(section.stableId) { Space(this) }.apply {
                    layoutParams = layoutParams?.apply { height = dp(section.height) }
                        ?: android.widget.LinearLayout.LayoutParams(1, dp(section.height))
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
            val adapter = createAdapter { apps -> viewModel.setCategoryAppOrder(category.id, apps) }
            val view = AppRowView(this)
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
            if (hadFocus || (requestInitialFocus && currentFocus == null)) {
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
        state.tvApps.isEmpty() &&
        state.nonTvApps.isEmpty() &&
        state.categories.all { it.apps.isEmpty() }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun appCount(count: Int): String = resources.getQuantityString(R.plurals.application_count, count, count)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private val ApplicationSortMode.labelRes: Int
    get() = when (this) {
        ApplicationSortMode.MANUAL -> R.string.sort_manual
        ApplicationSortMode.ALPHABETICAL -> R.string.sort_alphabetical
        ApplicationSortMode.LAST_USED -> R.string.sort_last_used
    }
