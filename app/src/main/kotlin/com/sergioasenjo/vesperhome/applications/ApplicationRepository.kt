package com.sergioasenjo.vesperhome.applications

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform

interface ApplicationRepository {
    fun observeApplications(): Flow<List<LauncherApp>>

    fun launch(componentName: ComponentName, user: UserHandle): Boolean

    fun createApplicationDetailsIntent(packageName: String): Intent

    fun createUninstallIntent(packageName: String): Intent
}

class PlatformApplicationRepository(context: Context) : ApplicationRepository {
    private data class RefreshRequest(val changedPackages: Set<String>, val initial: Boolean = false)

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val packageManager = context.packageManager
    private val ownPackageName = context.packageName
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val artworkCache = LruCache<String, Drawable>(ARTWORK_CACHE_SIZE)
    private val artworkVersions = ConcurrentHashMap<String, Long>()
    private val snapshotCache = ApplicationSnapshotCache(context)

    @Volatile
    private var memorySnapshot: List<LauncherApp>? = null

    override fun observeApplications(): Flow<List<LauncherApp>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(RefreshRequest(setOf(packageName)))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(RefreshRequest(setOf(packageName)))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(RefreshRequest(setOf(packageName)))
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(RefreshRequest(packageNames.toSet()))
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(RefreshRequest(packageNames.toSet()))
            }
        }

        launcherApps.registerCallback(callback, callbackHandler)
        trySend(RefreshRequest(emptySet(), initial = true))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .buffer(Channel.CONFLATED)
        .transform { request ->
            var renderedSnapshot = false
            if (request.initial) {
                val cachedApps = memorySnapshot ?: snapshotCache.load().also { cached ->
                    if (cached.isNotEmpty()) memorySnapshot = cached
                }
                if (cachedApps.isNotEmpty()) {
                    emit(cachedApps)
                    renderedSnapshot = true
                }
            }
            request.changedPackages.forEach { packageName ->
                artworkCache.remove(packageName)
                artworkVersions[packageName] = (artworkVersions[packageName] ?: 0L) + 1L
            }
            if (renderedSnapshot) delay(BACKGROUND_REFRESH_DELAY_MS)
            val freshApps = loadApplications()
            memorySnapshot = freshApps
            emit(freshApps)
            snapshotCache.save(freshApps)
        }
        .flowOn(Dispatchers.IO)

    private suspend fun loadApplications(): List<LauncherApp> {
        val user = Process.myUserHandle()
        val activities = launcherApps.getActivityList(null, user)
            .asSequence()
            .filterNot { it.componentName.packageName == ownPackageName }
            .distinctBy { it.componentName.packageName }
            .toList()
        return coroutineScope {
            activities.map { activity ->
                async(APP_LOAD_DISPATCHER) {
                    val packageName = activity.componentName.packageName
                    val artwork = artworkCache[packageName] ?: (
                        activity.applicationInfo.loadBanner(packageManager)
                            ?: activity.getBadgedIcon(0)
                        ).also { artworkCache.put(packageName, it) }
                    LauncherApp(
                        componentName = activity.componentName,
                        label = activity.label.toString(),
                        artwork = artwork,
                        artworkVersion = activity.applicationInfo.sourceDir
                            ?.let(::File)
                            ?.lastModified()
                            ?: (artworkVersions[packageName] ?: 0L),
                        user = activity.user
                    )
                }
            }
                .awaitAll()
        }
            .sortedBy { it.label.lowercase() }
    }

    override fun launch(componentName: ComponentName, user: UserHandle): Boolean = try {
        launcherApps.startMainActivity(componentName, user, null, null)
        true
    } catch (error: ActivityNotFoundException) {
        Log.e(TAG, "Unable to launch $componentName", error)
        false
    } catch (error: SecurityException) {
        Log.e(TAG, "Unable to launch $componentName", error)
        false
    }

    override fun createApplicationDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    override fun createUninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))

    private companion object {
        const val TAG = "ApplicationRepository"
        const val ARTWORK_CACHE_SIZE = 64
        const val MAX_CONCURRENT_APP_LOADS = 2
        const val BACKGROUND_REFRESH_DELAY_MS = 1_500L
        val APP_LOAD_DISPATCHER = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_APP_LOADS)
    }
}
