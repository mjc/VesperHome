package com.sergioasenjo.vesperhome.plugin.jellyfinlivetv

import android.os.Bundle
import com.sergioasenjo.vesperhome.plugin.api.LiveTvPluginContract
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class JellyfinLiveTvApi(private val client: OkHttpClient, private val json: Json) {
    suspend fun channels(connection: JellyfinConnection): List<Bundle> {
        val result: JellyfinChannelResult = get(
            connection,
            "LiveTv/Channels",
            mapOf(
                "userId" to connection.userId,
                "addCurrentProgram" to "true",
                "enableImages" to "true",
                "imageTypeLimit" to "1",
                "enableImageTypes" to "Primary",
                "sortBy" to "SortName",
                "sortOrder" to "Ascending"
            )
        )
        return result.items.map { channel ->
            Bundle().apply {
                putString(LiveTvPluginContract.CHANNEL_ID, channel.id)
                putString(LiveTvPluginContract.CHANNEL_NAME, channel.name)
                putString(LiveTvPluginContract.CHANNEL_NUMBER, channel.channelNumber)
                putString(LiveTvPluginContract.CHANNEL_PROGRAM, channel.currentProgram?.name)
                if (channel.imageTags["Primary"] != null) {
                    putString(
                        LiveTvPluginContract.CHANNEL_IMAGE_URL,
                        authenticatedUrl(connection, "Items/${channel.id}/Images/Primary").toString()
                    )
                }
            }
        }
    }

    suspend fun playback(connection: JellyfinConnection, channelId: String): Bundle {
        val response: JsonObject = post(
            connection,
            "Items/$channelId/PlaybackInfo",
            playbackRequest(connection.userId)
        )
        val source = response["MediaSources"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw IOException("Jellyfin returned no playable source")
        val playbackUrl = playbackUrl(connection, channelId, source)
            ?: throw IOException("Jellyfin returned no supported stream")
        return Bundle().apply {
            putString(LiveTvPluginContract.PLAYBACK_CHANNEL_ID, channelId)
            putString(LiveTvPluginContract.PLAYBACK_URL, playbackUrl)
            putString(LiveTvPluginContract.PLAYBACK_PLAY_SESSION_ID, response.string("PlaySessionId"))
            putString(LiveTvPluginContract.PLAYBACK_MEDIA_SOURCE_ID, source.string("Id"))
            putString(LiveTvPluginContract.PLAYBACK_LIVE_STREAM_ID, source.string("LiveStreamId"))
        }
    }

    suspend fun stopPlayback(connection: JellyfinConnection, playback: Bundle) {
        val channelId = playback.getString(LiveTvPluginContract.PLAYBACK_CHANNEL_ID) ?: return
        post<JsonObject>(
            connection,
            "Sessions/Playing/Stopped",
            buildJsonObject {
                put("ItemId", channelId)
                playback.getString(LiveTvPluginContract.PLAYBACK_PLAY_SESSION_ID)?.let {
                    put("PlaySessionId", it)
                }
                playback.getString(LiveTvPluginContract.PLAYBACK_MEDIA_SOURCE_ID)?.let {
                    put("MediaSourceId", it)
                }
                playback.getString(LiveTvPluginContract.PLAYBACK_LIVE_STREAM_ID)?.let {
                    put("LiveStreamId", it)
                }
                put("PositionTicks", 0)
            }
        )
    }

    private fun playbackRequest(userId: String): JsonObject = buildJsonObject {
        put("UserId", userId)
        put("AutoOpenLiveStream", true)
        put("EnableDirectPlay", true)
        put("EnableDirectStream", true)
        put("EnableTranscoding", true)
        put("AllowVideoStreamCopy", true)
        put("AllowAudioStreamCopy", true)
        put("DeviceProfile", deviceProfile())
    }

    // Mirrors the supported container strategy used by the GPL-licensed Jellyfin Android TV client.
    // https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfile.kt
    private fun deviceProfile(): JsonObject = buildJsonObject {
        put("Name", "Vesper Home Live TV")
        put("MaxStreamingBitrate", 120_000_000)
        put("MaxStaticBitrate", 120_000_000)
        put(
            "DirectPlayProfiles",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("Container", "asf,hls,m4v,mkv,mov,mp4,ts,webm,wmv")
                        put("Type", "Video")
                        put("VideoCodec", "h264,hevc,mpeg2video,vp8,vp9,av1")
                        put("AudioCodec", "aac,ac3,eac3,mp2,mp3,opus,flac,vorbis")
                    }
                )
            }
        )
        put(
            "TranscodingProfiles",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("Container", "ts")
                        put("Type", "Video")
                        put("Context", "Streaming")
                        put("Protocol", "hls")
                        put("VideoCodec", "h264")
                        put("AudioCodec", "aac,ac3,eac3,mp3")
                        put("CopyTimestamps", false)
                    }
                )
            }
        )
    }

    private fun playbackUrl(connection: JellyfinConnection, channelId: String, source: JsonObject): String? {
        val remotePath = source.string("Path")?.takeIf { source.boolean("IsRemote") && it.startsWith("http") }
        if (source.boolean("SupportsDirectPlay")) {
            if (remotePath != null) return remotePath
            val container = source.string("Container")?.substringBefore(',')?.ifBlank { null } ?: "ts"
            return authenticatedUrl(connection, "Videos/$channelId/stream.$container") {
                addQueryParameter("static", "true")
                source.string("Id")?.let { addQueryParameter("mediaSourceId", it) }
                source.string("LiveStreamId")?.let { addQueryParameter("liveStreamId", it) }
            }.toString()
        }
        return source.string("TranscodingUrl")?.let { relativeUrl ->
            val url = if (relativeUrl.startsWith("http")) {
                relativeUrl.toHttpUrl()
            } else {
                connection.baseUrl.toHttpUrl().resolve(relativeUrl)
                    ?: throw IOException("Jellyfin returned an invalid transcoding URL")
            }
            url.newBuilder().addQueryParameter("api_key", connection.accessToken).build().toString()
        }
    }

    private suspend inline fun <reified T> get(
        connection: JellyfinConnection,
        path: String,
        query: Map<String, String>
    ): T = execute(
        connection,
        authenticatedUrl(connection, path) {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }
    )

    private suspend inline fun <reified T> post(connection: JellyfinConnection, path: String, body: JsonObject): T =
        execute(connection, authenticatedUrl(connection, path), body)

    private suspend inline fun <reified T> execute(
        connection: JellyfinConnection,
        url: HttpUrl,
        body: JsonObject? = null
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorizationHeader(connection))
            .apply {
                if (body != null) post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Jellyfin request failed with ${response.code}")
            json.decodeFromString<T>(response.body.string().ifBlank { "{}" })
        }
    }

    private fun authenticatedUrl(
        connection: JellyfinConnection,
        path: String,
        configure: HttpUrl.Builder.() -> Unit = {}
    ): HttpUrl = connection.baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .apply(configure)
        .addQueryParameter("api_key", connection.accessToken)
        .build()

    private fun authorizationHeader(connection: JellyfinConnection): String =
        "MediaBrowser Client=\"Vesper Home Live TV\", Device=\"Android TV\", " +
            "DeviceId=\"${connection.deviceId}\", Version=\"1.0.0\", Token=\"${connection.accessToken}\""

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull == true

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
