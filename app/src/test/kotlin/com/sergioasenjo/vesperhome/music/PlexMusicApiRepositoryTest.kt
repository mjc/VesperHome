package com.sergioasenjo.vesperhome.music

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexMusicApiRepositoryTest {
    private val credentials =
        MusicCredentials("http://plex.test:32400", "secret + token", "", "Music", MusicProvider.PLEX)
    private val requests = mutableListOf<Request>()

    private fun repository(
        headers: (Request) -> Map<String, String> = { emptyMap() },
        response: (Request) -> String
    ): PlexMusicApiRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response(request).toResponseBody()).apply {
                    headers(request).forEach { (name, value) -> header(name, value) }
                }.build()
        }.build()
        return PlexMusicApiRepository(client, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun connectsWithHeaderTokenAndPreservesServerIdentity() = runBlocking {
        val api = repository { """{"MediaContainer":{"friendlyName":"My Music","version":"1"}}""" }
        val result = api.resolveServer(" http://plex.test:32400/ ", " token ")
        assertEquals(MusicProvider.PLEX, result.provider)
        assertEquals("My Music", result.serverName)
        assertEquals("http://plex.test:32400", result.baseUrl)
        assertEquals("token", requests.single().header("X-Plex-Token"))
        assertNull(requests.single().url.queryParameter("X-Plex-Token"))
    }

    @Test
    fun randomTrackUsesBoundedQueryAndMapsPlayableAudioAndArtwork() = runBlocking {
        val metadata = TRACK.replace("\"grandparentTitle\"", "\"originalTitle\":\"\",\"grandparentTitle\"")
        val api = repository { """{"MediaContainer":{"Metadata":[$metadata]}}""" }
        val track = api.randomTrack(credentials)
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(123000L, track.durationMillis)
        assertEquals("/library/parts/1/file.flac", track.streamUrl.toHttpUrl().encodedPath)
        assertEquals(credentials.accessToken, track.streamUrl.toHttpUrl().queryParameter("X-Plex-Token"))
        assertTrue(track.artworkUrl!!.contains("/library/metadata/2/thumb/3"))
        assertEquals("10", requests.single().url.queryParameter("type"))
        assertEquals("random", requests.single().url.queryParameter("sort"))
        assertEquals("1", requests.single().url.queryParameter("X-Plex-Container-Size"))
    }

    @Test
    fun pagesCollectionsAndExcludesVideoPlaylists() = runBlocking {
        val api = repository { request ->
            if (request.url.encodedPath == "/playlists") {
                if (request.url.queryParameter("X-Plex-Container-Start") == "0") {
                    """{"MediaContainer":{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"p","title":"Mix","type":"playlist","playlistType":"audio","leafCount":2}]}}"""
                } else {
                    """{"MediaContainer":{"size":1,"totalSize":2,"Metadata":[{"ratingKey":"v","title":"Videos","type":"playlist","playlistType":"video"}]}}"""
                }
            } else {
                """{"MediaContainer":{"size":1,"Metadata":[{"ratingKey":"a","title":"Album","type":"album","leafCount":3}]}}"""
            }
        }
        val collections = api.musicCollections(credentials)
        assertEquals(listOf("p", "a"), collections.map { it.id })
        assertEquals(listOf(2, 3), collections.map { it.trackCount })
        assertEquals("1", requests[1].url.queryParameter("X-Plex-Container-Start"))
        assertFalse(collections.any { it.id == "v" })
    }

    @Test
    fun pagesTracksWhenTotalSizeIsOnlyInResponseHeaders() = runBlocking {
        val api = repository(
            headers = { request ->
                mapOf(
                    "X-Plex-Container-Start" to request.url.queryParameter("X-Plex-Container-Start")!!,
                    "X-Plex-Container-Total-Size" to "201"
                )
            }
        ) { request ->
            val start = request.url.queryParameter("X-Plex-Container-Start")!!.toInt()
            val tracks = (start until minOf(start + 200, 201)).map {
                TRACK.replace("\"t\"", "\"t$it\"")
            }
            """{"MediaContainer":{"size":${tracks.size},"Metadata":[${tracks.joinToString()}]}}"""
        }
        val tracks = api.collectionTracks(
            credentials,
            MusicCollection("p", "Mix", 201, null, MusicCollectionType.PLAYLIST)
        )
        assertEquals((0..200).map { "t$it" }, tracks.map { it.id })
        assertEquals(listOf("0", "200"), requests.map { it.url.queryParameter("X-Plex-Container-Start") })
    }

    @Test
    fun pagesUntilEmptyWhenOnlyOffsetIsKnownEvenIfServerClampsPageSize() = runBlocking {
        for (headerOffset in listOf(false, true)) {
            requests.clear()
            val api = repository(
                headers = { request ->
                    if (headerOffset) {
                        mapOf("X-Plex-Container-Start" to request.url.queryParameter("X-Plex-Container-Start")!!)
                    } else {
                        emptyMap()
                    }
                }
            ) { request ->
                val start = request.url.queryParameter("X-Plex-Container-Start")!!.toInt()
                val offset = if (headerOffset) "" else "\"offset\":$start,"
                val metadata = if (start < 2) TRACK.replace("\"t\"", "\"t$start\"") else ""
                """{"MediaContainer":{$offset"size":${if (start < 2) 1 else 0},"Metadata":[$metadata]}}"""
            }
            val tracks = api.collectionTracks(
                credentials,
                MusicCollection("p", "Mix", 2, null, MusicCollectionType.PLAYLIST)
            )
            assertEquals(listOf("t0", "t1"), tracks.map { it.id })
            assertEquals(listOf("0", "1", "2"), requests.map { it.url.queryParameter("X-Plex-Container-Start") })
        }
    }

    @Test
    fun unpagedResponseIsReadOnceWithoutTreatingSizeAsATotal() = runBlocking {
        val api = repository {
            """{"MediaContainer":{"size":201,"Metadata":[${List(201) { TRACK }.joinToString()}]}}"""
        }
        assertEquals(
            201,
            api.collectionTracks(credentials, MusicCollection("p", "Mix", 201, null, MusicCollectionType.PLAYLIST)).size
        )
        assertEquals(1, requests.size)
    }

    @Test
    fun refusesRepeatedPageOffsetsInsteadOfDuplicatingTracks() {
        val api = repository { """{"MediaContainer":{"offset":0,"Metadata":[$TRACK]}}""" }
        assertThrows(IOException::class.java) {
            runBlocking {
                api.collectionTracks(credentials, MusicCollection("p", "Mix", 2, null, MusicCollectionType.PLAYLIST))
            }
        }
        assertEquals(2, requests.size)
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun includesPlaylistsInsideFoldersUsingFlatAudioListing() = runBlocking {
        val api = repository { request ->
            when {
                request.url.encodedPath != "/playlists" -> """{"MediaContainer":{"Metadata":[]}}"""

                request.url.queryParameter("type") == "15" ->
                    """{"MediaContainer":{"Metadata":[{"ratingKey":"nested","title":"Nested Mix","type":"playlist","playlistType":"audio"}]}}"""

                else -> """{"MediaContainer":{"Metadata":[{"ratingKey":"folder","title":"Music Folder","type":"playlistfolder"}]}}"""
            }
        }
        assertEquals(listOf("Nested Mix"), api.musicCollections(credentials).map { it.name })
        assertEquals("audio", requests.first().url.queryParameter("playlistType"))
    }

    @Test
    fun collectionPlaybackKeepsServerOrderAndFiltersNonAudioEntries() = runBlocking {
        val api = repository {
            """{"MediaContainer":{"Metadata":[$TRACK,{"ratingKey":"video","title":"Movie","type":"movie"},${TRACK.replace(
                "Song",
                "Second"
            ).replace("\"t\"", "\"t2\"")}]}}"""
        }
        val playlist = MusicCollection("p", "Mix", 2, null, MusicCollectionType.PLAYLIST)
        assertEquals(listOf("Song", "Second"), api.collectionTracks(credentials, playlist).map { it.title })
        assertEquals("/playlists/p/items", requests.single().url.encodedPath)
        api.collectionTracks(credentials, playlist.copy(id = "a", type = MusicCollectionType.ALBUM))
        assertEquals("/library/metadata/a/children", requests.last().url.encodedPath)
    }

    @Test
    fun emptyLibraryAndMissingPlayableMediaFailWithoutInventingStreams() {
        val empty = repository { """{"MediaContainer":{}}""" }
        assertThrows(IOException::class.java) { runBlocking { empty.randomTrack(credentials) } }
        val missing =
            repository { """{"MediaContainer":{"Metadata":[{"ratingKey":"t","title":"Song","type":"track"}]}}""" }
        assertThrows(IOException::class.java) { runBlocking { missing.randomTrack(credentials) } }
    }

    @Test
    fun refusesMetadataUrlsThatWouldSendTokenToAnotherHost() {
        for (path in listOf("https://other.test/music", "//other.test/music", "/music?X-Plex-Token=other")) {
            val api =
                repository {
                    """{"MediaContainer":{"Metadata":[${TRACK.replace("/library/parts/1/file.flac", path)}]}}"""
                }
            assertThrows(IllegalArgumentException::class.java) { runBlocking { api.randomTrack(credentials) } }
        }
    }

    private companion object {
        @Suppress("ktlint:standard:max-line-length")
        const val TRACK = """{"ratingKey":"t","title":"Song","type":"track","grandparentTitle":"Artist","parentTitle":"Album","parentThumb":"/library/metadata/2/thumb/3","duration":123000,"Media":[{"Part":[{"key":"/library/parts/1/file.flac"}]}]}"""
    }
}
