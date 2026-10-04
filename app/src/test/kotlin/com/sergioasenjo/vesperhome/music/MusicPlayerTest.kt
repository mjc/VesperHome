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
class JellyfinPlayerTest {
    private val application = RuntimeEnvironment.getApplication()
    private val shadowApplication = shadowOf(application)
    private val tracks = listOf(JellyfinTrack("track", "Title", "Artist", null, "https://music.test/track", null))

    @Before
    fun configureServiceBinding() {
        shadowApplication.setComponentNameAndServiceForBindService(
            ComponentName(application, JellyfinPlaybackService::class.java),
            Binder()
        )
    }

    @Test
    fun idleLifecycleAndControlsDoNotBindThePlaybackService() {
        val player = JellyfinPlayer(application, {}, {})
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
        assertFalse(JellyfinPlaybackService.isRunning)
    }

    @Test
    fun firstPlayBindsOnceAndReleaseCancelsThePendingConnection() {
        val player = JellyfinPlayer(application, {}, {})
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
    fun resumedAndRecreatedPlayersReconnectToAnExistingService() {
        var restoredTrack: JellyfinTrack? = null
        val player = JellyfinPlayer(application, {}, { restoredTrack = it })
        val service = Robolectric.buildService(JellyfinPlaybackService::class.java).create()
        try {
            val playbackService = service.get()
            shadowApplication.setComponentNameAndServiceForBindService(
                ComponentName(application, JellyfinPlaybackService::class.java),
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
            assertTrue(JellyfinPlaybackService.isRunning)
            player.onHostStarted()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, shadowApplication.boundServiceConnections.size)
            assertEquals("restored", restoredTrack?.id)
            assertEquals("Restored title", restoredTrack?.title)
            var recreatedTrack: JellyfinTrack? = null
            val recreated = JellyfinPlayer(application, {}, { recreatedTrack = it })
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
        assertFalse(JellyfinPlaybackService.isRunning)
    }
}
