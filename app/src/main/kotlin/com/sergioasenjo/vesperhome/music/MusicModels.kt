package com.sergioasenjo.vesperhome.music

enum class MusicProvider {
    JELLYFIN,
    PLEX
}

data class MusicCredentials(
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val serverName: String,
    val provider: MusicProvider = MusicProvider.JELLYFIN
)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val streamUrl: String,
    val artworkUrl: String?,
    val durationMillis: Long? = null,
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null
)

enum class MusicNormalizationMode {
    OFF,
    TRACK,
    ALBUM
}

enum class MusicCollectionType {
    PLAYLIST,
    ALBUM
}

data class MusicCollection(
    val id: String,
    val name: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val type: MusicCollectionType
)
