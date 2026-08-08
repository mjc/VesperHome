package com.sergioasenjo.ltvlauncher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LauncherNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: NotificationRepository
    private lateinit var popupManager: NotificationPopupManager
    private var popupsEnabled = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as LtvLauncherApplication).container
        repository = container.notificationRepository
        popupManager = NotificationPopupManager(this)
        repository.attach(this)
        serviceScope.launch {
            container.launcherSettingsRepository.settings
                .map { it.statusBar.systemNotificationPopups }
                .distinctUntilChanged()
                .collect { popupsEnabled = it }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        publishActiveNotifications()
    }

    override fun onListenerDisconnected() {
        repository.updateNotifications(emptyArray())
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(notification: StatusBarNotification) {
        publishActiveNotifications()
        if (popupsEnabled && shouldShowPopup(notification)) popupManager.show(notification)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        publishActiveNotifications()
    }

    override fun onDestroy() {
        popupManager.release()
        repository.detach(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun publishActiveNotifications() {
        try {
            repository.updateNotifications(activeNotifications)
        } catch (_: SecurityException) {
            repository.updateNotifications(emptyArray())
        }
    }

    private fun shouldShowPopup(statusBarNotification: StatusBarNotification): Boolean {
        val notification = statusBarNotification.notification
        if (statusBarNotification.isOngoing) return false
        if (notification.category == Notification.CATEGORY_SERVICE ||
            notification.category == Notification.CATEGORY_TRANSPORT
        ) {
            return false
        }
        if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return title.isNotBlank() || text.isNotBlank()
    }
}
