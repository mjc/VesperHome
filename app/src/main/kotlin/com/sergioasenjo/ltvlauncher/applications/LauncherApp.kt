package com.sergioasenjo.ltvlauncher.applications

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class LauncherApp(
    val componentName: ComponentName,
    val label: String,
    val artwork: Drawable,
    val artworkVersion: Long,
    val user: UserHandle,
    val isTvApp: Boolean,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val manualOrder: Long? = null,
    val lastUsedAt: Long? = null
) {
    val packageName: String
        get() = componentName.packageName
}
