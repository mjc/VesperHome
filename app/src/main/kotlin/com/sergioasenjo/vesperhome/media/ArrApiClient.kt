package com.sergioasenjo.vesperhome.media

import com.sergioasenjo.vesperhome.upcoming.UpcomingPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ArrApiClient(
    private val client: OkHttpClient,
    private val json: Json,
    private val preferencesRepository: UpcomingPreferencesRepository
) {
    suspend fun connectedProviders(): Set<MediaProvider> {
        val config = preferencesRepository.config.first()
        return buildSet {
            if (config.sonarrConfigured) add(MediaProvider.SONARR)
            if (config.radarrConfigured) add(MediaProvider.RADARR)
        }
    }

    suspend fun getArray(provider: MediaProvider, path: String, query: Map<String, String> = emptyMap()): JsonArray =
        json.decodeFromString(execute(provider, path, query = query))

    suspend fun getObject(provider: MediaProvider, path: String, query: Map<String, String> = emptyMap()): JsonObject =
        json.decodeFromString(execute(provider, path, query = query))

    suspend fun postObject(provider: MediaProvider, path: String, body: JsonObject): JsonObject =
        json.decodeFromString(execute(provider, path, body = body.toString()))

    private suspend fun execute(
        provider: MediaProvider,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val credentials = credentials(provider)
        val url = credentials.baseUrl.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header(API_KEY_HEADER, credentials.apiKey)
            .apply { if (body != null) post(body.toRequestBody(JSON_MEDIA_TYPE)) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("${provider.name} request failed with ${response.code}")
            response.body.string()
        }
    }

    private suspend fun credentials(provider: MediaProvider): ArrCredentials {
        val config = preferencesRepository.config.first()
        return when (provider) {
            MediaProvider.SONARR -> ArrCredentials(config.sonarrUrl, config.sonarrApiKey)
            MediaProvider.RADARR -> ArrCredentials(config.radarrUrl, config.radarrApiKey)
        }.also { credentials ->
            check(credentials.baseUrl.isNotBlank() && credentials.apiKey.isNotBlank()) {
                "${provider.name} is not configured"
            }
        }
    }

    private data class ArrCredentials(val baseUrl: String, val apiKey: String)

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
