package com.sergioasenjo.vesperhome.livetv

import android.content.ComponentName
import android.os.Bundle
import com.sergioasenjo.vesperhome.plugin.api.LiveTvPluginContract

data class LiveTvChannel(
    val plugin: ComponentName,
    val id: String,
    val name: String,
    val number: String?,
    val imageUrl: String?,
    val currentProgram: String?
) {
    companion object {
        fun from(plugin: ComponentName, bundle: Bundle): LiveTvChannel? {
            val id = bundle.getString(LiveTvPluginContract.CHANNEL_ID) ?: return null
            val name = bundle.getString(LiveTvPluginContract.CHANNEL_NAME) ?: return null
            return LiveTvChannel(
                plugin = plugin,
                id = id,
                name = name,
                number = bundle.getString(LiveTvPluginContract.CHANNEL_NUMBER),
                imageUrl = bundle.getString(LiveTvPluginContract.CHANNEL_IMAGE_URL),
                currentProgram = bundle.getString(LiveTvPluginContract.CHANNEL_PROGRAM)
            )
        }
    }
}

data class LiveTvPlayback(
    val plugin: ComponentName,
    val channelId: String,
    val url: String,
    val playSessionId: String?,
    val mediaSourceId: String?,
    val liveStreamId: String?
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(LiveTvPluginContract.PLAYBACK_CHANNEL_ID, channelId)
        putString(LiveTvPluginContract.PLAYBACK_URL, url)
        putString(LiveTvPluginContract.PLAYBACK_PLAY_SESSION_ID, playSessionId)
        putString(LiveTvPluginContract.PLAYBACK_MEDIA_SOURCE_ID, mediaSourceId)
        putString(LiveTvPluginContract.PLAYBACK_LIVE_STREAM_ID, liveStreamId)
    }

    companion object {
        fun from(plugin: ComponentName, bundle: Bundle): LiveTvPlayback? {
            val channelId = bundle.getString(LiveTvPluginContract.PLAYBACK_CHANNEL_ID) ?: return null
            val url = bundle.getString(LiveTvPluginContract.PLAYBACK_URL) ?: return null
            return LiveTvPlayback(
                plugin = plugin,
                channelId = channelId,
                url = url,
                playSessionId = bundle.getString(LiveTvPluginContract.PLAYBACK_PLAY_SESSION_ID),
                mediaSourceId = bundle.getString(LiveTvPluginContract.PLAYBACK_MEDIA_SOURCE_ID),
                liveStreamId = bundle.getString(LiveTvPluginContract.PLAYBACK_LIVE_STREAM_ID)
            )
        }
    }
}

data class LiveTvPluginState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val channels: List<LiveTvChannel> = emptyList()
)

data class LastLiveTvChannel(val plugin: ComponentName, val channelId: String)
