package com.sergioasenjo.ltvlauncher.about

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import com.sergioasenjo.ltvlauncher.accessibility.HomeButtonAccessibilityService
import com.sergioasenjo.ltvlauncher.platform.HomeRepository

data class DiagnosticsState(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val deviceName: String,
    val androidVersion: String,
    val apiLevel: Int,
    val defaultHome: Boolean,
    val homeButtonFixEnabled: Boolean,
    val notificationAccess: Boolean,
    val overlayAccess: Boolean,
    val brightnessAccess: Boolean
)

class DiagnosticsRepository(context: Context, private val homeRepository: HomeRepository) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val accessibilityManager = applicationContext.getSystemService(AccessibilityManager::class.java)
    private val accessibilityComponent = ComponentName(applicationContext, HomeButtonAccessibilityService::class.java)

    fun snapshot(): DiagnosticsState {
        val packageInfo = packageManager.getPackageInfo(applicationContext.packageName, 0)
        return DiagnosticsState(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            packageName = applicationContext.packageName,
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter(String::isNotBlank)
                .joinToString(" ")
                .trim(),
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            defaultHome = homeRepository.isDefaultLauncher(),
            homeButtonFixEnabled = isHomeButtonFixEnabled(),
            notificationAccess = applicationContext.packageName in
                NotificationManagerCompat.getEnabledListenerPackages(applicationContext),
            overlayAccess = Settings.canDrawOverlays(applicationContext),
            brightnessAccess = Settings.System.canWrite(applicationContext)
        )
    }

    private fun isHomeButtonFixEnabled(): Boolean = accessibilityManager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { service ->
            val serviceInfo = service.resolveInfo.serviceInfo
            ComponentName(serviceInfo.packageName, serviceInfo.name) == accessibilityComponent
        }
}
