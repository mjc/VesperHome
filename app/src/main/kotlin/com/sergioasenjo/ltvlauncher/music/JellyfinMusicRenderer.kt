package com.sergioasenjo.ltvlauncher.music

import android.content.Context
import android.view.View
import coil3.load
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ViewLauncherContentBinding

fun ViewLauncherContentBinding.renderJellyfinMusic(context: Context, state: JellyfinMusicUiState) {
    val configured = state.serverName != null
    musicPlayPause.visibility = if (configured) View.VISIBLE else View.GONE
    musicNext.visibility = if (configured) View.VISIBLE else View.GONE
    musicLoading.visibility = if (state.loading) View.VISIBLE else View.GONE
    musicPlayPause.setText(if (state.playing) R.string.pause else R.string.play)
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
        musicArtwork.setImageResource(R.drawable.ic_music)
    } else {
        musicArtwork.load(artworkUrl)
    }
}
