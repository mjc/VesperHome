package com.sergioasenjo.vesperhome.music

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class MusicProviderPreferencesTest {
    @Test
    fun sharedMusicApiDispatchesPlexBrowsingAndPlaybackToPlexEndpoints() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(
                    """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Song","type":"track",
                    "Media":[{"Part":[{"key":"/library/parts/1/music.mp3"}]}]}]}}""".toResponseBody()
                ).build()
        }.build()
        val api = MusicApiRepository(
            client,
            Json {
                ignoreUnknownKeys = true
            },
            MusicPreferencesRepository(RuntimeEnvironment.getApplication())
        )
        val plex = MusicCredentials("http://plex.test:32400", "plex-token", "", "Plex", MusicProvider.PLEX)
        assertEquals("Song", api.randomTrack(plex).title)
        api.musicCollections(plex)
        assertEquals(
            "Song",
            api.collectionTracks(plex, MusicCollection("a", "Album", 1, null, MusicCollectionType.ALBUM))
                .single().title
        )
        assertEquals(
            listOf("/library/all", "/playlists", "/library/all", "/library/metadata/a/children"),
            requests.map { it.url.encodedPath }
        )
        requests.forEach {
            assertEquals("plex-token", it.header("X-Plex-Token"))
            assertNull(it.header("Authorization"))
        }
    }

    @Test
    fun providerChoicePersistsAndKeepsBothAccountsIndependent() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = MusicPreferencesRepository(context)
        preferences.clear()
        preferences.clearPlex()
        preferences.setMusicProvider(MusicProvider.JELLYFIN)
        assertEquals(MusicProvider.JELLYFIN, preferences.musicProvider.first())
        assertNull(preferences.musicCredentials.first())
        preferences.save(
            JellyfinServer("http://jellyfin.test:8096", "server", "Jellyfin"),
            AuthenticationResult(JellyfinUser("user", "Name"), "jellyfin-token", "server")
        )
        val jellyfin = preferences.credentials.first()!!
        val plex = MusicCredentials("http://plex.test:32400", "plex-token", "", "Plex", MusicProvider.PLEX)
        preferences.savePlex(plex)
        val restored = MusicPreferencesRepository(context)
        assertEquals(MusicProvider.PLEX, restored.musicProvider.first())
        assertEquals(plex, restored.musicCredentials.first())
        assertEquals(jellyfin, restored.credentials.first())
        restored.setMusicProvider(MusicProvider.JELLYFIN)
        assertEquals(jellyfin, restored.musicCredentials.first())
        restored.clear()
        assertNull(restored.musicCredentials.first())
        assertEquals(plex, restored.plexCredentials.first())
        restored.setMusicProvider(MusicProvider.PLEX)
        assertEquals(plex, restored.musicCredentials.first())
        restored.clearPlex()
        assertNull(restored.musicCredentials.first())
    }
}
