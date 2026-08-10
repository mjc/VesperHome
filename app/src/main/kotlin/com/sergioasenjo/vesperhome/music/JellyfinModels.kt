package com.sergioasenjo.vesperhome.music

import kotlinx.serialization.Serializable

@Serializable
data class JellyfinServer(val Address: String, val Id: String, val Name: String, val EndpointAddress: String? = null) {
    val baseUrl: String
        get() {
            val address = Address.trimEnd('/')
            return if (address.startsWith("http://") || address.startsWith("https://")) {
                address
            } else {
                "http://$address:8096"
            }
        }
}

@Serializable
data class JellyfinPublicInfo(val Id: String, val ServerName: String)

@Serializable
data class QuickConnectResult(val Authenticated: Boolean, val Secret: String, val Code: String)

@Serializable
data class QuickConnectRequest(val Secret: String)

@Serializable
data class AuthenticationResult(val User: JellyfinUser, val AccessToken: String, val ServerId: String)

@Serializable
data class JellyfinUser(val Id: String, val Name: String)

@Serializable
data class JellyfinItemsResult(val Items: List<JellyfinAudioItem> = emptyList())

@Serializable
data class JellyfinLibraryItemsResult(val Items: List<JellyfinLibraryItem> = emptyList())

@Serializable
data class JellyfinLibraryItem(
    val Id: String,
    val Name: String,
    val Type: String,
    val ChildCount: Int? = null,
    val RecursiveItemCount: Int? = null,
    val ImageTags: Map<String, String> = emptyMap(),
    val ProviderIds: Map<String, String> = emptyMap()
)

@Serializable
data class JellyfinAudioItem(
    val Id: String,
    val Name: String,
    val Artists: List<String> = emptyList(),
    val AlbumArtist: String? = null,
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val ImageTags: Map<String, String> = emptyMap(),
    val MediaType: String? = null
)

data class JellyfinCredentials(val baseUrl: String, val accessToken: String, val userId: String, val serverName: String)

data class JellyfinTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val streamUrl: String,
    val artworkUrl: String?
)

enum class JellyfinCollectionType {
    PLAYLIST,
    ALBUM
}

data class JellyfinMusicCollection(
    val id: String,
    val name: String,
    val trackCount: Int,
    val artworkUrl: String?,
    val type: JellyfinCollectionType
)
