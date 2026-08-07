package com.sergioasenjo.ltvlauncher.launcher

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.applications.AppAdapter
import com.sergioasenjo.ltvlauncher.databinding.ActivityLauncherBinding
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLauncherBinding
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(container.applicationRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appAdapter = AppAdapter(viewModel::launch)
        binding.apps.apply {
            layoutManager = LinearLayoutManager(
                this@LauncherActivity,
                LinearLayoutManager.HORIZONTAL,
                false,
            )
            adapter = appAdapter
            itemAnimator = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.appCount.text = resources.getQuantityString(
                        com.sergioasenjo.ltvlauncher.R.plurals.application_count,
                        state.apps.size,
                        state.apps.size,
                    )
                    appAdapter.submitList(state.apps) {
                        if (!state.loading && state.apps.isNotEmpty() && binding.apps.focusedChild == null) {
                            binding.apps.post { binding.apps.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
    }
}
