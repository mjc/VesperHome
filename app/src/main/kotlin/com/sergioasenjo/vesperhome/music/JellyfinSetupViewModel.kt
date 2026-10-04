package com.sergioasenjo.vesperhome.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.upcoming.UpcomingPlayer
import com.sergioasenjo.vesperhome.upcoming.UpcomingPreferencesRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JellyfinSetupUiState(
    val servers: List<JellyfinServer> = emptyList(),
    val discovering: Boolean = false,
    val pairing: Boolean = false,
    val quickConnectCode: String? = null,
    val connectedServerName: String? = null,
    val normalizationMode: JellyfinNormalizationMode = JellyfinNormalizationMode.OFF,
    val errorRes: Int? = null,
    val serviceConfig: UpcomingServerConfig? = null,
    val savingSonarr: Boolean = false,
    val savingRadarr: Boolean = false,
    val sonarrStatusRes: Int? = null,
    val radarrStatusRes: Int? = null
)

class JellyfinSetupViewModel(
    private val discoveryRepository: JellyfinDiscoveryRepository,
    private val apiRepository: JellyfinApiRepository,
    private val preferencesRepository: JellyfinPreferencesRepository,
    private val upcomingRepository: UpcomingRepository,
    private val upcomingPreferencesRepository: UpcomingPreferencesRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(JellyfinSetupUiState())
    val uiState = mutableUiState.asStateFlow()
    private var pairingJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.credentials.collect { credentials ->
                mutableUiState.value = mutableUiState.value.copy(
                    connectedServerName = credentials?.serverName
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.normalizationMode.collect { mode ->
                mutableUiState.value = mutableUiState.value.copy(normalizationMode = mode)
            }
        }
        viewModelScope.launch {
            upcomingPreferencesRepository.config.collect { config ->
                mutableUiState.value = mutableUiState.value.copy(serviceConfig = config)
            }
        }
    }

    fun discover() {
        pairingJob?.cancel()
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                discovering = true,
                servers = emptyList(),
                quickConnectCode = null,
                errorRes = null
            )
            try {
                val servers = discoveryRepository.discover()
                mutableUiState.value = mutableUiState.value.copy(
                    discovering = false,
                    servers = servers,
                    errorRes = if (servers.isEmpty()) R.string.jellyfin_no_servers_found else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    discovering = false,
                    errorRes = R.string.jellyfin_discovery_failed
                )
            }
        }
    }

    fun connect(server: JellyfinServer) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                servers = emptyList(),
                pairing = true,
                quickConnectCode = null,
                errorRes = null
            )
            try {
                val request = apiRepository.initiateQuickConnect(server)
                mutableUiState.value = mutableUiState.value.copy(quickConnectCode = request.Code)
                repeat(MAX_POLL_ATTEMPTS) {
                    delay(POLL_INTERVAL_MS)
                    val status = apiRepository.checkQuickConnect(server, request.Secret)
                    if (status.Authenticated) {
                        val authentication = apiRepository.authenticate(server, request.Secret)
                        preferencesRepository.save(server, authentication)
                        mutableUiState.value = mutableUiState.value.copy(
                            pairing = false,
                            quickConnectCode = null,
                            connectedServerName = server.Name
                        )
                        return@launch
                    }
                }
                mutableUiState.value = mutableUiState.value.copy(
                    pairing = false,
                    quickConnectCode = null,
                    errorRes = R.string.jellyfin_quick_connect_expired
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    pairing = false,
                    quickConnectCode = null,
                    errorRes = R.string.jellyfin_quick_connect_failed
                )
            }
        }
    }

    fun connectManually(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                discovering = true,
                servers = emptyList(),
                errorRes = null
            )
            try {
                val server = apiRepository.resolveServer(address)
                mutableUiState.value = mutableUiState.value.copy(discovering = false)
                connect(server)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    discovering = false,
                    errorRes = R.string.jellyfin_url_unreachable
                )
            }
        }
    }

    fun disconnect() {
        pairingJob?.cancel()
        viewModelScope.launch {
            preferencesRepository.clear()
            mutableUiState.value = mutableUiState.value.copy(
                pairing = false,
                quickConnectCode = null,
                connectedServerName = null,
                errorRes = null
            )
        }
    }

    fun setNormalizationMode(mode: JellyfinNormalizationMode) {
        viewModelScope.launch { preferencesRepository.setNormalizationMode(mode) }
    }

    fun setUpcomingPlayer(player: UpcomingPlayer) {
        viewModelScope.launch { upcomingPreferencesRepository.setPlayer(player) }
    }

    fun saveSonarr(url: String, apiKey: String) {
        viewModelScope.launch {
            if (url.isBlank() && apiKey.isBlank()) {
                upcomingPreferencesRepository.saveSonarr("", "")
                mutableUiState.value = mutableUiState.value.copy(
                    savingSonarr = false,
                    sonarrStatusRes = R.string.media_service_disabled
                )
                return@launch
            }
            if (url.isBlank() || apiKey.isBlank()) {
                mutableUiState.value = mutableUiState.value.copy(
                    sonarrStatusRes = R.string.media_service_url_key_required
                )
                return@launch
            }
            mutableUiState.value = mutableUiState.value.copy(savingSonarr = true, sonarrStatusRes = null)
            val normalizedUrl = upcomingRepository.normalizeUrl(url)
            val valid = upcomingRepository.validate(normalizedUrl, apiKey.trim(), "Sonarr")
            if (valid) upcomingPreferencesRepository.saveSonarr(normalizedUrl, apiKey.trim())
            mutableUiState.value = mutableUiState.value.copy(
                savingSonarr = false,
                sonarrStatusRes = if (valid) {
                    R.string.media_service_connection_saved
                } else {
                    R.string.sonarr_connection_failed
                }
            )
        }
    }

    fun saveRadarr(url: String, apiKey: String) {
        viewModelScope.launch {
            if (url.isBlank() && apiKey.isBlank()) {
                upcomingPreferencesRepository.saveRadarr("", "")
                mutableUiState.value = mutableUiState.value.copy(
                    savingRadarr = false,
                    radarrStatusRes = R.string.media_service_disabled
                )
                return@launch
            }
            if (url.isBlank() || apiKey.isBlank()) {
                mutableUiState.value = mutableUiState.value.copy(
                    radarrStatusRes = R.string.media_service_url_key_required
                )
                return@launch
            }
            mutableUiState.value = mutableUiState.value.copy(savingRadarr = true, radarrStatusRes = null)
            val normalizedUrl = upcomingRepository.normalizeUrl(url)
            val valid = upcomingRepository.validate(normalizedUrl, apiKey.trim(), "Radarr")
            if (valid) upcomingPreferencesRepository.saveRadarr(normalizedUrl, apiKey.trim())
            mutableUiState.value = mutableUiState.value.copy(
                savingRadarr = false,
                radarrStatusRes = if (valid) {
                    R.string.media_service_connection_saved
                } else {
                    R.string.radarr_connection_failed
                }
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_POLL_ATTEMPTS = 150

        fun factory(
            discoveryRepository: JellyfinDiscoveryRepository,
            apiRepository: JellyfinApiRepository,
            preferencesRepository: JellyfinPreferencesRepository,
            upcomingRepository: UpcomingRepository,
            upcomingPreferencesRepository: UpcomingPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JellyfinSetupViewModel(
                    discoveryRepository,
                    apiRepository,
                    preferencesRepository,
                    upcomingRepository,
                    upcomingPreferencesRepository
                )
            }
        }
    }
}
