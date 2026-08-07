package com.sergioasenjo.ltvlauncher.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.applications.AppRowView
import com.sergioasenjo.ltvlauncher.applications.CategoryManagementActivity
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private data class CategoryRowUi(val view: AppRowView, val adapter: AppAdapter)

    private lateinit var binding: ActivityLauncherBinding
    private val categoryRows = mutableMapOf<Long, CategoryRowUi>()
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshHomeStatus()
    }
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(
            container.applicationRepository,
            container.appPreferencesRepository,
            container.categoryRepository,
            container.homeRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val favoriteAppAdapter = AppAdapter(viewModel::launch, ::showAppActions)
        val tvAppAdapter = AppAdapter(viewModel::launch, ::showAppActions)
        val nonTvAppAdapter = AppAdapter(viewModel::launch, ::showAppActions)
        configureRow(binding.favoriteRow, favoriteAppAdapter, R.string.favorites)
        configureRow(binding.tvRow, tvAppAdapter, R.string.tv_apps)
        configureRow(binding.nonTvRow, nonTvAppAdapter, R.string.non_tv_apps)

        binding.manageHiddenApps.setOnClickListener {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        binding.manageCategories.setOnClickListener {
            startActivity(Intent(this, CategoryManagementActivity::class.java))
        }
        binding.setDefaultLauncher.setOnClickListener { viewModel.requestDefaultLauncher() }
        binding.openSystemSettings.setOnClickListener { viewModel.openSystemSettings() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderHomeStatus(state.isDefaultLauncher)
                        binding.favoriteRow.visibility =
                            if (state.favoriteApps.isEmpty()) View.GONE else View.VISIBLE
                        binding.favoriteRow.appCount.text = appCount(state.favoriteApps.size)
                        binding.tvRow.appCount.text = appCount(state.tvApps.size)
                        binding.nonTvRow.appCount.text = appCount(state.nonTvApps.size)
                        submitApps(
                            binding.favoriteRow.apps,
                            favoriteAppAdapter,
                            state.favoriteApps,
                            requestInitialFocus = !state.loading && state.isDefaultLauncher != false
                        )
                        val categoriesHaveApps = renderCategories(
                            state.categories,
                            requestInitialFocus = !state.loading &&
                                state.isDefaultLauncher != false &&
                                state.favoriteApps.isEmpty()
                        )
                        submitApps(
                            binding.tvRow.apps,
                            tvAppAdapter,
                            state.tvApps,
                            requestInitialFocus = !state.loading &&
                                state.isDefaultLauncher != false &&
                                state.favoriteApps.isEmpty() &&
                                !categoriesHaveApps
                        )
                        submitApps(
                            binding.nonTvRow.apps,
                            nonTvAppAdapter,
                            state.nonTvApps,
                            requestInitialFocus = !state.loading &&
                                state.isDefaultLauncher != false &&
                                state.favoriteApps.isEmpty() &&
                                !categoriesHaveApps &&
                                state.tvApps.isEmpty()
                        )
                        if (!state.loading &&
                            state.favoriteApps.isEmpty() &&
                            state.tvApps.isEmpty() &&
                            state.nonTvApps.isEmpty()
                        ) {
                            binding.manageHiddenApps.post { binding.manageHiddenApps.requestFocus() }
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            LauncherEvent.LaunchFailed -> Toast.makeText(
                                this@LauncherActivity,
                                R.string.launch_failed,
                                Toast.LENGTH_SHORT
                            ).show()

                            LauncherEvent.PreferenceUpdateFailed -> Toast.makeText(
                                this@LauncherActivity,
                                R.string.preference_update_failed,
                                Toast.LENGTH_SHORT
                            ).show()

                            LauncherEvent.CategoryUpdateFailed -> Toast.makeText(
                                this@LauncherActivity,
                                R.string.category_update_failed,
                                Toast.LENGTH_SHORT
                            ).show()

                            is LauncherEvent.OpenIntent -> openIntent(event.intent)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshHomeStatus()
    }

    private fun renderHomeStatus(isDefaultLauncher: Boolean?) {
        binding.homeStatus.setText(
            when (isDefaultLauncher) {
                null -> R.string.home_status_checking
                true -> R.string.home_status_default
                false -> R.string.home_status_not_default
            }
        )
        binding.setDefaultLauncher.visibility =
            if (isDefaultLauncher == false) View.VISIBLE else View.GONE
        if (isDefaultLauncher == false && currentFocus == null) {
            binding.setDefaultLauncher.post { binding.setDefaultLauncher.requestFocus() }
        }
    }

    private fun openIntent(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureRow(appRow: AppRowView, appAdapter: AppAdapter, titleRes: Int) {
        configureRow(appRow, appAdapter, getString(titleRes))
    }

    private fun configureRow(appRow: AppRowView, appAdapter: AppAdapter, title: String) {
        appRow.title.text = title
        appRow.apps.apply {
            layoutManager = LinearLayoutManager(
                this@LauncherActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = appAdapter
            itemAnimator = null
        }
    }

    private fun showAppActions(app: LauncherApp) {
        val favoriteAction = if (app.isFavorite) {
            R.string.remove_from_favorites
        } else {
            R.string.add_to_favorites
        }
        val actions = mutableListOf(
            getString(favoriteAction) to { viewModel.toggleFavorite(app) },
            getString(R.string.hide_app) to { viewModel.setHidden(app, true) }
        )
        viewModel.uiState.value.categories.forEach { category ->
            val included = category.apps.any { it.componentName == app.componentName }
            val label = if (included) {
                getString(R.string.remove_from_category, category.name)
            } else {
                getString(R.string.add_to_category, category.name)
            }
            actions += label to {
                viewModel.setCategoryMembership(category, app, included = !included)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, action ->
                actions[action].second()
            }
            .show()
    }

    private fun renderCategories(categories: List<LauncherCategory>, requestInitialFocus: Boolean): Boolean {
        val categoryIds = categories.mapTo(mutableSetOf(), LauncherCategory::id)
        categoryRows.keys.filterNot(categoryIds::contains).forEach { removedId ->
            categoryRows.remove(removedId)?.let { binding.categoryRows.removeView(it.view) }
        }

        var focusRequested = false
        categories.forEachIndexed { index, category ->
            val row = categoryRows.getOrPut(category.id) {
                val view = AppRowView(this)
                val adapter = AppAdapter(viewModel::launch, ::showAppActions)
                configureRow(view, adapter, category.name)
                CategoryRowUi(view, adapter)
            }
            row.view.title.text = category.name
            row.view.appCount.text = appCount(category.apps.size)
            if (binding.categoryRows.indexOfChild(row.view) != index) {
                binding.categoryRows.removeView(row.view)
                binding.categoryRows.addView(row.view, index)
            }
            val shouldRequestFocus = requestInitialFocus && !focusRequested && category.apps.isNotEmpty()
            submitApps(row.view.apps, row.adapter, category.apps, shouldRequestFocus)
            focusRequested = focusRequested || shouldRequestFocus
        }
        return categories.any { it.apps.isNotEmpty() }
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
                ?.let { focused -> apps.indexOfFirst { it.componentName == focused.componentName } }
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

    private fun appCount(count: Int): String = resources.getQuantityString(R.plurals.application_count, count, count)
}
