package com.sergioasenjo.vesperhome.media

import kotlinx.serialization.json.JsonObject

enum class MediaProvider {
    SONARR,
    RADARR
}

enum class MediaSearchFilter {
    ALL,
    SERIES,
    MOVIES,
    TRACKED
}

sealed interface MediaCardItem {
    val stableId: String
    val provider: MediaProvider
    val title: String
    val year: Int
    val posterUrl: String?
    val detail: String
}

data class MediaSearchResult(
    override val provider: MediaProvider,
    val externalId: Int,
    override val title: String,
    override val year: Int,
    val overview: String,
    override val posterUrl: String?,
    val alreadyAdded: Boolean,
    val payload: JsonObject
) : MediaCardItem {
    override val stableId: String = "${provider.name}:$externalId"
    override val detail: String = provider.name
}

data class TrackedMedia(
    override val provider: MediaProvider,
    val externalId: Int,
    val providerItemId: Int?,
    override val title: String,
    override val year: Int,
    override val posterUrl: String?,
    val state: TrackedMediaState,
    override val detail: String,
    val progress: Int?,
    val addedAt: Long
) : MediaCardItem {
    override val stableId: String = "tracked:${provider.name}:$externalId"
}

enum class TrackedMediaState {
    MONITORED,
    SEARCHING,
    QUEUED,
    DOWNLOADING,
    AVAILABLE,
    FAILED
}

data class MediaRootFolder(val id: Int, val path: String, val freeSpace: Long?, val accessible: Boolean)

data class MediaQualityProfile(val id: Int, val name: String)

data class MediaTag(val id: Int, val label: String)

data class MediaAddChoices(
    val rootFolders: List<MediaRootFolder>,
    val qualityProfiles: List<MediaQualityProfile>,
    val tags: List<MediaTag>,
    val selection: MediaAddSelection
)

data class MediaAddSelection(
    val rootFolderId: Int,
    val qualityProfileId: Int,
    val monitor: String,
    val seriesType: SeriesType = SeriesType.STANDARD,
    val seasonFolder: Boolean = true,
    val minimumAvailability: MinimumAvailability = MinimumAvailability.RELEASED,
    val tagIds: Set<Int> = emptySet(),
    val searchMissing: Boolean = true,
    val searchCutoffUnmet: Boolean = false
)

data class MediaAddOutcome(val providerItemId: Int, val tracked: TrackedMedia)

data class MediaProviderDefaults(
    val rootFolderId: Int? = null,
    val qualityProfileId: Int? = null,
    val monitor: String? = null,
    val seriesType: SeriesType = SeriesType.STANDARD,
    val seasonFolder: Boolean = true,
    val minimumAvailability: MinimumAvailability = MinimumAvailability.RELEASED,
    val searchMissing: Boolean = true,
    val searchCutoffUnmet: Boolean = false
)

enum class SonarrMonitor(val apiValue: String) {
    ALL("all"),
    FUTURE("future"),
    MISSING("missing"),
    EXISTING("existing"),
    FIRST_SEASON("firstSeason"),
    LAST_SEASON("lastSeason"),
    PILOT("pilot"),
    RECENT("recent"),
    MONITOR_SPECIALS("monitorSpecials"),
    UNMONITOR_SPECIALS("unmonitorSpecials"),
    NONE("none"),
    SKIP("skip")
}

enum class RadarrMonitor(val apiValue: String) {
    MOVIE_ONLY("movieOnly"),
    MOVIE_AND_COLLECTION("movieAndCollection"),
    NONE("none")
}

enum class SeriesType(val apiValue: String) {
    STANDARD("standard"),
    DAILY("daily"),
    ANIME("anime")
}

enum class MinimumAvailability(val apiValue: String) {
    TBA("tba"),
    ANNOUNCED("announced"),
    IN_CINEMAS("inCinemas"),
    RELEASED("released")
}
