package com.sergioasenjo.vesperhome.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class JellyfinPlayer(
    context: Context,
    private val onPlayingChanged: (Boolean) -> Unit,
    private val onTrackChanged: (JellyfinTrack) -> Unit
) {
    private val applicationContext = context.applicationContext
    private var player: ExoPlayer? = null

    fun play(tracks: List<JellyfinTrack>) {
        if (tracks.isEmpty()) return
        getOrCreatePlayer().apply {
            setMediaItems(
                tracks.map { track ->
                    MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(track.streamUrl)
                        .setTag(track)
                        .build()
                }
            )
            prepare()
            play()
        }
    }

    fun toggle() {
        player?.run { if (isPlaying) pause() else play() }
    }

    fun playPrevious() {
        player?.seekToPreviousMediaItem()
    }

    fun playNext() {
        player?.seekToNextMediaItem()
    }

    fun playRandom() {
        player?.run {
            if (mediaItemCount == 0) return
            val candidateIndices = (0 until mediaItemCount).filter { it != currentMediaItemIndex }
            seekToDefaultPosition(candidateIndices.randomOrNull() ?: currentMediaItemIndex)
            play()
        }
    }

    fun stop() {
        player?.run {
            stop()
            clearMediaItems()
        }
    }

    fun release() {
        player?.release()
        player = null
    }

    private fun getOrCreatePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(applicationContext).build().apply {
        addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    onPlayingChanged(isPlaying)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    (mediaItem?.localConfiguration?.tag as? JellyfinTrack)?.let(onTrackChanged)
                }
            }
        )
        player = this
    }
}
