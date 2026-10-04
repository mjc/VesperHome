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
    private var controllerFuture: ListenableFuture<MediaController>? = null
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
        onHostStarted()
    }

    fun onHostStarted() {
        if (JellyfinPlaybackService.isRunning) connect()
    }

    private fun connect() {
        if (controller?.isConnected == false) release()
        if (controllerFuture != null) return
        val token =
            SessionToken(applicationContext, ComponentName(applicationContext, JellyfinPlaybackService::class.java))
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture === future) {
                    val connectedController = runCatching(future::get).getOrNull()
                    if (connectedController == null) {
                        controllerFuture = null
                        pendingAction = null
                        return@addListener
                    }
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
        connect()
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

    fun playAt(index: Int) = withController { player ->
        if (index !in 0 until player.mediaItemCount) return@withController
        player.seekToDefaultPosition(index)
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
        val future = controllerFuture
        controllerFuture = null
        future?.let(MediaController::releaseFuture)
    }

    private fun withController(action: (MediaController) -> Unit) {
        if (controllerFuture == null) return
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
                        durationMillis?.let { putLong(JellyfinPlaybackMetadata.DURATION_MS, it) }
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
        durationMillis = mediaMetadata.extras?.long(JellyfinPlaybackMetadata.DURATION_MS),
        trackGainDb = mediaMetadata.extras?.gain(JellyfinPlaybackMetadata.TRACK_GAIN_DB),
        albumGainDb = mediaMetadata.extras?.gain(JellyfinPlaybackMetadata.ALBUM_GAIN_DB)
    )

    private fun Bundle.gain(key: String): Double? = getDouble(key).takeIf { containsKey(key) }

    private fun Bundle.long(key: String): Long? = getLong(key).takeIf { containsKey(key) }
}
