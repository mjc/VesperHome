package com.sergioasenjo.vesperhome.upcoming

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sergioasenjo.vesperhome.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlexItemLauncher(private val activity: AppCompatActivity) {
    val available: Boolean
        get() = activity.packageManager.getLeanbackLaunchIntentForPackage(PLEX_PACKAGE) != null

    fun open(item: UpcomingMediaItem) {
        activity.lifecycleScope.launch {
            val uri = try {
                findItem(item)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("PlexItemLauncher", "Plex library lookup failed", error)
                null
            }
            if (uri == null) {
                Toast.makeText(activity, R.string.upcoming_plex_item_unavailable, Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                // Plex's singleTask entry activity ignores new item links on a reused task.
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(PLEX_PACKAGE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.upcoming_plex_app_unavailable, Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(activity, R.string.upcoming_plex_app_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal suspend fun findItem(item: UpcomingMediaItem): Uri? = withContext(Dispatchers.IO) {
        // Plex resolves suggestions using its own signed-in servers; no separate credentials are needed.
        activity.contentResolver.query(
            Uri.parse("content://com.plexapp.android.SearchProvider/${SearchManager.SUGGEST_URI_PATH_QUERY}"),
            null,
            null,
            arrayOf(item.title),
            null
        )?.use { cursor ->
            val titleColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1)
            val uriColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_INTENT_DATA)
            val typeColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_CONTENT_TYPE)
            val yearColumn = cursor.getColumnIndex(SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR)
            val isSeries = item.type == UpcomingMediaType.EPISODE
            if (titleColumn == -1 || uriColumn == -1 || typeColumn == -1) return@use null
            if (!isSeries && (item.productionYear == null || yearColumn == -1)) return@use null
            val exactMatches = mutableSetOf<Uri>()
            val aliases = mutableSetOf<Uri>()
            while (cursor.moveToNext()) {
                // A series suggestion has no video MIME type; individual episodes and movies do.
                val mime = cursor.getString(typeColumn)
                if (isSeries) {
                    if (mime != null) continue
                } else {
                    if (mime?.startsWith("video/", ignoreCase = true) != true) continue
                    if (cursor.getString(yearColumn)?.toIntOrNull() != item.productionYear) continue
                }
                val title = cursor.getString(titleColumn) ?: continue
                val uri = cursor.getString(uriColumn)?.let(Uri::parse) ?: continue
                if (uri.scheme != "plex") continue
                if (title.equals(item.title, ignoreCase = true)) exactMatches.add(uri)
                // Sonarr calls Richard Osman's House of Games simply House of Games.
                if (isSeries && item.title.equals("House of Games", ignoreCase = true) &&
                    title.equals("Richard Osman's House of Games", ignoreCase = true)
                ) {
                    aliases.add(uri)
                }
            }
            // Reject ambiguous titles rather than opening an arbitrary remake or server copy.
            if (exactMatches.isNotEmpty()) exactMatches.singleOrNull() else aliases.singleOrNull()
        }
    }

    private companion object {
        const val PLEX_PACKAGE = "com.plexapp.android"
    }
}
