package com.sergioasenjo.vesperhome.music

import android.app.Application
import android.os.Bundle
import android.os.Process
import androidx.media3.session.MediaSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MusicPlaybackAccessTest {
    @Test
    fun untrustedControllersCannotDiscoverOrConnectToThePlaybackSession() {
        val service = Robolectric.buildService(MusicPlaybackService::class.java).create()
        try {
            val playback = service.get()
            val session = playback.onGetSession(controller(Process.myUid()))!!
            val untrusted = controller(Process.myUid() + 1)
            assertNull(playback.onGetSession(untrusted))
            assertFalse(playback.onConnect(session, untrusted).isAccepted)
        } finally {
            service.destroy()
        }
    }

    @Test
    fun launcherAndAndroidTrustedControllersKeepPlaybackAccess() {
        val service = Robolectric.buildService(MusicPlaybackService::class.java).create()
        try {
            val playback = service.get()
            val ownController = controller(Process.myUid())
            val session = playback.onGetSession(ownController)!!
            assertTrue(playback.onConnect(session, ownController).isAccepted)
            val systemController = controller(Process.SYSTEM_UID, trusted = true)
            assertNotNull(playback.onGetSession(systemController))
            assertTrue(playback.onConnect(session, systemController).isAccepted)
        } finally {
            service.destroy()
        }
    }

    private fun controller(uid: Int, trusted: Boolean = false): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            "com.sergioasenjo.vesperhome.debug",
            Process.myPid(),
            uid,
            1,
            1,
            trusted,
            Bundle().apply { putBoolean("trusted", true) },
            false
        )
}
