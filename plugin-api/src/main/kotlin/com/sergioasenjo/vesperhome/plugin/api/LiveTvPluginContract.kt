package com.sergioasenjo.vesperhome.plugin.api

object LiveTvPluginContract {
    const val API_VERSION = 1
    const val SERVICE_ACTION = "com.sergioasenjo.vesperhome.plugin.LIVE_TV"

    const val CONNECTION_BASE_URL = "connection.base_url"
    const val CONNECTION_ACCESS_TOKEN = "connection.access_token"
    const val CONNECTION_USER_ID = "connection.user_id"
    const val CONNECTION_DEVICE_ID = "connection.device_id"

    const val CHANNEL_ID = "channel.id"
    const val CHANNEL_NAME = "channel.name"
    const val CHANNEL_NUMBER = "channel.number"
    const val CHANNEL_IMAGE_URL = "channel.image_url"
    const val CHANNEL_PROGRAM = "channel.program"

    const val PLAYBACK_CHANNEL_ID = "playback.channel_id"
    const val PLAYBACK_URL = "playback.url"
    const val PLAYBACK_PLAY_SESSION_ID = "playback.play_session_id"
    const val PLAYBACK_MEDIA_SOURCE_ID = "playback.media_source_id"
    const val PLAYBACK_LIVE_STREAM_ID = "playback.live_stream_id"
}
