package com.sergioasenjo.vesperhome.music

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MusicProviderRendererTest {
    @Test
    fun plexUsesTheSameLibraryQueueAndPlaybackControls() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_VesperHome)
        val binding = ViewLauncherContentBinding.inflate(LayoutInflater.from(context))
        val track = JellyfinTrack("track", "Song", "Artist", "Album", "http://plex.test/music", null)
        val collection = JellyfinMusicCollection("album", "Album", 1, null, JellyfinCollectionType.ALBUM)
        val state = JellyfinMusicUiState(
            serverName = "Plex",
            provider = MusicProvider.PLEX,
            track = track,
            playing = true,
            collectionPlayback = true,
            activeCollection = collection,
            queue = listOf(track)
        )
        binding.renderJellyfinMusic(context, state)
        assertEquals("Song", binding.musicTitle.text.toString())
        assertEquals("Artist | Album", binding.musicArtist.text.toString())
        for (control in listOf(
            binding.musicLibrary,
            binding.musicPlayPause,
            binding.musicPrevious,
            binding.musicNext,
            binding.musicRandom
        )) {
            assertEquals(View.VISIBLE, control.visibility)
        }
        assertTrue(binding.musicArtwork.isFocusable)
        assertEquals(context.getString(R.string.pause), binding.musicPlayPause.contentDescription)
        binding.renderJellyfinMusic(context, state.copy(track = null))
        assertEquals(context.getString(R.string.plex_music), binding.musicTitle.text.toString())
    }
}
