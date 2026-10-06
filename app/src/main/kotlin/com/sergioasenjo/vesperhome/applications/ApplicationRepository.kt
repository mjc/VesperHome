package com.sergioasenjo.vesperhome.applications

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val defaultArtwork = packageManager.defaultActivityIcon
    private val artworkVersions = ConcurrentHashMap<String, Long>()
    private val snapshotCache = ApplicationSnapshotCache(context)
    private val refreshMutex = Mutex()

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
                val cachedApps = refreshMutex.withLock {
                    memorySnapshot ?: snapshotCache.load().also { cached ->
                        if (cached.isNotEmpty()) memorySnapshot = cached
                    }
                }
                if (cachedApps.isNotEmpty()) {
                    emit(cachedApps)
                    renderedSnapshot = true
                }
            }
            if (renderedSnapshot) delay(BACKGROUND_REFRESH_DELAY_MS)
            // Each collector shares these files and versions. Keep the entire refresh atomic.
            val freshApps = refreshMutex.withLock {
                request.changedPackages.forEach { packageName ->
                    snapshotCache.invalidateArtwork(packageName)
                    artworkVersions[packageName] = (artworkVersions[packageName] ?: 0L) + 1L
                }
                loadApplications().also { apps ->
                    snapshotCache.save(apps)
                    memorySnapshot = apps
                }
            }
            emit(freshApps)
        }
        .flowOn(Dispatchers.IO)

    private suspend fun loadApplications(): List<LauncherApp> {
        val user = Process.myUserHandle()
        val activities = launcherApps.getActivityList(null, user)
            .asSequence()
            .filterNot { it.componentName.packageName == ownPackageName }
            .distinctBy { it.componentName.packageName }
            .toList()
        val previousApps = memorySnapshot.orEmpty().associateBy(LauncherApp::packageName)
        return coroutineScope {
            activities.map { activity ->
                async(APP_LOAD_DISPATCHER) {
                    val packageName = activity.componentName.packageName
                    val artworkVersion = (
                        activity.applicationInfo.sourceDir
                            ?.let(::File)
                            ?.lastModified()
                            ?: 0L
                        ) + (artworkVersions[packageName] ?: 0L)
                    val cachedArtwork = snapshotCache.cachedArtwork(packageName, artworkVersion)
                    val previousApp = previousApps[packageName]
                    if (snapshotCache.labelsMatchLocale && cachedArtwork != null &&
                        previousApp?.artworkVersion == artworkVersion
                    ) {
                        // Reading platform labels also opens every installed APK's resource table.
                        return@async previousApp.copy(
                            componentName = activity.componentName,
                            user = activity.user,
                            artworkFile = cachedArtwork
                        )
                    }
                    val artwork = if (cachedArtwork != null) {
                        defaultArtwork
                    } else {
                        (
                            activity.applicationInfo.loadBanner(packageManager)
                                ?: activity.getBadgedIcon(0)
                            )
                    }
                    val artworkFile = cachedArtwork ?: snapshotCache.saveArtwork(packageName, artwork)
                    LauncherApp(
                        componentName = activity.componentName,
                        label = activity.label.toString(),
                        artwork = if (artworkFile != null) defaultArtwork else artwork,
                        artworkVersion = artworkVersion,
                        user = activity.user,
                        artworkFile = artworkFile
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
        const val MAX_CONCURRENT_APP_LOADS = 2
        const val BACKGROUND_REFRESH_DELAY_MS = 1_500L
        val APP_LOAD_DISPATCHER = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_APP_LOADS)
    }
}
