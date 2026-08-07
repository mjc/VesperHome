package com.sergioasenjo.ltvlauncher.applications

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface ApplicationRepository {
    fun observeApplications(): Flow<List<LauncherApp>>

    fun launch(componentName: ComponentName, user: UserHandle): Boolean

    fun createApplicationDetailsIntent(packageName: String): Intent

    fun createUninstallIntent(packageName: String): Intent
}

class PlatformApplicationRepository(context: Context) : ApplicationRepository {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val packageManager = context.packageManager
    private val ownPackageName = context.packageName
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val artworkCache = LruCache<String, Drawable>(ARTWORK_CACHE_SIZE)

    override fun observeApplications(): Flow<List<LauncherApp>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(setOf(packageName))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(setOf(packageName))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(setOf(packageName))
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(packageNames.toSet())
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(packageNames.toSet())
            }
        }

        launcherApps.registerCallback(callback, callbackHandler)
        trySend(emptySet())
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .buffer(Channel.CONFLATED)
        .map { changedPackages ->
            changedPackages.forEach(artworkCache::remove)
            loadApplications()
        }
        .flowOn(Dispatchers.IO)

    private fun loadApplications(): List<LauncherApp> {
        val user = Process.myUserHandle()
        return launcherApps.getActivityList(null, user)
            .asSequence()
            .filterNot { it.componentName.packageName == ownPackageName }
            .distinctBy { it.componentName.packageName }
            .map { activity ->
                val packageName = activity.componentName.packageName
                val artwork = artworkCache[packageName] ?: (
                    activity.applicationInfo.loadBanner(packageManager)
                        ?: activity.getBadgedIcon(0)
                    ).also { artworkCache.put(packageName, it) }
                LauncherApp(
                    componentName = activity.componentName,
                    label = activity.label.toString(),
                    artwork = artwork,
                    artworkVersion = runCatching {
                        packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                    }.getOrDefault(0L),
                    user = activity.user,
                    isTvApp = packageManager.getLeanbackLaunchIntentForPackage(
                        activity.componentName.packageName
                    ) != null
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
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
    }
}
