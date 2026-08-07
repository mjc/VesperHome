package com.sergioasenjo.ltvlauncher.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherBinding
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(
            container.applicationRepository,
            container.appPreferencesRepository
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.favoriteRow.visibility =
                            if (state.favoriteApps.isEmpty()) View.GONE else View.VISIBLE
                        binding.favoriteRow.appCount.text = appCount(state.favoriteApps.size)
                        binding.tvRow.appCount.text = appCount(state.tvApps.size)
                        binding.nonTvRow.appCount.text = appCount(state.nonTvApps.size)
                        submitApps(
                            binding.favoriteRow.apps,
                            favoriteAppAdapter,
                            state.favoriteApps,
                            requestInitialFocus = !state.loading
                        )
                        submitApps(
                            binding.tvRow.apps,
                            tvAppAdapter,
                            state.tvApps,
                            requestInitialFocus = !state.loading && state.favoriteApps.isEmpty()
                        )
                        submitApps(
                            binding.nonTvRow.apps,
                            nonTvAppAdapter,
                            state.nonTvApps,
                            requestInitialFocus = !state.loading &&
                                state.favoriteApps.isEmpty() &&
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
                        }
                    }
                }
            }
        }
    }

    private fun configureRow(appRow: AppRowView, appAdapter: AppAdapter, titleRes: Int) {
        appRow.title.setText(titleRes)
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
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(
                arrayOf(getString(favoriteAction), getString(R.string.hide_app))
            ) { _, action ->
                when (action) {
                    0 -> viewModel.toggleFavorite(app)
                    1 -> viewModel.setHidden(app, true)
                }
            }
            .show()
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
