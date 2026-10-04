package com.sergioasenjo.vesperhome.music

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Maps Plex music into the library, queue and player already used by the music panel. */
class PlexMusicApiRepository(client: OkHttpClient, private val json: Json) {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    suspend fun resolveServer(address: String, token: String): JellyfinCredentials {
        require(address.isNotBlank() && token.isNotBlank())
        val baseUrl = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }.toHttpUrl().toString().trimEnd('/')
        val credentials = JellyfinCredentials(baseUrl, token.trim(), "", "Plex", MusicProvider.PLEX)
        val server = execute(credentials, "").MediaContainer
        val name = server.friendlyName?.takeIf(String::isNotBlank)
            ?: throw IOException("Plex server identity is missing")
        return credentials.copy(serverName = name)
    }

    suspend fun randomTrack(credentials: JellyfinCredentials): JellyfinTrack {
        val item = execute(
            credentials,
            "library/all",
            mapOf("type" to "10", "sort" to "random", "X-Plex-Container-Size" to "1")
        ).MediaContainer.Metadata.firstOrNull { it.type == "track" }
            ?: throw IOException("The Plex library contains no audio items")
        return item.toTrack(credentials)
    }

    suspend fun musicCollections(credentials: JellyfinCredentials): List<JellyfinMusicCollection> {
        val playlists = items(credentials, "playlists", mapOf("playlistType" to "audio"))
            .filter { it.type == "playlist" && it.playlistType == "audio" }
            .map { it.toCollection(credentials, JellyfinCollectionType.PLAYLIST) }
        val albums = items(credentials, "library/all", mapOf("type" to "9"))
            .filter { it.type == "album" }
            .map { it.toCollection(credentials, JellyfinCollectionType.ALBUM) }
        return (playlists + albums).sortedWith(compareBy(JellyfinMusicCollection::type, { it.name.lowercase() }))
    }

    suspend fun collectionTracks(
        credentials: JellyfinCredentials,
        collection: JellyfinMusicCollection
    ): List<JellyfinTrack> {
        // Keys belong to the selected server. Encode the id as a single path segment.
        val id = credentials.baseUrl.toHttpUrl().newBuilder().addPathSegment(collection.id).build()
            .encodedPath.substringAfterLast('/')
        val path = when (collection.type) {
            JellyfinCollectionType.PLAYLIST -> "playlists/$id/items"
            JellyfinCollectionType.ALBUM -> "library/metadata/$id/children"
        }
        return items(credentials, path).filter { it.type == "track" }.map { it.toTrack(credentials) }
    }

    private suspend fun items(
        credentials: JellyfinCredentials,
        path: String,
        query: Map<String, String> = emptyMap()
    ): List<PlexMusicItem> {
        val result = mutableListOf<PlexMusicItem>()
        var start = 0
        while (true) {
            val page = execute(
                credentials,
                path,
                query +
                    mapOf("X-Plex-Container-Start" to start.toString(), "X-Plex-Container-Size" to PAGE_SIZE.toString())
            ).MediaContainer
            val entries = page.Metadata
            result.addAll(entries)
            start += entries.size
            if (entries.isEmpty() || start >= (page.totalSize ?: page.size ?: entries.size)) break
        }
        return result
    }

    private fun PlexMusicItem.toCollection(credentials: JellyfinCredentials, collectionType: JellyfinCollectionType) =
        JellyfinMusicCollection(
            id = ratingKey,
            name = title,
            trackCount = leafCount ?: 0,
            artworkUrl = (thumb ?: composite)?.let { authenticatedUrl(credentials, it) },
            type = collectionType
        )

    private fun PlexMusicItem.toTrack(credentials: JellyfinCredentials): JellyfinTrack {
        val part = Media.firstOrNull()?.Part?.firstOrNull()
            ?: throw IOException("Plex track has no playable media")
        return JellyfinTrack(
            id = ratingKey,
            title = title,
            artist = originalTitle?.takeIf(String::isNotBlank) ?: grandparentTitle.orEmpty(),
            album = parentTitle,
            streamUrl = authenticatedUrl(credentials, part.key),
            artworkUrl = (thumb ?: parentThumb)?.let { authenticatedUrl(credentials, it) },
            durationMillis = duration
        )
    }

    private suspend fun execute(
        credentials: JellyfinCredentials,
        path: String,
        query: Map<String, String> = emptyMap()
    ): PlexMusicResponse = withContext(Dispatchers.IO) {
        val url = credentials.baseUrl.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Token", credentials.accessToken)
            .header("X-Plex-Product", "Vesper Home")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Plex request failed with ${response.code}")
            json.decodeFromString<PlexMusicResponse>(response.body.string())
        }
    }

    private fun authenticatedUrl(credentials: JellyfinCredentials, path: String): String {
        // Never forward the server token to a URL supplied by metadata on another host.
        require(path.startsWith('/') && !path.startsWith("//") && !path.contains('?') && !path.contains('#'))
        return credentials.baseUrl.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/'))
            .addQueryParameter("X-Plex-Token", credentials.accessToken).build().toString()
    }

    private companion object {
        const val PAGE_SIZE = 200
    }
}

@Serializable
private data class PlexMusicResponse(val MediaContainer: PlexMusicContainer)

@Serializable
private data class PlexMusicContainer(
    val friendlyName: String? = null,
    val size: Int? = null,
    val totalSize: Int? = null,
    val Metadata: List<PlexMusicItem> = emptyList()
)

@Serializable
private data class PlexMusicItem(
    val ratingKey: String,
    val title: String,
    val type: String,
    val playlistType: String? = null,
    val leafCount: Int? = null,
    val thumb: String? = null,
    val composite: String? = null,
    val parentThumb: String? = null,
    val originalTitle: String? = null,
    val grandparentTitle: String? = null,
    val parentTitle: String? = null,
    val duration: Long? = null,
    val Media: List<PlexMusicMedia> = emptyList()
)

@Serializable
private data class PlexMusicMedia(val Part: List<PlexMusicPart> = emptyList())

@Serializable
private data class PlexMusicPart(val key: String)
