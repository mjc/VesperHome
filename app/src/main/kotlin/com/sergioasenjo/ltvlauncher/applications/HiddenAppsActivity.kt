package com.sergioasenjo.ltvlauncher.applications

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ActivityHiddenAppsBinding
import kotlinx.coroutines.launch

class HiddenAppsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHiddenAppsBinding
    private var displayedHiddenApps = false
    private val viewModel: HiddenAppsViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        HiddenAppsViewModel.factory(container.managedApplicationsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHiddenAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appAdapter = AppAdapter(
            onAppClick = viewModel::restore,
            onAppLongClick = viewModel::restore
        )
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
}
