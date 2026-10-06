package com.sergioasenjo.vesperhome.music

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ActivityMediaServicesSetupBinding
import com.sergioasenjo.vesperhome.upcoming.PlexItemLauncher
import com.sergioasenjo.vesperhome.upcoming.UpcomingPlayer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MediaServicesSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaServicesSetupBinding
    private lateinit var appearanceRenderer: MediaServicesAppearanceRenderer
    private var serviceFieldsInitialized = false
    private val viewModel: MediaServicesSetupViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        MediaServicesSetupViewModel.factory(
            container.jellyfinDiscoveryRepository,
            container.musicApiRepository,
            container.musicPreferencesRepository,
            container.upcomingRepository,
            container.upcomingPreferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaServicesSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appearanceRenderer = MediaServicesAppearanceRenderer(binding)
        val container = (application as VesperHomeApplication).container

        val jellyfin = binding.jellyfinPage
        val serverAdapter = JellyfinServerAdapter(viewModel::connect)
        jellyfin.servers.apply {
            layoutManager = LinearLayoutManager(this@MediaServicesSetupActivity)
            adapter = serverAdapter
            itemAnimator = null
        }
        jellyfin.discover.setOnClickListener { viewModel.discover() }
        jellyfin.connectManually.setOnClickListener {
            viewModel.connectManually(jellyfin.serverUrl.text.toString())
        }
        jellyfin.disconnect.setOnClickListener { viewModel.disconnect() }
        jellyfin.normalization.setOnClickListener { showNormalizationPicker() }
        binding.plexPage.connectMusic.setOnClickListener {
            viewModel.connectPlex(
                binding.plexPage.serverUrl.text.toString(),
                binding.plexPage.accessToken.text.toString()
            )
        }
        binding.plexPage.disconnectMusic.setOnClickListener { viewModel.disconnectPlex() }
        binding.plexPage.useMusic.setOnClickListener { viewModel.setMusicProvider(MusicProvider.PLEX) }
        jellyfin.useMusic.setOnClickListener { viewModel.setMusicProvider(MusicProvider.JELLYFIN) }
        val plexAvailable = PlexItemLauncher(this).available
        binding.plexPage.usePlex.isEnabled = plexAvailable
        binding.plexPage.usePlex.setOnClickListener { viewModel.setUpcomingPlayer(UpcomingPlayer.PLEX) }
        binding.plexPage.openPlex.setOnClickListener {
            packageManager.getLeanbackLaunchIntentForPackage("com.plexapp.android")?.let(::startActivity)
        }
        binding.plexPage.openPlex.isEnabled = plexAvailable
        jellyfin.useJellyfin.setOnClickListener { viewModel.setUpcomingPlayer(UpcomingPlayer.JELLYFIN) }
        binding.sonarrPage.configure(getString(R.string.sonarr), getString(R.string.sonarr_setup_description))
        binding.radarrPage.configure(getString(R.string.radarr), getString(R.string.radarr_setup_description))
        binding.sonarrPage.setOnSaveClick {
            viewModel.saveSonarr(binding.sonarrPage.url, binding.sonarrPage.apiKey)
        }
        binding.radarrPage.setOnSaveClick {
            viewModel.saveRadarr(binding.radarrPage.url, binding.radarrPage.apiKey)
        }
        binding.showJellyfin.setOnClickListener { showPage(ServicePage.JELLYFIN) }
        binding.showPlex.setOnClickListener { showPage(ServicePage.PLEX) }
        binding.showSonarr.setOnClickListener { showPage(ServicePage.SONARR) }
        binding.showRadarr.setOnClickListener { showPage(ServicePage.RADARR) }
        val initialPage = if (plexAvailable) ServicePage.PLEX else ServicePage.JELLYFIN
        showPage(initialPage)
        (if (plexAvailable) binding.showPlex else binding.showJellyfin).let { it.post { it.requestFocus() } }

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
                        binding.plexPage.connectMusic.isEnabled = !state.connectingPlex
                        binding.plexPage.disconnectMusic.isVisible = state.plexServerName != null
                        binding.plexPage.useMusic.isEnabled = state.plexServerName != null
                        jellyfin.useMusic.isEnabled = state.connectedServerName != null
                        binding.plexPage.useMusic.text = getString(
                            if (state.musicProvider ==
                                MusicProvider.PLEX
                            ) {
                                R.string.music_selected
                            } else {
                                R.string.use_plex_music
                            }
                        )
                        jellyfin.useMusic.text = getString(
                            if (state.musicProvider ==
                                MusicProvider.JELLYFIN
                            ) {
                                R.string.music_selected
                            } else {
                                R.string.use_jellyfin_music
                            }
                        )
                        binding.plexPage.musicStatus.text = when {
                            state.connectingPlex -> getString(R.string.plex_music_connecting)
                            state.plexErrorRes != null -> getString(state.plexErrorRes)
                            state.plexServerName != null -> getString(R.string.jellyfin_ready, state.plexServerName)
                            else -> getString(R.string.plex_music_setup_description)
                        }
                        val plexSelected = state.serviceConfig?.player == UpcomingPlayer.PLEX ||
                            (state.serviceConfig?.player == UpcomingPlayer.AUTO && plexAvailable)
                        binding.plexPage.status.text = getString(
                            when {
                                !plexAvailable -> R.string.upcoming_plex_app_unavailable
                                plexSelected -> R.string.plex_selected
                                else -> R.string.plex_available
                            }
                        )
                        jellyfin.useJellyfin.text = getString(
                            if (plexSelected) R.string.use_jellyfin_coming_next else R.string.jellyfin_selected
                        )
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
                        jellyfin.normalization.text = getString(
                            R.string.jellyfin_normalization_value,
                            getString(state.normalizationMode.labelRes)
                        )
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
        binding.plexPage.root.isVisible = page == ServicePage.PLEX
        binding.jellyfinPage.root.isVisible = page == ServicePage.JELLYFIN
        binding.sonarrPage.isVisible = page == ServicePage.SONARR
        binding.radarrPage.isVisible = page == ServicePage.RADARR
        binding.showJellyfin.isSelected = page == ServicePage.JELLYFIN
        binding.showPlex.isSelected = page == ServicePage.PLEX
        binding.showSonarr.isSelected = page == ServicePage.SONARR
        binding.showRadarr.isSelected = page == ServicePage.RADARR
    }

    private fun serviceStatus(statusRes: Int?, configured: Boolean): String =
        statusRes?.let(::getString) ?: if (configured) getString(R.string.media_service_configured) else ""

    private fun showNormalizationPicker() {
        val modes = MusicNormalizationMode.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.jellyfin_normalization)
            .setSingleChoiceItems(
                modes.map { getString(it.labelRes) }.toTypedArray(),
                modes.indexOf(viewModel.uiState.value.normalizationMode)
            ) { dialog, index ->
                viewModel.setNormalizationMode(modes[index])
                dialog.dismiss()
            }
            .show()
    }

    private enum class ServicePage {
        PLEX,
        JELLYFIN,
        SONARR,
        RADARR
    }
}

private val MusicNormalizationMode.labelRes: Int
    get() = when (this) {
        MusicNormalizationMode.OFF -> R.string.jellyfin_normalization_off
        MusicNormalizationMode.TRACK -> R.string.jellyfin_normalization_track
        MusicNormalizationMode.ALBUM -> R.string.jellyfin_normalization_album
    }
