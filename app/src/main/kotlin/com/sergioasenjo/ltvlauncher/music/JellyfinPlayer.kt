package com.sergioasenjo.ltvlauncher.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class JellyfinPlayer(context: Context, private val onPlayingChanged: (Boolean) -> Unit) {
    private val applicationContext = context.applicationContext
    private var player: ExoPlayer? = null

    fun play(url: String) {
        getOrCreatePlayer().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    fun toggle() {
        player?.run { if (isPlaying) pause() else play() }
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
            }
        )
        player = this
    }
}
