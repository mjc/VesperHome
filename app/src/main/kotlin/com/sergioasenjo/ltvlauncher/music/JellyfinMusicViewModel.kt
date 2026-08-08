package com.sergioasenjo.ltvlauncher.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JellyfinMusicUiState(
    val serverName: String? = null,
    val track: JellyfinTrack? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class JellyfinMusicViewModel(
    application: Application,
    private val apiRepository: () -> JellyfinApiRepository,
    preferencesRepository: JellyfinPreferencesRepository
) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(JellyfinMusicUiState())
    val uiState = mutableUiState.asStateFlow()
    private val player = JellyfinPlayer(application) { playing ->
        mutableUiState.value = mutableUiState.value.copy(playing = playing)
    }
    private var credentials: JellyfinCredentials? = null
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.credentials.collect { updatedCredentials ->
                credentials = updatedCredentials
                if (updatedCredentials == null) player.stop()
                mutableUiState.value = mutableUiState.value.copy(
                    serverName = updatedCredentials?.serverName,
                    track = if (updatedCredentials == null) null else mutableUiState.value.track,
                    error = null
                )
            }
        }
    }

    fun playPause() {
        if (mutableUiState.value.track == null) next() else player.toggle()
    }

    fun next() {
        val activeCredentials = credentials ?: return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(loading = true, error = null)
            try {
                val track = apiRepository().randomTrack(activeCredentials)
                mutableUiState.value = mutableUiState.value.copy(track = track, loading = false)
                player.play(track.streamUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    error = "Unable to load music from Jellyfin."
                )
            }
        }
    }

    fun onHostStopped() {
        requestJob?.cancel()
        requestJob = null
        player.stop()
        mutableUiState.value = mutableUiState.value.copy(track = null, loading = false)
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        fun factory(
            application: Application,
            apiRepository: () -> JellyfinApiRepository,
            preferencesRepository: JellyfinPreferencesRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { JellyfinMusicViewModel(application, apiRepository, preferencesRepository) }
        }
    }
}
