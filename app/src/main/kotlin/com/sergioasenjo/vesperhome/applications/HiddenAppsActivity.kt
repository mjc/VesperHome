package com.sergioasenjo.vesperhome.applications

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ActivityHiddenAppsBinding
import com.sergioasenjo.vesperhome.settings.ManagementScreenAppearanceRenderer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HiddenAppsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHiddenAppsBinding
    private var displayedHiddenApps = false
    private val viewModel: HiddenAppsViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        HiddenAppsViewModel.factory(container.managedApplicationsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val container = (application as VesperHomeApplication).container
        val appearanceRenderer = ManagementScreenAppearanceRenderer(
            binding.root,
            binding.wallpaper,
            binding.content,
            listOf(binding.workspace)
        )

        val appAdapter = AppAdapter(
            onAppClick = viewModel::restore,
            onAppLongClick = { app, _ -> viewModel.restore(app) }
        )
        appAdapter.setItemSize(dp(244), dp(176))
        binding.apps.apply {
            layoutManager = LinearLayoutManager(
                this@HiddenAppsActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = appAdapter
            itemAnimator = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        container.launcherSettingsRepository.appearance,
                        container.wallpaperRepository.state
                    ) { appearance, wallpaper -> appearance to wallpaper }.collect { (appearance, wallpaper) ->
                        appAdapter.setAppearance(appearanceRenderer.render(appearance, wallpaper))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        val hiddenApps = state.apps
                        binding.emptyMessage.visibility =
                            if (hiddenApps.isEmpty()) View.VISIBLE else View.GONE
                        appAdapter.submitList(hiddenApps) {
                            if (hiddenApps.isNotEmpty() && currentFocus == null) {
                                binding.apps.post {
                                    binding.apps.findViewHolderForAdapterPosition(0)
                                        ?.itemView
                                        ?.requestFocus()
                                }
                            }
                        }

                        if (hiddenApps.isNotEmpty()) {
                            displayedHiddenApps = true
                        } else if (displayedHiddenApps) {
                            finish()
                        }
                    }
                }
                launch {
                    viewModel.failures.collect {
                        Toast.makeText(
                            this@HiddenAppsActivity,
                            R.string.preference_update_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
