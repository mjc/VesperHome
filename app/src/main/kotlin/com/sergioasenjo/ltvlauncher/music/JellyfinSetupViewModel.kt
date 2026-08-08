package com.sergioasenjo.ltvlauncher.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.ltvlauncher.R
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
    val errorRes: Int? = null
)

class JellyfinSetupViewModel(
    private val discoveryRepository: JellyfinDiscoveryRepository,
    private val apiRepository: JellyfinApiRepository,
    private val preferencesRepository: JellyfinPreferencesRepository
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

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_POLL_ATTEMPTS = 150

        fun factory(
            discoveryRepository: JellyfinDiscoveryRepository,
            apiRepository: JellyfinApiRepository,
            preferencesRepository: JellyfinPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { JellyfinSetupViewModel(discoveryRepository, apiRepository, preferencesRepository) }
        }
    }
}
