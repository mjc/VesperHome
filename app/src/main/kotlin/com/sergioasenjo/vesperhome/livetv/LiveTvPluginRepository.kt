package com.sergioasenjo.vesperhome.livetv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.sergioasenjo.vesperhome.music.MusicPreferencesRepository
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvChannelsCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionProvider
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvPlaybackCallback
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvPlugin
import com.sergioasenjo.vesperhome.plugin.api.LiveTvPluginContract
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class LiveTvPluginRepository(context: Context, private val jellyfinPreferences: MusicPreferencesRepository) {
    private data class BoundPlugin(
        val component: ComponentName,
        val connection: ServiceConnection,
        var service: ILiveTvPlugin? = null
    )

    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(LiveTvPluginState())
    private val plugins = mutableMapOf<ComponentName, BoundPlugin>()
    private val channelsByPlugin = mutableMapOf<ComponentName, List<LiveTvChannel>>()
    private val loadingPlugins = mutableSetOf<ComponentName>()
    private var scope: CoroutineScope? = null
    val state: StateFlow<LiveTvPluginState> = mutableState.asStateFlow()

    private val connectionProvider = object : ILiveTvConnectionProvider.Stub() {
        override fun requestConnection(callback: ILiveTvConnectionCallback) {
            scope?.launch {
                val credentials = jellyfinPreferences.credentials.first()
                if (credentials == null) {
                    callback.onUnavailable()
                    return@launch
                }
                callback.onConnection(
                    Bundle().apply {
                        putString(LiveTvPluginContract.CONNECTION_BASE_URL, credentials.baseUrl)
                        putString(LiveTvPluginContract.CONNECTION_ACCESS_TOKEN, credentials.accessToken)
                        putString(LiveTvPluginContract.CONNECTION_USER_ID, credentials.userId)
                        putString(LiveTvPluginContract.CONNECTION_DEVICE_ID, jellyfinPreferences.deviceId())
                    }
                )
            }
        }
    }

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        discoverPlugins()
    }

    fun refresh() {
        if (scope == null) return
        if (plugins.isEmpty() || plugins.values.any { it.service == null }) {
            discoverPlugins()
        } else {
            plugins.values.filter { it.service != null }.forEach(::loadChannels)
        }
    }

    suspend fun resolvePlayback(channel: LiveTvChannel): Result<LiveTvPlayback> =
        suspendCancellableCoroutine { continuation ->
            val plugin = plugins[channel.plugin]?.service
            if (plugin == null) {
                continuation.resume(Result.failure(IllegalStateException("The Live TV plugin is unavailable")))
                return@suspendCancellableCoroutine
            }
            runCatching {
                plugin.resolvePlayback(
                    channel.id,
                    connectionProvider,
                    object : ILiveTvPlaybackCallback.Stub() {
                        override fun onPlayback(playback: Bundle) {
                            val result = LiveTvPlayback.from(channel.plugin, playback)
                                ?.let(Result.Companion::success)
                                ?: Result.failure(IllegalStateException("The plugin returned invalid playback data"))
                            if (continuation.isActive) {
                                continuation.resume(result)
                            } else {
                                result.getOrNull()?.let(::stopPlayback)
                            }
                        }

                        override fun onError(message: String) {
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(IllegalStateException(message)))
                            }
                        }
                    }
                )
            }.onFailure { error ->
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
        }

    fun stopPlayback(playback: LiveTvPlayback) {
        runCatching {
            plugins[playback.plugin]?.service?.stopPlayback(playback.toBundle(), connectionProvider)
        }
    }

    fun release() {
        plugins.values.forEach { plugin -> runCatching { context.unbindService(plugin.connection) } }
        plugins.clear()
        channelsByPlugin.clear()
        loadingPlugins.clear()
        scope?.cancel()
        scope = null
        mutableState.value = LiveTvPluginState()
    }

    private fun discoverPlugins() {
        releaseBindings()
        val intent = Intent(LiveTvPluginContract.SERVICE_ACTION)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            context.packageManager.queryIntentServices(intent, 0)
        }
        val components = services.mapNotNull { result ->
            val info = result.serviceInfo ?: return@mapNotNull null
            ComponentName(info.packageName, info.name).takeIf(::hasMatchingSignature)
        }.distinct()
        mutableState.value = LiveTvPluginState(
            available = components.isNotEmpty(),
            loading = components.isNotEmpty()
        )
        components.forEach(::bindPlugin)
    }

    private fun bindPlugin(component: ComponentName) {
        lateinit var boundPlugin: BoundPlugin
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = ILiveTvPlugin.Stub.asInterface(binder)
                if (service.apiVersion != LiveTvPluginContract.API_VERSION) {
                    finishLoading(component)
                    return
                }
                boundPlugin.service = service
                loadChannels(boundPlugin)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                boundPlugin.service = null
                channelsByPlugin.remove(component)
                publishChannels()
            }
        }
        boundPlugin = BoundPlugin(component, serviceConnection)
        plugins[component] = boundPlugin
        loadingPlugins += component
        if (
            !context.bindService(
                Intent(LiveTvPluginContract.SERVICE_ACTION).setComponent(component),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        ) {
            plugins.remove(component)
            finishLoading(component)
        }
    }

    private fun loadChannels(plugin: BoundPlugin) {
        val service = plugin.service ?: return
        loadingPlugins += plugin.component
        mutableState.value = mutableState.value.copy(loading = true)
        runCatching {
            service.loadChannels(
                connectionProvider,
                object : ILiveTvChannelsCallback.Stub() {
                    override fun onChannels(channels: MutableList<Bundle>) {
                        scope?.launch {
                            channelsByPlugin[plugin.component] = channels.mapNotNull { channel ->
                                LiveTvChannel.from(plugin.component, channel)
                            }
                            finishLoading(plugin.component)
                        }
                    }

                    override fun onError(message: String) {
                        scope?.launch {
                            channelsByPlugin.remove(plugin.component)
                            finishLoading(plugin.component)
                        }
                    }
                }
            )
        }.onFailure {
            channelsByPlugin.remove(plugin.component)
            finishLoading(plugin.component)
        }
    }

    private fun finishLoading(component: ComponentName) {
        loadingPlugins -= component
        publishChannels()
    }

    private fun publishChannels() {
        mutableState.value = LiveTvPluginState(
            available = plugins.isNotEmpty(),
            loading = loadingPlugins.isNotEmpty(),
            channels = channelsByPlugin.values.flatten()
        )
    }

    private fun hasMatchingSignature(component: ComponentName): Boolean =
        context.packageManager.checkSignatures(context.packageName, component.packageName) ==
            PackageManager.SIGNATURE_MATCH

    private fun releaseBindings() {
        plugins.values.forEach { plugin -> runCatching { context.unbindService(plugin.connection) } }
        plugins.clear()
        channelsByPlugin.clear()
        loadingPlugins.clear()
    }
}
