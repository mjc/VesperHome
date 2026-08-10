package com.sergioasenjo.vesperhome.music

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ActivityJellyfinSetupBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class JellyfinSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJellyfinSetupBinding
    private lateinit var appearanceRenderer: MediaServicesAppearanceRenderer
    private var serviceFieldsInitialized = false
    private val viewModel: JellyfinSetupViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        JellyfinSetupViewModel.factory(
            container.jellyfinDiscoveryRepository,
            container.jellyfinApiRepository,
            container.jellyfinPreferencesRepository,
            container.upcomingRepository,
            container.upcomingPreferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJellyfinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appearanceRenderer = MediaServicesAppearanceRenderer(binding)
        val container = (application as VesperHomeApplication).container

        val jellyfin = binding.jellyfinPage
        val serverAdapter = JellyfinServerAdapter(viewModel::connect)
        jellyfin.servers.apply {
            layoutManager = LinearLayoutManager(this@JellyfinSetupActivity)
            adapter = serverAdapter
            itemAnimator = null
        }
        jellyfin.discover.setOnClickListener { viewModel.discover() }
        jellyfin.connectManually.setOnClickListener {
            viewModel.connectManually(jellyfin.serverUrl.text.toString())
        }
        jellyfin.disconnect.setOnClickListener { viewModel.disconnect() }
        binding.sonarrPage.configure(getString(R.string.sonarr), getString(R.string.sonarr_setup_description))
        binding.radarrPage.configure(getString(R.string.radarr), getString(R.string.radarr_setup_description))
        binding.sonarrPage.setOnSaveClick {
            viewModel.saveSonarr(binding.sonarrPage.url, binding.sonarrPage.apiKey)
        }
        binding.radarrPage.setOnSaveClick {
            viewModel.saveRadarr(binding.radarrPage.url, binding.radarrPage.apiKey)
        }
        binding.showJellyfin.setOnClickListener { showPage(ServicePage.JELLYFIN) }
        binding.showSonarr.setOnClickListener { showPage(ServicePage.SONARR) }
        binding.showRadarr.setOnClickListener { showPage(ServicePage.RADARR) }
        showPage(ServicePage.JELLYFIN)
        binding.showJellyfin.post { binding.showJellyfin.requestFocus() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        container.launcherSettingsRepository.appearance,
                        container.wallpaperRepository.state
                    ) { appearance, wallpaper -> appearance to wallpaper }.collect { (appearance, wallpaper) ->
                        serverAdapter.setAppearance(appearanceRenderer.render(appearance, wallpaper))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        jellyfin.discover.isEnabled = !state.discovering && !state.pairing
                        jellyfin.connectManually.isEnabled = !state.discovering && !state.pairing
                        jellyfin.progress.isVisible = state.discovering || state.pairing
                        jellyfin.quickConnectCode.isVisible = state.quickConnectCode != null
                        jellyfin.quickConnectCode.text = state.quickConnectCode.orEmpty()
                        jellyfin.quickConnectInstructions.isVisible = state.quickConnectCode != null
                        jellyfin.connectedStatus.isVisible = state.connectedServerName != null
                        jellyfin.connectedStatus.text = state.connectedServerName?.let {
                            getString(R.string.jellyfin_ready, it)
                        }.orEmpty()
                        jellyfin.disconnect.isVisible = state.connectedServerName != null
                        jellyfin.error.isVisible = state.errorRes != null
                        jellyfin.error.text = state.errorRes?.let(::getString).orEmpty()
                        jellyfin.servers.isVisible = state.servers.isNotEmpty()
                        state.serviceConfig?.takeIf { !serviceFieldsInitialized }?.let { config ->
                            serviceFieldsInitialized = true
                            binding.sonarrPage.setValues(config.sonarrUrl, config.sonarrApiKey)
                            binding.radarrPage.setValues(config.radarrUrl, config.radarrApiKey)
                        }
                        binding.sonarrPage.render(
                            state.savingSonarr,
                            serviceStatus(state.sonarrStatusRes, state.serviceConfig?.sonarrConfigured == true)
                        )
                        binding.radarrPage.render(
                            state.savingRadarr,
                            serviceStatus(state.radarrStatusRes, state.serviceConfig?.radarrConfigured == true)
                        )
                        serverAdapter.submitList(state.servers) {
                            if (state.servers.isNotEmpty() && currentFocus == null) {
                                jellyfin.servers.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showPage(page: ServicePage) {
        binding.jellyfinPage.root.isVisible = page == ServicePage.JELLYFIN
        binding.sonarrPage.isVisible = page == ServicePage.SONARR
        binding.radarrPage.isVisible = page == ServicePage.RADARR
        binding.showJellyfin.isSelected = page == ServicePage.JELLYFIN
        binding.showSonarr.isSelected = page == ServicePage.SONARR
        binding.showRadarr.isSelected = page == ServicePage.RADARR
    }

    private fun serviceStatus(statusRes: Int?, configured: Boolean): String =
        statusRes?.let(::getString) ?: if (configured) getString(R.string.media_service_configured) else ""

    private enum class ServicePage {
        JELLYFIN,
        SONARR,
        RADARR
    }
}
