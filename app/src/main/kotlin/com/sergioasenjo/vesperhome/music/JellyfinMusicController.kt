package com.sergioasenjo.vesperhome.music

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.status.NetworkStatusRepository
import com.sergioasenjo.vesperhome.status.NetworkTransport
import com.sergioasenjo.vesperhome.upcoming.UpcomingController
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
    private val upcomingController = UpcomingController(activity, binding, upcomingRepository, ::openInJellyfin)

    init {
        binding.musicLibrary.setOnClickListener { showCollectionDialog() }
        binding.musicPrevious.setOnClickListener { viewModel.playPrevious() }
        binding.musicPlayPause.setOnClickListener { viewModel.playPause() }
        binding.musicNext.setOnClickListener { viewModel.playNext() }
        binding.musicRandom.setOnClickListener { viewModel.random() }
        upcomingController.setAppearance(currentAppearance())
    }

    suspend fun collectState(): Unit = coroutineScope {
        launch {
            networkStatusRepository.observeStatus()
                .map { it.transport != NetworkTransport.NONE }
                .distinctUntilChanged()
                .filter { it }
                .collectLatest { upcomingController.load() }
        }
        viewModel.uiState.collectLatest { state ->
            binding.renderJellyfinMusic(activity, state)
            collectionDialog?.render(state.collectionPicker)
            if (state.serverName == null) collectionDialog?.dismiss()
        }
    }

    fun setAppearance(appearance: LauncherAppearance) {
        collectionDialog?.setAppearance(appearance)
        upcomingController.setAppearance(appearance)
    }

    fun onHostStopped() {
        collectionDialog?.dismiss()
        viewModel.onHostStopped()
    }

    fun release() {
        collectionDialog?.release()
    }

    private fun showCollectionDialog() {
        viewModel.loadCollections()
        getOrCreateCollectionDialog().show(viewModel.uiState.value.collectionPicker, currentAppearance())
    }

    private fun openInJellyfin(item: UpcomingMediaItem) {
        activity.lifecycleScope.launch {
            val itemId = try {
                viewModel.jellyfinItemId(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (itemId == null) {
                Toast.makeText(activity, R.string.upcoming_jellyfin_item_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(itemId)).apply {
                setClassName(JELLYFIN_PACKAGE, JELLYFIN_STARTUP_ACTIVITY)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.upcoming_jellyfin_app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOrCreateCollectionDialog(): JellyfinCollectionDialog = collectionDialog ?: JellyfinCollectionDialog(
        context = activity,
        onCollectionSelected = viewModel::playCollection,
        onRandomSelected = viewModel::playGlobalRandom,
        onRetry = viewModel::loadCollections,
        onDismissed = { binding.musicLibrary.requestFocus() }
    ).also { collectionDialog = it }

    private companion object {
        const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"
        const val JELLYFIN_STARTUP_ACTIVITY = "$JELLYFIN_PACKAGE.ui.startup.StartupActivity"
    }
}
