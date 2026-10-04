package com.sergioasenjo.vesperhome.music

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import coil3.load
import coil3.request.ErrorResult
import coil3.result
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.artwork.cacheSizedArtwork
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding

fun ViewLauncherContentBinding.renderJellyfinMusic(context: Context, state: JellyfinMusicUiState) {
    val configured = state.serverName != null
    musicLibrary.visibility = if (configured) View.VISIBLE else View.GONE
    musicPlayPause.visibility = if (configured) View.VISIBLE else View.GONE
    musicPrevious.visibility = if (configured && state.collectionPlayback) View.VISIBLE else View.GONE
    musicNext.visibility = if (configured && state.collectionPlayback) View.VISIBLE else View.GONE
    musicRandom.visibility = if (configured) View.VISIBLE else View.GONE
    musicArtwork.isFocusable = state.activeCollection != null && state.queue.isNotEmpty()
    musicArtwork.isClickable = musicArtwork.isFocusable
    musicArtwork.contentDescription = context.getString(
        if (musicArtwork.isFocusable) R.string.open_current_queue else R.string.music_artwork
    )
    musicLoading.visibility = if (state.loading) View.VISIBLE else View.GONE
    musicPlayPause.setIconResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
    musicPlayPause.contentDescription = context.getString(if (state.playing) R.string.pause else R.string.play)
    musicTitle.text =
        state.track?.title
            ?: context.getString(
                if (state.provider ==
                    MusicProvider.PLEX
                ) {
                    R.string.plex_music
                } else {
                    R.string.jellyfin_music
                }
            )
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
    val artworkData = artworkUrl ?: R.drawable.ic_music
    val reloadArtwork = musicArtwork.getTag(R.id.music_artwork_request_data) != artworkData ||
        musicArtwork.result is ErrorResult
    if (artworkUrl == null) {
        val padding = (PLACEHOLDER_PADDING_DP * musicArtwork.resources.displayMetrics.density).toInt()
        musicArtwork.setPadding(padding, padding, padding, padding)
        musicArtwork.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
    } else {
        musicArtwork.setPadding(0, 0, 0, 0)
        musicArtwork.imageTintList = null
    }
    if (reloadArtwork) {
        musicArtwork.setTag(R.id.music_artwork_request_data, artworkData)
        musicArtwork.load(artworkData) {
            if (artworkUrl != null) cacheSizedArtwork("music")
        }
    }
}

private const val PLACEHOLDER_PADDING_DP = 9
