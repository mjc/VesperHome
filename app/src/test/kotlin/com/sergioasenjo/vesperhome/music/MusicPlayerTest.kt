package com.sergioasenjo.vesperhome.music

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MusicPlayerTest {
    private val application = RuntimeEnvironment.getApplication()
    private val shadowApplication = shadowOf(application)
    private val tracks = listOf(MusicTrack("track", "Title", "Artist", null, "https://music.test/track", null))

    @Before
    fun configureServiceBinding() {
        shadowApplication.setComponentNameAndServiceForBindService(
            ComponentName(application, MusicPlaybackService::class.java),
            Binder()
        )
    }

    @Test
    fun idleLifecycleAndControlsDoNotBindThePlaybackService() {
        val player = MusicPlayer(application, {}, {})
        player.onHostStarted()
        player.toggle()
        player.playPrevious()
        player.playNext()
        player.playRandom()
        player.playAt(0)
        player.play(emptyList())
        player.stop()
        player.release()
        assertTrue(shadowApplication.boundServiceConnections.isEmpty())
        assertTrue(shadowApplication.unboundServiceConnections.isEmpty())
        assertFalse(MusicPlaybackService.isRunning)
    }

    @Test
    fun firstPlayBindsOnceAndReleaseCancelsThePendingConnection() {
        val player = MusicPlayer(application, {}, {})
        player.play(tracks)
        assertEquals(1, shadowApplication.boundServiceConnections.size)
        player.play(tracks)
        player.onHostStarted()
        player.stop()
        assertEquals(1, shadowApplication.boundServiceConnections.size)
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadowApplication.boundServiceConnections.isEmpty())
        assertEquals(1, shadowApplication.unboundServiceConnections.size)
    }

    @Test
    fun playPauseRetriesTheSelectedQueueAfterConnectionFailure() {
        val player = MusicPlayer(application, {}, {})
        val queue = tracks + tracks.first().copy(id = "second")
        shadowApplication.setThrowInBindService(SecurityException("Playback service unavailable"))
        player.play(queue)
        shadowOf(Looper.getMainLooper()).idle()
        shadowApplication.setThrowInBindService(null)

        withPlaybackSession { session ->
            assertEquals(0, session.player.mediaItemCount)
            player.toggle()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("track", "second"), session.mediaIds())
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun hostStartResubmitsTheSelectedTrackAfterConnectionFailure() {
        val player = MusicPlayer(application, {}, {})
        shadowApplication.setThrowInBindService(SecurityException("Playback service unavailable"))
        player.play(tracks)
        shadowOf(Looper.getMainLooper()).idle()
        shadowApplication.setThrowInBindService(null)

        withPlaybackSession { session ->
            player.onHostStarted()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("track"), session.mediaIds())
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun repeatedFailuresAndRetryClicksKeepOnlyTheLatestSelection() {
        val player = MusicPlayer(application, {}, {})
        shadowApplication.setThrowInBindService(SecurityException("Playback service unavailable"))
        player.play(tracks)
        shadowOf(Looper.getMainLooper()).idle()
        player.play(listOf(tracks.first().copy(id = "latest")))
        shadowOf(Looper.getMainLooper()).idle()
        shadowApplication.setThrowInBindService(null)

        withPlaybackSession { session ->
            val connectionsBeforeRetry = shadowApplication.boundServiceConnections.size
            player.toggle()
            player.toggle()
            player.play(listOf(tracks.first().copy(id = "newest")))
            assertEquals(connectionsBeforeRetry + 1, shadowApplication.boundServiceConnections.size)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("newest"), session.mediaIds())
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun stoppingAfterFailureDiscardsTheRetryableTrack() {
        val player = MusicPlayer(application, {}, {})
        shadowApplication.setThrowInBindService(SecurityException("Playback service unavailable"))
        player.play(tracks)
        shadowOf(Looper.getMainLooper()).idle()
        shadowApplication.setThrowInBindService(null)
        player.stop()

        withPlaybackSession { session ->
            player.onHostStarted()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(session.mediaIds().isEmpty())
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun releasingAnInFlightRetryIgnoresItsCallbackAndDiscardsTheOldTrack() {
        val player = MusicPlayer(application, {}, {})
        shadowApplication.setThrowInBindService(SecurityException("Playback service unavailable"))
        player.play(tracks)
        shadowOf(Looper.getMainLooper()).idle()
        shadowApplication.setThrowInBindService(null)

        withPlaybackSession { session ->
            player.toggle()
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(session.mediaIds().isEmpty())
            val connectionsAfterRelease = shadowApplication.boundServiceConnections.size
            player.toggle()
            assertEquals(connectionsAfterRelease, shadowApplication.boundServiceConnections.size)
            player.play(listOf(tracks.first().copy(id = "fresh")))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("fresh"), session.mediaIds())
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun resumedAndRecreatedPlayersReconnectToAnExistingService() {
        var restoredTrack: MusicTrack? = null
        val player = MusicPlayer(application, {}, { restoredTrack = it })
        val service = Robolectric.buildService(MusicPlaybackService::class.java).create()
        try {
            val playbackService = service.get()
            shadowApplication.setComponentNameAndServiceForBindService(
                ComponentName(application, MusicPlaybackService::class.java),
                playbackService.onBind(Intent(MediaSessionService.SERVICE_INTERFACE))
            )
            val session = playbackService.onGetSession(
                MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                    application.packageName,
                    Process.myPid(),
                    Process.myUid(),
                    1,
                    1,
                    false,
                    Bundle.EMPTY,
                    true
                )
            )!!
            session.player.setMediaItems(
                listOf(
                    MediaItem.Builder().setMediaId("restored").setUri("file:///restored.mp3").setMediaMetadata(
                        MediaMetadata.Builder().setTitle("Restored title").build()
                    ).build()
                )
            )
            assertTrue(MusicPlaybackService.isRunning)
            player.onHostStarted()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, shadowApplication.boundServiceConnections.size)
            assertEquals("restored", restoredTrack?.id)
            assertEquals("Restored title", restoredTrack?.title)
            var recreatedTrack: MusicTrack? = null
            val recreated = MusicPlayer(application, {}, { recreatedTrack = it })
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, shadowApplication.boundServiceConnections.size)
            assertEquals("restored", recreatedTrack?.id)
            recreated.stop()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, session.player.mediaItemCount)
            recreated.release()
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(shadowApplication.boundServiceConnections.isEmpty())
        } finally {
            service.destroy()
        }
        assertFalse(MusicPlaybackService.isRunning)
    }

    private fun withPlaybackSession(block: (MediaSession) -> Unit) {
        val service = Robolectric.buildService(MusicPlaybackService::class.java).create()
        try {
            val playbackService = service.get()
            shadowApplication.setComponentNameAndServiceForBindService(
                ComponentName(application, MusicPlaybackService::class.java),
                playbackService.onBind(Intent(MediaSessionService.SERVICE_INTERFACE))
            )
            val session = playbackService.onGetSession(
                MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                    application.packageName,
                    Process.myPid(),
                    Process.myUid(),
                    1,
                    1,
                    false,
                    Bundle.EMPTY,
                    true
                )
            )!!
            block(session)
        } finally {
            service.destroy()
        }
    }

    private fun MediaSession.mediaIds(): List<String> =
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
}
