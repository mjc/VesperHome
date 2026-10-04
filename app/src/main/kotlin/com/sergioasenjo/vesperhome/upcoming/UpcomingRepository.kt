package com.sergioasenjo.vesperhome.upcoming

import com.sergioasenjo.vesperhome.http.executeBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class UpcomingRepository(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: Flow<UpcomingServerConfig>
) {
    val player = config.map { it.player }

    private data class CachedUpcoming(
        val config: UpcomingServerConfig,
        val startMillis: Long,
        val fetchedAtMillis: Long,
        val items: List<UpcomingMediaItem>
    )

    private val refreshMutex = Mutex()

    @Volatile
    private var cached: CachedUpcoming? = null

    suspend fun upcoming(
        nowMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false
    ): List<UpcomingMediaItem> {
        val observedCache = cached
        return refreshMutex.withLock {
            val config = config.first()
            if (!config.configured) {
                cached = null
                return@withLock emptyList()
            }
            val startMillis = startOfDay(nowMillis)
            cached?.takeIf {
                it.config == config && it.startMillis == startMillis &&
                    nowMillis - it.fetchedAtMillis in 0 until CACHE_FRESHNESS_MS &&
                    (!forceRefresh || it !== observedCache)
            }?.let { return@withLock it.items }
            cached = null
            val endMillis = startMillis + TimeUnit.DAYS.toMillis(LOOKAHEAD_DAYS)
            supervisorScope {
                val sonarr = async {
                    if (config.sonarrConfigured) {
                        requestSafely { sonarrItems(config, startMillis, endMillis) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                val radarr = async {
                    if (config.radarrConfigured) {
                        requestSafely { radarrItems(config, startMillis, endMillis) }
                    } else {
                        Result.success(emptyList())
                    }
                }
                val sonarrResult = sonarr.await()
                val radarrResult = radarr.await()
                if (sonarrResult.isFailure && radarrResult.isFailure) {
                    throw sonarrResult.exceptionOrNull() ?: IOException("Upcoming calendar requests failed")
                }
                val items = (sonarrResult.getOrDefault(emptyList()) + radarrResult.getOrDefault(emptyList()))
                    .sortedBy(UpcomingMediaItem::startsAtMillis)
                    .take(MAX_ITEMS)
                if (sonarrResult.isSuccess && radarrResult.isSuccess) {
                    cached = CachedUpcoming(config, startMillis, nowMillis, items)
                }
                items
            }
        }
    }

    suspend fun validate(url: String, apiKey: String, expectedApp: String): Boolean = try {
        val status = execute<JsonObject>(normalizeUrl(url), apiKey, "api/v3/system/status")
        status["appName"]?.jsonPrimitive?.content.equals(expectedApp, ignoreCase = true)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private suspend fun sonarrItems(
        config: UpcomingServerConfig,
        startMillis: Long,
        endMillis: Long
    ): List<UpcomingMediaItem> {
        val episodes: List<SonarrEpisode> = executeCalendar(
            config.sonarrUrl,
            config.sonarrApiKey,
            startMillis,
            endMillis,
            mapOf("includeSeries" to "true")
        )
        return episodes.mapNotNull { episode ->
            val startsAt = episode.airDateUtc?.let(::parseApiDate)?.takeIf { it in startMillis..endMillis }
                ?: return@mapNotNull null
            val series = episode.series ?: return@mapNotNull null
            UpcomingMediaItem(
                id = "sonarr:${episode.id}",
                title = series.title,
                detail = "S%02d E%02d | %s".format(
                    Locale.US,
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.title
                ),
                startsAtMillis = startsAt,
                imageUrl = series.images.posterUrl(),
                type = UpcomingMediaType.EPISODE,
                providerId = UpcomingProviderId(UpcomingProvider.TVDB, series.tvdbId)
            )
        }
    }

    private suspend fun radarrItems(
        config: UpcomingServerConfig,
        startMillis: Long,
        endMillis: Long
    ): List<UpcomingMediaItem> {
        val movies: List<RadarrMovie> = executeCalendar(
            config.radarrUrl,
            config.radarrApiKey,
            startMillis,
            endMillis,
            mapOf("includeUnmonitored" to "false")
        )
        return movies.mapNotNull { movie ->
            val release = listOfNotNull(
                movie.inCinemas.toRelease(UpcomingMediaType.CINEMA),
                movie.digitalRelease.toRelease(UpcomingMediaType.DIGITAL),
                movie.physicalRelease.toRelease(UpcomingMediaType.PHYSICAL)
            ).filter { it.first in startMillis..endMillis }.minByOrNull { it.first } ?: return@mapNotNull null
            UpcomingMediaItem(
                id = "radarr:${movie.id}:${release.second}",
                title = movie.title,
                detail = "Movie",
                startsAtMillis = release.first,
                imageUrl = movie.images.posterUrl(),
                type = release.second,
                providerId = UpcomingProviderId(UpcomingProvider.TMDB, movie.tmdbId)
            )
        }
    }

    private suspend inline fun <reified T> executeCalendar(
        baseUrl: String,
        apiKey: String,
        startMillis: Long,
        endMillis: Long,
        query: Map<String, String>
    ): T = execute(
        baseUrl,
        apiKey,
        "api/v3/calendar",
        mapOf("start" to formatApiDate(startMillis), "end" to formatApiDate(endMillis)) + query
    )

    private suspend inline fun <reified T> execute(
        baseUrl: String,
        apiKey: String,
        path: String,
        query: Map<String, String> = emptyMap()
    ): T = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder().url(url).header(API_KEY_HEADER, apiKey).build()
        json.decodeFromString<T>(client.executeBody(request))
    }

    fun normalizeUrl(value: String): String = value.trim().trimEnd('/').let { url ->
        if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    private suspend fun <T> requestSafely(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun String?.toRelease(type: UpcomingMediaType): Pair<Long, UpcomingMediaType>? =
        this?.let(::parseReleaseDate)?.let { it to type }

    private fun List<CalendarImage>.posterUrl(): String? =
        firstOrNull { it.coverType.equals("poster", ignoreCase = true) }?.remoteUrl

    private fun parseApiDate(value: String): Long? = synchronized(API_DATE_FORMAT) {
        runCatching { API_DATE_FORMAT.parse(value)?.time }.getOrNull()
    }

    private fun parseReleaseDate(value: String): Long? = synchronized(RELEASE_DATE_FORMAT) {
        runCatching { RELEASE_DATE_FORMAT.parse(value.take(RELEASE_DATE_LENGTH))?.time }.getOrNull()
    }

    private fun formatApiDate(value: Long): String = synchronized(API_DATE_FORMAT) {
        API_DATE_FORMAT.format(Date(value))
    }

    private fun startOfDay(value: Long): Long = Calendar.getInstance().apply {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        const val LOOKAHEAD_DAYS = 28L
        const val CACHE_FRESHNESS_MS = 5 * 60_000L
        const val MAX_ITEMS = 12
        const val RELEASE_DATE_LENGTH = 10
        val API_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val RELEASE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
