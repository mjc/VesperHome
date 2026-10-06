package com.sergioasenjo.vesperhome.music

import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.launcher.handleContainedHorizontalFocus
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.status.NetworkStatusRepository
import com.sergioasenjo.vesperhome.status.NetworkTransport
import com.sergioasenjo.vesperhome.upcoming.PlexItemLauncher
import com.sergioasenjo.vesperhome.upcoming.UpcomingController
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingPlayer
import com.sergioasenjo.vesperhome.upcoming.UpcomingRepository
import java.util.Calendar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MusicController(
    private val activity: AppCompatActivity,
    private val binding: ViewLauncherContentBinding,
    private val viewModel: MusicViewModel,
    private val upcomingRepository: UpcomingRepository,
    private val networkStatusRepository: NetworkStatusRepository,
    private val currentAppearance: () -> LauncherAppearance
) {
    private var collectionDialog: MusicCollectionDialog? = null
    private var queueDialog: MusicQueueDialog? = null
    private val itemLauncher = JellyfinItemLauncher(activity)
    private val plexItemLauncher = PlexItemLauncher(activity)
    private var player = UpcomingPlayer.AUTO
    private val usePlex: Boolean
        get() = player == UpcomingPlayer.PLEX || (player == UpcomingPlayer.AUTO && plexItemLauncher.available)
    private val upcomingController = UpcomingController(
        activity,
        binding,
        upcomingRepository,
        if (usePlex) R.string.upcoming_open_plex else R.string.upcoming_open_jellyfin,
        ::openUpcomingItem
    )

    init {
        binding.musicLibrary.setOnClickListener { showCollectionDialog() }
        binding.musicPrevious.setOnClickListener { viewModel.playPrevious() }
        binding.musicPlayPause.setOnClickListener { viewModel.playPause() }
        binding.musicNext.setOnClickListener { viewModel.playNext() }
        binding.musicRandom.setOnClickListener { viewModel.random() }
        binding.musicArtwork.setOnClickListener { showQueueDialog() }
        upcomingController.setAppearance(currentAppearance())
    }

    suspend fun collectState(): Unit = coroutineScope {
        launch {
            upcomingRepository.player.distinctUntilChanged().collect { selectedPlayer ->
                player = selectedPlayer
                upcomingController.setActionDescription(
                    if (usePlex) R.string.upcoming_open_plex else R.string.upcoming_open_jellyfin
                )
            }
        }
        launch {
            var disconnected = false
            networkStatusRepository.observeStatus()
                .map { it.transport != NetworkTransport.NONE }
                .distinctUntilChanged()
                .collectLatest { connected ->
                    if (connected) {
                        var forceRefresh = disconnected
                        disconnected = false
                        while (true) {
                            upcomingController.load(forceRefresh)
                            forceRefresh = false
                            delay(millisUntilTomorrow())
                        }
                    } else {
                        disconnected = true
                    }
                }
        }
        viewModel.uiState.collectLatest { state ->
            binding.renderMusic(activity, state)
            collectionDialog?.render(state.collectionPicker)
            queueDialog?.takeIf { it.isShowing }?.render(state, currentAppearance())
            if (state.serverName == null) collectionDialog?.dismiss()
            if (state.activeCollection == null) queueDialog?.dismiss()
        }
    }

    fun setAppearance(appearance: LauncherAppearance) {
        collectionDialog?.setAppearance(appearance)
        queueDialog?.takeIf { it.isShowing }?.render(viewModel.uiState.value, appearance)
        upcomingController.setAppearance(appearance)
    }

    fun setSectionVisibility(showMusic: Boolean, showComingNext: Boolean) {
        binding.jellyfinPanel.visibility = if (showMusic) View.VISIBLE else View.GONE
        if (upcomingController.setEnabled(showComingNext)) {
            activity.lifecycleScope.launch { upcomingController.load() }
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean = handleContainedHorizontalFocus(
        event,
        activity.currentFocus,
        listOf(
            binding.musicArtwork,
            binding.musicLibrary,
            binding.musicPrevious,
            binding.musicPlayPause,
            binding.musicNext,
            binding.musicRandom
        ).filter(View::isShown)
    )

    fun onHostStopped() {
        collectionDialog?.dismiss()
        queueDialog?.dismiss()
        viewModel.onHostStopped()
    }

    fun release() {
        collectionDialog?.release()
        queueDialog?.release()
    }

    private fun showCollectionDialog() {
        viewModel.loadCollections()
        getOrCreateCollectionDialog().show(viewModel.uiState.value.collectionPicker, currentAppearance())
    }

    private fun showQueueDialog() {
        val state = viewModel.uiState.value
        if (state.activeCollection == null || state.queue.isEmpty()) return
        getOrCreateQueueDialog().show(state, currentAppearance())
    }

    private fun openUpcomingItem(item: UpcomingMediaItem) {
        if (usePlex) {
            plexItemLauncher.open(item)
        } else {
            itemLauncher.open { viewModel.jellyfinItemId(item) }
        }
    }

    private fun getOrCreateCollectionDialog(): MusicCollectionDialog = collectionDialog ?: MusicCollectionDialog(
        context = activity,
        onCollectionSelected = viewModel::playCollection,
        onRandomSelected = viewModel::playGlobalRandom,
        onRetry = viewModel::loadCollections,
        onDismissed = { binding.musicLibrary.requestFocus() }
    ).also { collectionDialog = it }

    private fun getOrCreateQueueDialog(): MusicQueueDialog = queueDialog ?: MusicQueueDialog(
        context = activity,
        onTrackSelected = viewModel::playQueueTrack,
        onDismissed = {
            if (binding.musicArtwork.isFocusable) {
                binding.musicArtwork.requestFocus()
            } else {
                binding.musicLibrary.requestFocus()
            }
        }
    ).also { queueDialog = it }

    private fun millisUntilTomorrow(): Long {
        val now = System.currentTimeMillis()
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (tomorrow.timeInMillis - now).coerceAtLeast(1L)
    }
}
