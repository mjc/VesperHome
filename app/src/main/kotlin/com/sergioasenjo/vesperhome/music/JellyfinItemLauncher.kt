package com.sergioasenjo.vesperhome.music

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sergioasenjo.vesperhome.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class JellyfinItemLauncher(private val activity: AppCompatActivity) {
    fun open(resolveItemId: suspend () -> String?) {
        activity.lifecycleScope.launch {
            val itemId = try {
                resolveItemId()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (itemId == null) {
                Toast.makeText(activity, R.string.upcoming_jellyfin_item_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(itemId)).apply {
                setClassName(JELLYFIN_PACKAGE, JELLYFIN_STARTUP_ACTIVITY)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.upcoming_jellyfin_app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"
        const val JELLYFIN_STARTUP_ACTIVITY = "$JELLYFIN_PACKAGE.ui.startup.StartupActivity"
    }
}
