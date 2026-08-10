package com.sergioasenjo.ltvlauncher.music

import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance
import kotlinx.coroutines.flow.collectLatest

class JellyfinMusicController(
    private val activity: AppCompatActivity,
    private val binding: ViewLauncherContentBinding,
    private val viewModel: JellyfinMusicViewModel,
    private val currentAppearance: () -> LauncherAppearance
) {
    private var collectionDialog: JellyfinCollectionDialog? = null

    init {
        binding.musicLibrary.setOnClickListener { showCollectionDialog() }
        binding.musicPrevious.setOnClickListener { viewModel.playPrevious() }
        binding.musicPlayPause.setOnClickListener { viewModel.playPause() }
        binding.musicNext.setOnClickListener { viewModel.playNext() }
        binding.musicRandom.setOnClickListener { viewModel.random() }
    }

    suspend fun collectState() {
        viewModel.uiState.collectLatest { state ->
            binding.renderJellyfinMusic(activity, state)
            collectionDialog?.render(state.collectionPicker)
            if (state.serverName == null) collectionDialog?.dismiss()
        }
    }

    fun setAppearance(appearance: LauncherAppearance) {
        collectionDialog?.setAppearance(appearance)
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

    private fun getOrCreateCollectionDialog(): JellyfinCollectionDialog = collectionDialog ?: JellyfinCollectionDialog(
        context = activity,
        onCollectionSelected = viewModel::playCollection,
        onRandomSelected = viewModel::playGlobalRandom,
        onRetry = viewModel::loadCollections,
        onDismissed = { binding.musicLibrary.requestFocus() }
    ).also { collectionDialog = it }
}
