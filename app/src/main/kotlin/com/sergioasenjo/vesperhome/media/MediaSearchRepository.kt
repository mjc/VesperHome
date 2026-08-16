package com.sergioasenjo.vesperhome.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class MediaSearchRepository(
    private val api: ArrApiClient,
    private val preferences: MediaSearchPreferencesRepository,
    private val trackedMediaDao: TrackedMediaDao
) {
    suspend fun connectedProviders(): Set<MediaProvider> = api.connectedProviders()

    suspend fun search(query: String, filter: MediaSearchFilter): List<MediaSearchResult> = supervisorScope {
        val providers = api.connectedProviders().filter { provider ->
            when (filter) {
                MediaSearchFilter.SERIES -> provider == MediaProvider.SONARR
                MediaSearchFilter.MOVIES -> provider == MediaProvider.RADARR
                MediaSearchFilter.ALL -> true
                MediaSearchFilter.TRACKED -> false
            }
        }
        val results = providers.map { provider ->
            async {
                try {
                    Result.success(searchProvider(provider, query))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        }.awaitAll()
        if (results.isNotEmpty() && results.all(Result<List<MediaSearchResult>>::isFailure)) {
            throw results.firstNotNullOf { it.exceptionOrNull() }
        }
        results.flatMap { it.getOrDefault(emptyList()) }
    }

    suspend fun loadAddChoices(provider: MediaProvider): MediaAddChoices = coroutineScope {
        val rootFolders = async { loadRootFolders(provider) }
        val qualityProfiles = async { loadQualityProfiles(provider) }
        val tags = async { loadTags(provider) }
        val defaults = async { preferences.defaults(provider) }
        val roots = rootFolders.await()
        val profiles = qualityProfiles.await()
        val saved = defaults.await()
        check(roots.isNotEmpty() && profiles.isNotEmpty()) { "No root folders or quality profiles are available" }
        val defaultMonitor = if (provider == MediaProvider.SONARR) {
            SonarrMonitor.ALL.apiValue
        } else {
            RadarrMonitor.MOVIE_ONLY.apiValue
        }
        MediaAddChoices(
            rootFolders = roots,
            qualityProfiles = profiles,
            tags = tags.await(),
            selection = MediaAddSelection(
                rootFolderId = saved.rootFolderId?.takeIf { id -> roots.any { it.id == id } } ?: roots.first().id,
                qualityProfileId = saved.qualityProfileId?.takeIf { id -> profiles.any { it.id == id } }
                    ?: profiles.first().id,
                monitor = saved.monitor ?: defaultMonitor,
                seriesType = saved.seriesType,
                seasonFolder = saved.seasonFolder,
                minimumAvailability = saved.minimumAvailability,
                searchMissing = saved.searchMissing,
                searchCutoffUnmet = saved.searchCutoffUnmet
            )
        )
    }

    suspend fun add(
        result: MediaSearchResult,
        choices: MediaAddChoices,
        selection: MediaAddSelection
    ): MediaAddOutcome {
        val rootFolder = choices.rootFolders.first { it.id == selection.rootFolderId }
        check(rootFolder.accessible) { "The selected root folder is unavailable" }
        check(choices.qualityProfiles.any { it.id == selection.qualityProfileId }) {
            "The selected quality profile is unavailable"
        }
        val payload = when (result.provider) {
            MediaProvider.SONARR -> sonarrPayload(result.payload, rootFolder.path, selection)
            MediaProvider.RADARR -> radarrPayload(result.payload, rootFolder.path, selection)
        }
        val path = if (result.provider == MediaProvider.SONARR) "api/v3/series" else "api/v3/movie"
        val response = api.postObject(result.provider, path, payload)
        val providerItemId = response.getValue("id").jsonPrimitive.int
        preferences.save(result.provider, selection)
        val searching = selection.searchMissing || selection.searchCutoffUnmet
        val tracked = TrackedMedia(
            provider = result.provider,
            externalId = result.externalId,
            providerItemId = providerItemId,
            title = result.title,
            year = result.year,
            posterUrl = result.posterUrl,
            state = if (searching) TrackedMediaState.SEARCHING else TrackedMediaState.MONITORED,
            detail = "",
            progress = null,
            addedAt = System.currentTimeMillis()
        )
        trackedMediaDao.upsert(tracked.toEntity())
        return MediaAddOutcome(providerItemId, tracked)
    }

    private suspend fun searchProvider(provider: MediaProvider, query: String): List<MediaSearchResult> =
        coroutineScope {
            val endpoint = if (provider == MediaProvider.SONARR) "api/v3/series/lookup" else "api/v3/movie/lookup"
            val libraryEndpoint = if (provider == MediaProvider.SONARR) "api/v3/series" else "api/v3/movie"
            val lookup = async { api.getArray(provider, endpoint, mapOf("term" to query)) }
            val library = async { api.getArray(provider, libraryEndpoint) }
            val existingIds = library.await().mapNotNull { element ->
                element.jsonObject[provider.externalIdName]?.jsonPrimitive?.intOrNull
            }.toSet()
            lookup.await().mapNotNull { element ->
                val payload = element.jsonObject
                val externalId = payload[provider.externalIdName]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                MediaSearchResult(
                    provider = provider,
                    externalId = externalId,
                    title = payload["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    year = payload["year"]?.jsonPrimitive?.intOrNull ?: 0,
                    overview = payload["overview"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    posterUrl = payload.posterUrl(),
                    alreadyAdded = externalId in existingIds,
                    payload = payload
                )
            }.take(MAX_RESULTS_PER_PROVIDER)
        }

    private suspend fun loadRootFolders(provider: MediaProvider): List<MediaRootFolder> =
        api.getArray(provider, "api/v3/rootfolder").map { element ->
            val value = element.jsonObject
            MediaRootFolder(
                id = value.getValue("id").jsonPrimitive.int,
                path = value.getValue("path").jsonPrimitive.content,
                freeSpace = value["freeSpace"]?.jsonPrimitive?.longOrNull,
                accessible = value["accessible"]?.jsonPrimitive?.boolean ?: true
            )
        }.sortedByDescending(MediaRootFolder::accessible)

    private suspend fun loadQualityProfiles(provider: MediaProvider): List<MediaQualityProfile> =
        api.getArray(provider, "api/v3/qualityprofile").map { element ->
            val value = element.jsonObject
            MediaQualityProfile(
                id = value.getValue("id").jsonPrimitive.int,
                name = value.getValue("name").jsonPrimitive.content
            )
        }

    private suspend fun loadTags(provider: MediaProvider): List<MediaTag> =
        api.getArray(provider, "api/v3/tag").map { element ->
            val value = element.jsonObject
            MediaTag(
                id = value.getValue("id").jsonPrimitive.int,
                label = value.getValue("label").jsonPrimitive.content
            )
        }

    private fun sonarrPayload(payload: JsonObject, rootPath: String, selection: MediaAddSelection): JsonObject =
        buildJsonObject {
            for ((name, value) in payload) put(name, value)
            put("rootFolderPath", rootPath)
            put("qualityProfileId", selection.qualityProfileId)
            put("monitored", selection.monitor !in setOf(SonarrMonitor.NONE.apiValue, SonarrMonitor.SKIP.apiValue))
            put("seriesType", selection.seriesType.apiValue)
            put("seasonFolder", selection.seasonFolder)
            put("tags", selection.tagIds.toJsonArray())
            put(
                "addOptions",
                buildJsonObject {
                    put("monitor", selection.monitor)
                    put("searchForMissingEpisodes", selection.searchMissing)
                    put("searchForCutoffUnmetEpisodes", selection.searchCutoffUnmet)
                }
            )
        }

    private fun radarrPayload(payload: JsonObject, rootPath: String, selection: MediaAddSelection): JsonObject =
        buildJsonObject {
            for ((name, value) in payload) put(name, value)
            put("rootFolderPath", rootPath)
            put("qualityProfileId", selection.qualityProfileId)
            put("monitored", selection.monitor != RadarrMonitor.NONE.apiValue)
            put("minimumAvailability", selection.minimumAvailability.apiValue)
            put("tags", selection.tagIds.toJsonArray())
            put(
                "addOptions",
                buildJsonObject {
                    put("monitor", selection.monitor)
                    put("searchForMovie", selection.searchMissing)
                    put("addMethod", "manual")
                }
            )
        }

    private fun Set<Int>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))

    private fun JsonObject.posterUrl(): String? = this["remotePoster"]?.jsonPrimitive?.contentOrNull
        ?: this["images"]?.jsonArray?.firstOrNull { element ->
            element.jsonObject["coverType"]?.jsonPrimitive?.contentOrNull.equals("poster", ignoreCase = true)
        }?.jsonObject?.get("remoteUrl")?.jsonPrimitive?.contentOrNull

    private val MediaProvider.externalIdName: String
        get() = if (this == MediaProvider.SONARR) "tvdbId" else "tmdbId"

    private companion object {
        const val MAX_RESULTS_PER_PROVIDER = 30
    }
}
