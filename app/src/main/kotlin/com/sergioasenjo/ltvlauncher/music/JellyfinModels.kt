package com.sergioasenjo.ltvlauncher.music

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
data class JellyfinAudioItem(
    val Id: String,
    val Name: String,
    val Artists: List<String> = emptyList(),
    val AlbumArtist: String? = null,
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val ImageTags: Map<String, String> = emptyMap()
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
