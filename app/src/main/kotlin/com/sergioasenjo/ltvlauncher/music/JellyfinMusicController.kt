package com.sergioasenjo.ltvlauncher.music

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import com.sergioasenjo.ltvlauncher.upcoming.UpcomingController
import com.sergioasenjo.ltvlauncher.upcoming.UpcomingMediaItem
import com.sergioasenjo.ltvlauncher.upcoming.UpcomingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class JellyfinMusicController(
    private val activity: AppCompatActivity,
    private val binding: ViewLauncherContentBinding,
    private val viewModel: JellyfinMusicViewModel,
    upcomingRepository: UpcomingRepository,
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
        launch { upcomingController.load() }
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
