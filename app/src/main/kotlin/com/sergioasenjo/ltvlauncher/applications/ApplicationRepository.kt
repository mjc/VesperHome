package com.sergioasenjo.ltvlauncher.applications

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ApplicationRepository {
    suspend fun getApplications(): List<LauncherApp>

    fun launch(componentName: ComponentName, user: UserHandle)
}

class PlatformApplicationRepository(context: Context) : ApplicationRepository {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val packageManager = context.packageManager
    private val ownPackageName = context.packageName

    override suspend fun getApplications(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val user = Process.myUserHandle()
        launcherApps.getActivityList(null, user)
            .asSequence()
            .filterNot { it.componentName.packageName == ownPackageName }
            .map { activity ->
                val artwork = activity.applicationInfo.loadBanner(packageManager)
                    ?: activity.getBadgedIcon(0)
                LauncherApp(
                    componentName = activity.componentName,
                    label = activity.label.toString(),
                    artwork = artwork,
                    user = activity.user,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    override fun launch(componentName: ComponentName, user: UserHandle) {
        launcherApps.startMainActivity(componentName, user, null, null)
    }
}
