package com.sergioasenjo.vesperhome.media

import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class TrackedMediaRepository(private val api: ArrApiClient, private val dao: TrackedMediaDao) {
    val items: Flow<List<TrackedMedia>> = dao.observeAll().map { entities -> entities.map(TrackedMediaEntity::toModel) }

    suspend fun refresh() {
        dao.getAll().forEach { entity ->
            try {
                dao.upsert(refresh(entity.toModel()).toEntity())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {}
        }
    }

    private suspend fun refresh(item: TrackedMedia): TrackedMedia {
        val providerItemId = item.providerItemId ?: return item.copy(state = TrackedMediaState.FAILED)
        val queue = api.getArray(
            item.provider,
            "api/v3/queue/details",
            mapOf(item.provider.queueIdName to providerItemId.toString())
        )
        return when (item.provider) {
            MediaProvider.SONARR -> refreshSeries(item, providerItemId, queue.map { it.jsonObject })
            MediaProvider.RADARR -> refreshMovie(item, providerItemId, queue.map { it.jsonObject })
        }
    }

    private suspend fun refreshSeries(item: TrackedMedia, providerItemId: Int, queue: List<JsonObject>): TrackedMedia {
        api.getObject(MediaProvider.SONARR, "api/v3/series/$providerItemId")
        val episodes = api.getArray(
            MediaProvider.SONARR,
            "api/v3/episode",
            mapOf("seriesId" to providerItemId.toString())
        )
        val monitored = episodes.map { it.jsonObject }.filter {
            it["monitored"]?.jsonPrimitive?.booleanOrNull == true
        }
        val available = monitored.count { it["hasFile"]?.jsonPrimitive?.booleanOrNull == true }
        val queuedProgress = queue.sumOf(::itemProgress)
        val calculatedProgress = if (monitored.isNotEmpty()) {
            (((available + queuedProgress) / monitored.size) * 100).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val previousAggregateProgress = if (item.detail.isNotBlank()) item.progress ?: 0 else 0
        val progress = maxOf(previousAggregateProgress, calculatedProgress)
        return item.copy(
            state = when {
                available == monitored.size && monitored.isNotEmpty() -> TrackedMediaState.AVAILABLE
                queue.any(::isDownloading) -> TrackedMediaState.DOWNLOADING
                queue.isNotEmpty() -> TrackedMediaState.QUEUED
                else -> stateWithoutQueue(item, available > 0)
            },
            detail = if (monitored.isEmpty()) "" else "$available / ${monitored.size}",
            progress = progress
        )
    }

    private suspend fun refreshMovie(item: TrackedMedia, providerItemId: Int, queue: List<JsonObject>): TrackedMedia {
        val movie = api.getObject(MediaProvider.RADARR, "api/v3/movie/$providerItemId")
        val available = movie["hasFile"]?.jsonPrimitive?.booleanOrNull == true
        val calculatedProgress = queue.firstOrNull()?.let { (itemProgress(it) * 100).roundToInt() }
        val progress = calculatedProgress?.let { maxOf(item.progress ?: 0, it) }
        return item.copy(
            state = when {
                available -> TrackedMediaState.AVAILABLE
                queue.any(::isDownloading) -> TrackedMediaState.DOWNLOADING
                queue.isNotEmpty() -> TrackedMediaState.QUEUED
                else -> stateWithoutQueue(item, false)
            },
            detail = "",
            progress = progress
        )
    }

    private fun itemProgress(item: JsonObject): Double {
        val size = item["size"]?.jsonPrimitive?.longOrNull ?: return 0.0
        val sizeLeft = item["sizeleft"]?.jsonPrimitive?.longOrNull ?: return 0.0
        if (size <= 0) return 0.0
        return (size - sizeLeft).coerceAtLeast(0).toDouble().div(size).coerceIn(0.0, 1.0)
    }

    private fun isDownloading(item: JsonObject): Boolean =
        item["status"]?.jsonPrimitive?.contentOrNull.equals("downloading", ignoreCase = true) ||
            itemProgress(item) > 0

    private val MediaProvider.queueIdName: String
        get() = if (this == MediaProvider.SONARR) "seriesId" else "movieId"

    private fun stateWithoutQueue(item: TrackedMedia, available: Boolean): TrackedMediaState = when {
        available -> TrackedMediaState.AVAILABLE

        item.state == TrackedMediaState.SEARCHING && System.currentTimeMillis() - item.addedAt < SEARCH_GRACE_MS ->
            TrackedMediaState.SEARCHING

        else -> TrackedMediaState.MONITORED
    }

    private companion object {
        const val SEARCH_GRACE_MS = 120_000L
    }
}
