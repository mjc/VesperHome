package com.sergioasenjo.vesperhome.plugin.jellyfinlivetv

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvChannelsCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionProvider
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvPlaybackCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvPlugin
import com.sergioasenjo.vesperhome.plugin.api.LiveTvPluginContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class JellyfinLiveTvPluginService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = JellyfinLiveTvApi(OkHttpClient(), Json { ignoreUnknownKeys = true })

    @Volatile private var lastConnection: JellyfinConnection? = null
    private val binder = object : ILiveTvPlugin.Stub() {
        override fun getApiVersion(): Int = LiveTvPluginContract.API_VERSION

        override fun loadChannels(connectionProvider: ILiveTvConnectionProvider, callback: ILiveTvChannelsCallback) =
            withConnection(
                provider = connectionProvider,
                onUnavailable = { callback.onChannels(emptyList()) }
            ) { connection ->
                runCatching { api.channels(connection) }
                    .onSuccess(callback::onChannels)
                    .onFailure { error -> callback.onError(error.message ?: "Unable to load Live TV channels") }
            }

        override fun resolvePlayback(
            channelId: String,
            connectionProvider: ILiveTvConnectionProvider,
            callback: ILiveTvPlaybackCallback
        ) = withConnection(
            provider = connectionProvider,
            onUnavailable = { callback.onError("Connect Jellyfin in Vesper Home first") }
        ) { connection ->
            runCatching { api.playback(connection, channelId) }
                .onSuccess(callback::onPlayback)
                .onFailure { error -> callback.onError(error.message ?: "Unable to start the channel") }
        }

        override fun stopPlayback(playback: Bundle, connectionProvider: ILiveTvConnectionProvider) {
            lastConnection?.let { connection ->
                scope.launch { stopPlayback(connection, playback) }
                return
            }
            withConnection(connectionProvider, onUnavailable = {}) { connection ->
                stopPlayback(connection, playback)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? =
        binder.takeIf { intent.action == LiveTvPluginContract.SERVICE_ACTION }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun withConnection(
        provider: ILiveTvConnectionProvider,
        onUnavailable: () -> Unit,
        action: suspend (JellyfinConnection) -> Unit
    ) {
        provider.requestConnection(
            object : ILiveTvConnectionCallback.Stub() {
                override fun onConnection(connection: Bundle) {
                    val credentials = JellyfinConnection.from(connection) ?: return
                    lastConnection = credentials
                    scope.launch { action(credentials) }
                }

                override fun onUnavailable() = onUnavailable()
            }
        )
    }

    private suspend fun stopPlayback(connection: JellyfinConnection, playback: Bundle) {
        runCatching { api.stopPlayback(connection, playback) }
            .onFailure { error -> Log.w(TAG, "Unable to close Jellyfin playback", error) }
    }

    private companion object {
        const val TAG = "JellyfinLiveTvPlugin"
    }
}
