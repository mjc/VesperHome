package com.sergioasenjo.vesperhome.upcoming

import kotlinx.serialization.Serializable

data class UpcomingServerConfig(
    val sonarrUrl: String,
    val sonarrApiKey: String,
    val radarrUrl: String,
    val radarrApiKey: String,
    val player: UpcomingPlayer = UpcomingPlayer.AUTO
) {
    val sonarrConfigured: Boolean
        get() = sonarrUrl.isNotBlank() && sonarrApiKey.isNotBlank()

    val radarrConfigured: Boolean
        get() = radarrUrl.isNotBlank() && radarrApiKey.isNotBlank()

    val configured: Boolean
        get() = sonarrConfigured || radarrConfigured
}

enum class UpcomingPlayer {
    AUTO,
    PLEX,
    JELLYFIN
}

data class UpcomingMediaItem(
    val id: String,
    val title: String,
    val detail: String,
    val startsAtMillis: Long,
    val imageUrl: String?,
    val type: UpcomingMediaType,
    val providerId: UpcomingProviderId
)

data class UpcomingProviderId(val provider: UpcomingProvider, val value: Int)

enum class UpcomingProvider(val apiName: String) {
    TVDB("tvdb"),
    TMDB("tmdb")
}

enum class UpcomingMediaType {
    EPISODE,
    CINEMA,
    DIGITAL,
    PHYSICAL
}

@Serializable
data class CalendarImage(val coverType: String, val remoteUrl: String? = null)

@Serializable
data class SonarrEpisode(
    val id: Int,
    val title: String,
    val airDateUtc: String? = null,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val series: SonarrSeries? = null
)

@Serializable
data class SonarrSeries(val title: String, val tvdbId: Int, val images: List<CalendarImage> = emptyList())

@Serializable
data class RadarrMovie(
    val id: Int,
    val title: String,
    val tmdbId: Int,
    val inCinemas: String? = null,
    val digitalRelease: String? = null,
    val physicalRelease: String? = null,
    val images: List<CalendarImage> = emptyList()
)
