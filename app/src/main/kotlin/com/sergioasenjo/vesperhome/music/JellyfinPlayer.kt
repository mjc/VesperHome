package com.sergioasenjo.vesperhome.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class JellyfinPlayer(
    context: Context,
    private val onPlayingChanged: (Boolean) -> Unit,
    private val onTrackChanged: (JellyfinTrack) -> Unit
) {
    private val applicationContext = context.applicationContext
    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.toTrack()?.let(onTrackChanged)
        }
    }

    init {
        val token =
            SessionToken(applicationContext, ComponentName(applicationContext, JellyfinPlaybackService::class.java))
        controllerFuture = MediaController.Builder(applicationContext, token).buildAsync()
        controllerFuture.addListener(
            {
                runCatching(controllerFuture::get).getOrNull()?.let { connectedController ->
                    controller = connectedController
                    connectedController.addListener(listener)
                    onPlayingChanged(connectedController.isPlaying)
                    connectedController.currentMediaItem?.toTrack()?.let(onTrackChanged)
                    pendingAction?.invoke(connectedController)
                    pendingAction = null
                }
            },
            ContextCompat.getMainExecutor(applicationContext)
        )
    }

    fun play(tracks: List<JellyfinTrack>) {
        if (tracks.isEmpty()) return
        withController { player ->
            player.setMediaItems(tracks.map { it.toMediaItem() })
            player.prepare()
            player.play()
        }
    }

    fun toggle() = withController { player -> if (player.isPlaying) player.pause() else player.play() }

    fun playPrevious() = withController(MediaController::seekToPreviousMediaItem)

    fun playNext() = withController(MediaController::seekToNextMediaItem)

    fun playRandom() = withController { player ->
        if (player.mediaItemCount == 0) return@withController
        val candidateIndices = (0 until player.mediaItemCount).filter { it != player.currentMediaItemIndex }
        player.seekToDefaultPosition(candidateIndices.randomOrNull() ?: player.currentMediaItemIndex)
        player.play()
    }

    fun stop() = withController { player ->
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        pendingAction = null
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pendingAction = action }
    }

    private fun JellyfinTrack.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setExtras(
                    Bundle().apply {
                        trackGainDb?.let { putDouble(JellyfinPlaybackMetadata.TRACK_GAIN_DB, it) }
                        albumGainDb?.let { putDouble(JellyfinPlaybackMetadata.ALBUM_GAIN_DB, it) }
                    }
                )
                .build()
        )
        .build()

    private fun MediaItem.toTrack(): JellyfinTrack = JellyfinTrack(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString(),
        streamUrl = localConfiguration?.uri.toString(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
        trackGainDb = mediaMetadata.extras?.gain(JellyfinPlaybackMetadata.TRACK_GAIN_DB),
        albumGainDb = mediaMetadata.extras?.gain(JellyfinPlaybackMetadata.ALBUM_GAIN_DB)
    )

    private fun Bundle.gain(key: String): Double? = getDouble(key).takeIf { containsKey(key) }
}
