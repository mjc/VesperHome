package com.sergioasenjo.ltvlauncher.music

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.databinding.ActivityJellyfinSetupBinding
import kotlinx.coroutines.launch

class JellyfinSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJellyfinSetupBinding
    private val viewModel: JellyfinSetupViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        JellyfinSetupViewModel.factory(
            container.jellyfinDiscoveryRepository,
            container.jellyfinApiRepository,
            container.jellyfinPreferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJellyfinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val serverAdapter = JellyfinServerAdapter(viewModel::connect)
        binding.servers.apply {
            layoutManager = LinearLayoutManager(this@JellyfinSetupActivity)
            adapter = serverAdapter
            itemAnimator = null
        }
        binding.discover.setOnClickListener { viewModel.discover() }
        binding.connectManually.setOnClickListener {
            viewModel.connectManually(binding.serverUrl.text.toString())
        }
        binding.disconnect.setOnClickListener { viewModel.disconnect() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.discover.isEnabled = !state.discovering && !state.pairing
                    binding.connectManually.isEnabled = !state.discovering && !state.pairing
                    binding.progress.isVisible = state.discovering || state.pairing
                    binding.quickConnectCode.isVisible = state.quickConnectCode != null
                    binding.quickConnectCode.text = state.quickConnectCode.orEmpty()
                    binding.quickConnectInstructions.isVisible = state.quickConnectCode != null
                    binding.connectedStatus.isVisible = state.connectedServerName != null
                    binding.connectedStatus.text = state.connectedServerName?.let {
                        getString(com.sergioasenjo.ltvlauncher.R.string.jellyfin_ready, it)
                    }.orEmpty()
                    binding.disconnect.isVisible = state.connectedServerName != null
                    binding.error.isVisible = state.errorRes != null
                    binding.error.text = state.errorRes?.let(::getString).orEmpty()
                    serverAdapter.submitList(state.servers) {
                        if (state.servers.isNotEmpty() && currentFocus == null) {
                            binding.servers.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
