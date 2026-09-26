package com.sergioasenjo.vesperhome.plugin.jellyfinlivetv

import android.os.Bundle
import com.sergioasenjo.vesperhome.plugin.api.LiveTvPluginContract
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class JellyfinConnection(
    val baseUrl: String,
    val accessToken: String,
    val userId: String,
    val deviceId: String
) {
    companion object {
        fun from(bundle: Bundle): JellyfinConnection? {
            val baseUrl = bundle.getString(LiveTvPluginContract.CONNECTION_BASE_URL) ?: return null
            val accessToken = bundle.getString(LiveTvPluginContract.CONNECTION_ACCESS_TOKEN) ?: return null
            val userId = bundle.getString(LiveTvPluginContract.CONNECTION_USER_ID) ?: return null
            val deviceId = bundle.getString(LiveTvPluginContract.CONNECTION_DEVICE_ID) ?: return null
            return JellyfinConnection(baseUrl, accessToken, userId, deviceId)
        }
    }
}

@Serializable
internal data class JellyfinChannelResult(@SerialName("Items") val items: List<JellyfinChannel> = emptyList())

@Serializable
internal data class JellyfinChannel(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("CurrentProgram") val currentProgram: JellyfinProgram? = null
)

@Serializable
internal data class JellyfinProgram(@SerialName("Name") val name: String = "")
