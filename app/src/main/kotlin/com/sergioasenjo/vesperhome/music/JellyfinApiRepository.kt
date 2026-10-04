package com.sergioasenjo.vesperhome.music

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class JellyfinApiRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val preferencesRepository: JellyfinPreferencesRepository
) {
    private val plex by lazy { PlexMusicApiRepository(client, json) }

    suspend fun resolvePlexServer(address: String, token: String): JellyfinCredentials =
        plex.resolveServer(address, token)

    suspend fun resolveServer(address: String): JellyfinServer {
        val normalizedAddress = address.trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        val info: JellyfinPublicInfo = execute(normalizedAddress, "System/Info/Public")
        return JellyfinServer(Address = normalizedAddress, Id = info.Id, Name = info.ServerName)
    }

    suspend fun initiateQuickConnect(server: JellyfinServer): QuickConnectResult = execute(
        server.baseUrl,
        "QuickConnect/Initiate",
        method = HttpMethod.POST
    )

    suspend fun checkQuickConnect(server: JellyfinServer, secret: String): QuickConnectResult = execute(
        server.baseUrl,
        "QuickConnect/Connect",
        query = mapOf("secret" to secret)
    )

    suspend fun authenticate(server: JellyfinServer, secret: String): AuthenticationResult = execute(
        server.baseUrl,
        "Users/AuthenticateWithQuickConnect",
        method = HttpMethod.POST,
        body = json.encodeToString(QuickConnectRequest(secret))
    )

    suspend fun randomTrack(credentials: JellyfinCredentials): JellyfinTrack {
        if (credentials.provider == MusicProvider.PLEX) return plex.randomTrack(credentials)
        val result: JellyfinItemsResult = execute(
            credentials.baseUrl,
            "Items",
            token = credentials.accessToken,
            query = mapOf(
                "userId" to credentials.userId,
                "includeItemTypes" to "Audio",
                "recursive" to "true",
                "sortBy" to "Random",
                "limit" to "1",
                "enableImages" to "true"
            )
        )
        val item = result.Items.firstOrNull() ?: throw IOException("The Jellyfin library contains no audio items")
        return item.toTrack(credentials)
    }

    suspend fun musicCollections(credentials: JellyfinCredentials): List<JellyfinMusicCollection> {
        if (credentials.provider == MusicProvider.PLEX) return plex.musicCollections(credentials)
        val result: JellyfinLibraryItemsResult = execute(
            credentials.baseUrl,
            "Items",
            token = credentials.accessToken,
            query = mapOf(
                "userId" to credentials.userId,
                "includeItemTypes" to "Playlist,MusicAlbum",
                "recursive" to "true",
                "sortBy" to "SortName",
                "sortOrder" to "Ascending",
                "fields" to "ChildCount,RecursiveItemCount",
                "enableImages" to "true",
                "imageTypeLimit" to "1",
                "enableImageTypes" to "Primary"
            )
        )
        return result.Items.map { item ->
            JellyfinMusicCollection(
                id = item.Id,
                name = item.Name,
                trackCount = item.ChildCount ?: item.RecursiveItemCount ?: 0,
                artworkUrl = item.ImageTags["Primary"]?.let { tag -> imageUrl(credentials, item.Id, tag, 480) },
                type = if (item.Type == "Playlist") JellyfinCollectionType.PLAYLIST else JellyfinCollectionType.ALBUM
            )
        }.sortedWith(compareBy(JellyfinMusicCollection::type, { it.name.lowercase() }))
    }

    suspend fun collectionTracks(
        credentials: JellyfinCredentials,
        collection: JellyfinMusicCollection
    ): List<JellyfinTrack> {
        if (credentials.provider == MusicProvider.PLEX) return plex.collectionTracks(credentials, collection)
        val path: String
        val query: Map<String, String>
        when (collection.type) {
            JellyfinCollectionType.PLAYLIST -> {
                path = "Playlists/${collection.id}/Items"
                query = mediaQuery(credentials)
            }

            JellyfinCollectionType.ALBUM -> {
                path = "Items"
                query = mediaQuery(credentials) + mapOf(
                    "parentId" to collection.id,
                    "includeItemTypes" to "Audio",
                    "recursive" to "true",
                    "sortBy" to "ParentIndexNumber,IndexNumber,SortName",
                    "sortOrder" to "Ascending"
                )
            }
        }
        val result: JellyfinItemsResult = execute(
            credentials.baseUrl,
            path,
            token = credentials.accessToken,
            query = query
        )
        return result.Items
            .filter { it.MediaType == null || it.MediaType.equals("Audio", ignoreCase = true) }
            .map { it.toTrack(credentials) }
    }

    suspend fun itemIdByProvider(
        credentials: JellyfinCredentials,
        provider: String,
        providerId: Int,
        itemType: String
    ): String? {
        val result: JellyfinLibraryItemsResult = execute(
            credentials.baseUrl,
            "Items",
            token = credentials.accessToken,
            query = mapOf(
                "userId" to credentials.userId,
                "recursive" to "true",
                "includeItemTypes" to itemType,
                "fields" to "ProviderIds",
                "enableImages" to "false"
            )
        )
        return result.Items.firstOrNull { item ->
            item.ProviderIds.entries.any { (name, value) ->
                name.equals(provider, ignoreCase = true) && value == providerId.toString()
            }
        }?.Id
    }

    private fun mediaQuery(credentials: JellyfinCredentials): Map<String, String> = mapOf(
        "userId" to credentials.userId,
        "fields" to "PrimaryImageAspectRatio,RunTimeTicks",
        "enableImages" to "true",
        "imageTypeLimit" to "1",
        "enableImageTypes" to "Primary"
    )

    private fun JellyfinAudioItem.toTrack(credentials: JellyfinCredentials): JellyfinTrack {
        val streamUrl = authenticatedUrl(credentials, "Audio/$Id/stream", mapOf("static" to "true"))
        val imageId = if (ImageTags["Primary"] != null) Id else AlbumId
        val imageTag = ImageTags["Primary"] ?: AlbumPrimaryImageTag
        val artworkUrl = imageId?.let { id -> imageTag?.let { tag -> id to tag } }?.let { (id, tag) ->
            imageUrl(credentials, id, tag, 512)
        }
        return JellyfinTrack(
            id = Id,
            title = Name,
            artist = Artists.joinToString().ifBlank { AlbumArtist.orEmpty() },
            album = Album,
            streamUrl = streamUrl,
            artworkUrl = artworkUrl,
            durationMillis = RunTimeTicks?.div(TICKS_PER_MILLISECOND),
            trackGainDb = NormalizationGain,
            albumGainDb = AlbumNormalizationGain
        )
    }

    private fun imageUrl(credentials: JellyfinCredentials, itemId: String, tag: String, maxWidth: Int): String =
        authenticatedUrl(
            credentials,
            "Items/$itemId/Images/Primary",
            mapOf("tag" to tag, "maxWidth" to maxWidth.toString(), "quality" to "90")
        )

    private suspend inline fun <reified T> execute(
        baseUrl: String,
        path: String,
        method: HttpMethod = HttpMethod.GET,
        token: String? = null,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): T = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val requestBody = body.orEmpty().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorizationHeader(token))
            .apply {
                if (method == HttpMethod.POST) post(requestBody)
            }
            .build()
        json.decodeFromString<T>(client.executeBody(request))
    }

    private suspend fun authorizationHeader(token: String?): String {
        val deviceId = preferencesRepository.deviceId()
        return buildString {
            append("MediaBrowser Client=\"Vesper Home\", Device=\"Android TV\", ")
            append("DeviceId=\"").append(deviceId).append("\", Version=\"0.1.0\"")
            if (token != null) append(", Token=\"").append(token).append('"')
        }
    }

    private fun authenticatedUrl(credentials: JellyfinCredentials, path: String, query: Map<String, String>): String =
        credentials.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply {
                query.forEach { (name, value) -> addQueryParameter(name, value) }
                addQueryParameter("api_key", credentials.accessToken)
            }
            .build()
            .toString()

    private enum class HttpMethod {
        GET,
        POST
    }

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
