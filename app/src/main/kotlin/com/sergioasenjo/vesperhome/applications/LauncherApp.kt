package com.sergioasenjo.vesperhome.applications

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle
import java.io.File

data class LauncherApp(
    val componentName: ComponentName,
    val label: String,
    val artwork: Drawable,
    val artworkVersion: Long,
    val user: UserHandle,
    val artworkFile: File? = null,
    val customBannerFile: File? = null,
    val customBannerRevision: Long? = null,
    val customName: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val manualOrder: Long? = null,
    val lastUsedAt: Long? = null
) {
    val packageName: String
        get() = componentName.packageName
}
