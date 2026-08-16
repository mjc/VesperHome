package com.sergioasenjo.vesperhome.music

import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.launcher.handleContainedHorizontalFocus
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.status.NetworkStatusRepository
import com.sergioasenjo.vesperhome.status.NetworkTransport
import com.sergioasenjo.vesperhome.upcoming.UpcomingController
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingRepository
import java.util.Calendar
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class JellyfinMusicController(
    private val activity: AppCompatActivity,
    private val binding: ViewLauncherContentBinding,
    private val viewModel: JellyfinMusicViewModel,
    upcomingRepository: UpcomingRepository,
    private val networkStatusRepository: NetworkStatusRepository,
    private val currentAppearance: () -> LauncherAppearance
) {
    private var collectionDialog: JellyfinCollectionDialog? = null
    private var queueDialog: JellyfinQueueDialog? = null
    private val itemLauncher = JellyfinItemLauncher(activity)
    private val upcomingController = UpcomingController(activity, binding, upcomingRepository, ::openInJellyfin)

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
            networkStatusRepository.observeStatus()
                .map { it.transport != NetworkTransport.NONE }
                .distinctUntilChanged()
                .collectLatest { connected ->
                    if (connected) {
                        while (true) {
                            upcomingController.load()
                            delay(millisUntilTomorrow())
                        }
                    }
                }
        }
        viewModel.uiState.collectLatest { state ->
            binding.renderJellyfinMusic(activity, state)
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
        )
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

    private fun openInJellyfin(item: UpcomingMediaItem) {
        itemLauncher.open { viewModel.jellyfinItemId(item) }
    }

    private fun getOrCreateCollectionDialog(): JellyfinCollectionDialog = collectionDialog ?: JellyfinCollectionDialog(
        context = activity,
        onCollectionSelected = viewModel::playCollection,
        onRandomSelected = viewModel::playGlobalRandom,
        onRetry = viewModel::loadCollections,
        onDismissed = { binding.musicLibrary.requestFocus() }
    ).also { collectionDialog = it }

    private fun getOrCreateQueueDialog(): JellyfinQueueDialog = queueDialog ?: JellyfinQueueDialog(
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
