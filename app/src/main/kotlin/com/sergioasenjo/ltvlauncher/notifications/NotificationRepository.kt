package com.sergioasenjo.ltvlauncher.notifications

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.LruCache
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LauncherNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val clearable: Boolean
)

data class NotificationState(
    val notifications: List<LauncherNotification> = emptyList(),
    val listenerEnabled: Boolean = false,
    val overlayEnabled: Boolean = false
)

class NotificationRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager
    private val appLabelCache = LruCache<String, String>(APP_LABEL_CACHE_SIZE)
    private val mutableState = MutableStateFlow(NotificationState())
    private var listenerService: LauncherNotificationListenerService? = null

    val state: StateFlow<NotificationState> = mutableState.asStateFlow()

    fun refreshPermissions() {
        mutableState.value = mutableState.value.copy(
            listenerEnabled = applicationContext.packageName in
                NotificationManagerCompat.getEnabledListenerPackages(applicationContext),
            overlayEnabled = Settings.canDrawOverlays(applicationContext)
        )
    }

    internal fun attach(service: LauncherNotificationListenerService) {
        listenerService = service
        refreshPermissions()
    }

    internal fun detach(service: LauncherNotificationListenerService) {
        if (listenerService === service) listenerService = null
        mutableState.value = mutableState.value.copy(notifications = emptyList())
        refreshPermissions()
    }

    internal fun updateNotifications(notifications: Array<out StatusBarNotification>?) {
        mutableState.value = mutableState.value.copy(
            notifications = notifications.orEmpty()
                .map(::toLauncherNotification)
                .sortedByDescending(LauncherNotification::postedAt)
        )
    }

    fun dismiss(key: String): Boolean {
        val service = listenerService ?: return false
        return try {
            service.cancelNotification(key)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun dismissAll(): Boolean {
        val service = listenerService ?: return false
        return try {
            service.cancelAllNotifications()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun toLauncherNotification(notification: StatusBarNotification): LauncherNotification {
        val extras = notification.notification.extras
        return LauncherNotification(
            key = notification.key,
            packageName = notification.packageName,
            appLabel = appLabel(notification.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = notification.postTime,
            clearable = notification.isClearable
        )
    }

    private fun appLabel(packageName: String): String = appLabelCache[packageName] ?: run {
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            packageName
        }
        appLabelCache.put(packageName, label)
        label
    }

    private companion object {
        const val APP_LABEL_CACHE_SIZE = 64
    }
}
