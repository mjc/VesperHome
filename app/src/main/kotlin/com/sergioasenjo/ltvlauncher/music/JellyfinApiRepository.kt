package com.sergioasenjo.ltvlauncher.music

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
        val streamUrl = authenticatedUrl(credentials, "Audio/${item.Id}/stream", mapOf("static" to "true"))
        val imageId = if (item.ImageTags["Primary"] != null) item.Id else item.AlbumId
        val imageTag = item.ImageTags["Primary"] ?: item.AlbumPrimaryImageTag
        val artworkUrl = imageId?.let { id -> imageTag?.let { tag -> id to tag } }?.let { (id, tag) ->
            authenticatedUrl(
                credentials,
                "Items/$id/Images/Primary",
                mapOf("tag" to tag, "maxWidth" to "512", "quality" to "90")
            )
        }
        return JellyfinTrack(
            id = item.Id,
            title = item.Name,
            artist = item.Artists.joinToString().ifBlank { item.AlbumArtist.orEmpty() },
            album = item.Album,
            streamUrl = streamUrl,
            artworkUrl = artworkUrl
        )
    }

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
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Jellyfin request failed with ${response.code}")
            json.decodeFromString<T>(response.body.string())
        }
    }

    private suspend fun authorizationHeader(token: String?): String {
        val deviceId = preferencesRepository.deviceId()
        return buildString {
            append("MediaBrowser Client=\"LTvLauncher\", Device=\"Android TV\", ")
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
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
