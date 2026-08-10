package com.sergioasenjo.ltvlauncher.music

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import coil3.load
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding

fun ViewLauncherContentBinding.renderJellyfinMusic(context: Context, state: JellyfinMusicUiState) {
    val configured = state.serverName != null
    musicLibrary.visibility = if (configured) View.VISIBLE else View.GONE
    musicPlayPause.visibility = if (configured) View.VISIBLE else View.GONE
    musicPrevious.visibility = if (configured && state.collectionPlayback) View.VISIBLE else View.GONE
    musicNext.visibility = if (configured && state.collectionPlayback) View.VISIBLE else View.GONE
    musicRandom.visibility = if (configured) View.VISIBLE else View.GONE
    musicLoading.visibility = if (state.loading) View.VISIBLE else View.GONE
    musicPlayPause.setIconResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
    musicPlayPause.contentDescription = context.getString(if (state.playing) R.string.pause else R.string.play)
    musicTitle.text = state.track?.title ?: context.getString(R.string.jellyfin_music)
    musicArtist.text = when {
        state.errorRes != null -> context.getString(state.errorRes)

        state.track != null -> listOfNotNull(
            state.track.artist.takeIf(String::isNotBlank),
            state.track.album
        ).joinToString(" | ")

        state.serverName != null -> context.getString(R.string.jellyfin_ready, state.serverName)

        else -> context.getString(R.string.jellyfin_not_connected)
    }
    val artworkUrl = state.track?.artworkUrl
    if (artworkUrl == null) {
        val padding = (PLACEHOLDER_PADDING_DP * musicArtwork.resources.displayMetrics.density).toInt()
        musicArtwork.setPadding(padding, padding, padding, padding)
        musicArtwork.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
        musicArtwork.load(R.drawable.ic_music)
    } else {
        musicArtwork.setPadding(0, 0, 0, 0)
        musicArtwork.imageTintList = null
        musicArtwork.load(artworkUrl)
    }
}

private const val PLACEHOLDER_PADDING_DP = 9
