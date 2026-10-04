package com.sergioasenjo.vesperhome.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.screensaver.DreamStateTracker
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MusicUiState(
    val serverName: String? = null,
    val provider: MusicProvider = MusicProvider.JELLYFIN,
    val track: MusicTrack? = null,
    val playing: Boolean = false,
    val collectionPlayback: Boolean = false,
    val activeCollection: MusicCollection? = null,
    val queue: List<MusicTrack> = emptyList(),
    val loading: Boolean = false,
    val errorRes: Int? = null,
    val collectionPicker: MusicCollectionPickerState = MusicCollectionPickerState()
)

data class MusicCollectionPickerState(
    val collections: List<MusicCollection> = emptyList(),
    val loading: Boolean = false,
    val errorRes: Int? = null
)

class MusicViewModel(
    application: Application,
    private val apiRepository: () -> MusicApiRepository,
    private val preferencesRepository: MusicPreferencesRepository,
    private val dreamStateTracker: DreamStateTracker
) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(MusicUiState())
    val uiState = mutableUiState.asStateFlow()
    private val player = MusicPlayer(
        application,
        onPlayingChanged = { playing -> mutableUiState.value = mutableUiState.value.copy(playing = playing) },
        onTrackChanged = { track -> mutableUiState.value = mutableUiState.value.copy(track = track) }
    )
    private var credentials: MusicCredentials? = null
    private var requestJob: Job? = null
    private var playlistRequestJob: Job? = null
    private var hostStopJob: Job? = null
    private var hostStopped = false

    init {
        viewModelScope.launch {
            preferencesRepository.musicProvider.collect { provider ->
                mutableUiState.value = mutableUiState.value.copy(provider = provider)
            }
        }
        viewModelScope.launch {
            preferencesRepository.musicCredentials.collect { updatedCredentials ->
                val credentialsChanged = credentials != updatedCredentials
                if (credentialsChanged) {
                    requestJob?.cancel()
                    playlistRequestJob?.cancel()
                }
                credentials = updatedCredentials
                if (credentialsChanged) player.stop()
                mutableUiState.value = mutableUiState.value.copy(
                    serverName = updatedCredentials?.serverName,
                    loading = if (credentialsChanged) false else mutableUiState.value.loading,
                    track = if (credentialsChanged) null else mutableUiState.value.track,
                    collectionPlayback = if (credentialsChanged) false else mutableUiState.value.collectionPlayback,
                    activeCollection = if (credentialsChanged) null else mutableUiState.value.activeCollection,
                    queue = if (credentialsChanged) emptyList() else mutableUiState.value.queue,
                    errorRes = null,
                    collectionPicker = if (credentialsChanged) {
                        MusicCollectionPickerState()
                    } else {
                        mutableUiState.value.collectionPicker
                    }
                )
            }
        }
        viewModelScope.launch {
            dreamStateTracker.dreaming.collectLatest { dreaming ->
                if (!dreaming && hostStopped) scheduleHostStop()
            }
        }
    }

    fun playPause() {
        if (mutableUiState.value.track == null) random() else player.toggle()
    }

    fun random() {
        if (mutableUiState.value.collectionPlayback) {
            player.playRandom()
            return
        }
        playGlobalRandom()
    }

    fun playGlobalRandom() {
        val activeCredentials = credentials ?: return
        requestJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            collectionPlayback = false,
            activeCollection = null,
            queue = emptyList()
        )
        requestJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(loading = true, errorRes = null)
            try {
                val track = apiRepository().randomTrack(activeCredentials)
                mutableUiState.value = mutableUiState.value.copy(
                    track = track,
                    collectionPlayback = false,
                    loading = false
                )
                player.play(listOf(track))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    errorRes = R.string.jellyfin_music_load_failed
                )
            }
        }
    }

    fun playPrevious() {
        player.playPrevious()
    }

    fun playNext() {
        player.playNext()
    }

    fun playQueueTrack(track: MusicTrack) {
        val index = mutableUiState.value.queue.indexOfFirst { it.id == track.id }
        if (index >= 0) player.playAt(index)
    }

    suspend fun jellyfinItemId(item: UpcomingMediaItem): String? {
        val activeCredentials = preferencesRepository.credentials.first() ?: return null
        return apiRepository().itemIdByProvider(
            activeCredentials,
            item.providerId.provider.apiName,
            item.providerId.value,
            if (item.type == UpcomingMediaType.EPISODE) "Series" else "Movie"
        )
    }

    fun loadCollections() {
        val activeCredentials = credentials ?: return
        playlistRequestJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            collectionPicker = mutableUiState.value.collectionPicker.copy(loading = true, errorRes = null)
        )
        playlistRequestJob = viewModelScope.launch {
            try {
                val collections = apiRepository().musicCollections(activeCredentials)
                mutableUiState.value = mutableUiState.value.copy(
                    collectionPicker = MusicCollectionPickerState(collections = collections)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    collectionPicker = mutableUiState.value.collectionPicker.copy(
                        loading = false,
                        errorRes = R.string.jellyfin_collection_load_failed
                    )
                )
            }
        }
    }

    fun playCollection(collection: MusicCollection) {
        val activeCredentials = credentials ?: return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(loading = true, errorRes = null)
            try {
                val tracks = apiRepository().collectionTracks(activeCredentials, collection)
                if (tracks.isEmpty()) {
                    mutableUiState.value = mutableUiState.value.copy(
                        loading = false,
                        errorRes = R.string.jellyfin_collection_empty
                    )
                    return@launch
                }
                mutableUiState.value = mutableUiState.value.copy(
                    track = tracks.first(),
                    collectionPlayback = true,
                    activeCollection = collection,
                    queue = tracks,
                    loading = false
                )
                player.play(tracks)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    loading = false,
                    errorRes = R.string.jellyfin_collection_play_failed
                )
            }
        }
    }

    fun onHostStarted() {
        hostStopped = false
        hostStopJob?.cancel()
        hostStopJob = null
        player.onHostStarted()
    }

    fun onHostStopped() {
        hostStopped = true
        scheduleHostStop()
    }

    private fun scheduleHostStop() {
        hostStopJob?.cancel()
        hostStopJob = viewModelScope.launch {
            delay(HOST_STOP_GRACE_PERIOD_MS)
            if (hostStopped && !dreamStateTracker.dreaming.value) stopForHost()
        }
    }

    private fun stopForHost() {
        requestJob?.cancel()
        requestJob = null
        playlistRequestJob?.cancel()
        playlistRequestJob = null
        player.stop()
        mutableUiState.value = mutableUiState.value.copy(
            track = null,
            collectionPlayback = false,
            activeCollection = null,
            queue = emptyList(),
            loading = false,
            collectionPicker = mutableUiState.value.collectionPicker.copy(loading = false)
        )
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        fun factory(
            application: Application,
            apiRepository: () -> MusicApiRepository,
            preferencesRepository: MusicPreferencesRepository,
            dreamStateTracker: DreamStateTracker
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MusicViewModel(application, apiRepository, preferencesRepository, dreamStateTracker) }
        }

        private const val HOST_STOP_GRACE_PERIOD_MS = 1_000L
    }
}
