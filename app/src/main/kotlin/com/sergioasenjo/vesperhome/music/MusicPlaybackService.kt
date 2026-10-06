package com.sergioasenjo.vesperhome.music

import android.os.Process
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
class MusicPlaybackService :
    MediaSessionService(),
    MediaSession.Callback {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gainProcessor = MusicGainAudioProcessor()
    private var mediaSession: MediaSession? = null
    private var normalizationMode = MusicNormalizationMode.OFF
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyNormalization(mediaItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this, MusicRenderersFactory(this, gainProcessor)).build().apply {
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
        mediaSession = MediaSession.Builder(this, player).setCallback(this).build()
        isRunning = true
        serviceScope.launch {
            MusicPreferencesRepository(this@MusicPlaybackService).normalizationMode.collect { mode ->
                normalizationMode = mode
                applyNormalization(player.currentMediaItem)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession.takeIf { canConnect(controllerInfo) }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult = if (canConnect(controller)) {
        super<MediaSession.Callback>.onConnect(session, controller)
    } else {
        MediaSession.ConnectionResult.reject()
    }

    private fun canConnect(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == Process.myUid() || controller.isTrusted

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
            MusicNormalizationMode.OFF -> null

            MusicNormalizationMode.TRACK -> extras?.gain(MusicPlaybackMetadata.TRACK_GAIN_DB)

            MusicNormalizationMode.ALBUM -> extras?.gain(MusicPlaybackMetadata.ALBUM_GAIN_DB)
                ?: extras?.gain(MusicPlaybackMetadata.TRACK_GAIN_DB)
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
