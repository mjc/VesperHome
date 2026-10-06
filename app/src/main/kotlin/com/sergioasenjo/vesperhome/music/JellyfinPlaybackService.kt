package com.sergioasenjo.vesperhome.music

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class JellyfinPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gainProcessor = JellyfinGainAudioProcessor()
    private var mediaSession: MediaSession? = null
    private var normalizationMode = JellyfinNormalizationMode.OFF
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyNormalization(mediaItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this, JellyfinRenderersFactory(this, gainProcessor)).build().apply {
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).build()
        isRunning = true
        serviceScope.launch {
            JellyfinPreferencesRepository(this@JellyfinPlaybackService).normalizationMode.collect { mode ->
                normalizationMode = mode
                applyNormalization(player.currentMediaItem)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        isRunning = false
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyNormalization(mediaItem: MediaItem?) {
        val extras = mediaItem?.mediaMetadata?.extras
        val gainDb = when (normalizationMode) {
            JellyfinNormalizationMode.OFF -> null

            JellyfinNormalizationMode.TRACK -> extras?.gain(JellyfinPlaybackMetadata.TRACK_GAIN_DB)

            JellyfinNormalizationMode.ALBUM -> extras?.gain(JellyfinPlaybackMetadata.ALBUM_GAIN_DB)
                ?: extras?.gain(JellyfinPlaybackMetadata.TRACK_GAIN_DB)
        }
        gainProcessor.setGainDb(gainDb)
    }

    private fun android.os.Bundle.gain(key: String): Double? = getDouble(key).takeIf { containsKey(key) }

    companion object {
        @Volatile
        internal var isRunning = false
            private set
    }
}
