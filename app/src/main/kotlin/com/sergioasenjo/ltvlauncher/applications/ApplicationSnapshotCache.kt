package com.sergioasenjo.ltvlauncher.applications

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.AtomicFile
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ApplicationSnapshotCache(context: Context) {
    private val cacheDirectory = File(context.filesDir, CACHE_DIRECTORY_NAME)
    private val metadataFile = AtomicFile(File(cacheDirectory, METADATA_FILE_NAME))
    private val defaultArtwork = context.packageManager.defaultActivityIcon
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedVersions = emptyMap<String, Long>()

    suspend fun load(): List<LauncherApp> {
        val snapshot = runCatching {
            metadataFile.openRead().bufferedReader().use { reader ->
                json.decodeFromString<ApplicationSnapshot>(reader.readText())
            }
        }.getOrNull() ?: return emptyList()
        cachedVersions = snapshot.apps.associate { it.packageName to it.artworkVersion }
        val user = Process.myUserHandle()
        return snapshot.apps.map { cachedApp ->
            LauncherApp(
                componentName = ComponentName(cachedApp.packageName, cachedApp.className),
                label = cachedApp.label,
                artwork = defaultArtwork,
                artworkVersion = cachedApp.artworkVersion,
                user = user,
                isTvApp = cachedApp.isTvApp,
                artworkFile = artworkFile(cachedApp.packageName).takeIf(File::isFile)
            )
        }
    }

    fun save(apps: List<LauncherApp>) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return
        apps.forEach { app ->
            val artworkFile = artworkFile(app.packageName)
            if (!artworkFile.exists() || cachedVersions[app.packageName] != app.artworkVersion) {
                saveArtwork(app.packageName, app.artwork)
            }
        }
        val activePackages = apps.mapTo(mutableSetOf(), LauncherApp::packageName)
        cacheDirectory.listFiles()
            ?.filter { it.extension == ARTWORK_FILE_EXTENSION && it.nameWithoutExtension !in activePackages }
            ?.forEach(File::delete)

        val snapshot = ApplicationSnapshot(
            apps = apps.map { app ->
                CachedApplication(
                    packageName = app.packageName,
                    className = app.componentName.className,
                    label = app.label,
                    artworkVersion = app.artworkVersion,
                    isTvApp = app.isTvApp
                )
            }
        )
        val output = runCatching { metadataFile.startWrite() }.getOrNull() ?: return
        try {
            output.write(json.encodeToString(snapshot).toByteArray())
            output.flush()
            metadataFile.finishWrite(output)
            cachedVersions = apps.associate { it.packageName to it.artworkVersion }
        } catch (_: Exception) {
            metadataFile.failWrite(output)
        }
    }

    private fun saveArtwork(packageName: String, artwork: Drawable) {
        val width = artwork.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ARTWORK_SIZE
        val height = artwork.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ARTWORK_SIZE
        val scale = minOf(
            MAX_ARTWORK_WIDTH.toFloat() / width,
            MAX_ARTWORK_HEIGHT.toFloat() / height,
            1f
        )
        val bitmap = runCatching {
            artwork.toBitmap(
                width = (width * scale).toInt().coerceAtLeast(1),
                height = (height * scale).toInt().coerceAtLeast(1),
                config = Bitmap.Config.ARGB_8888
            )
        }.getOrNull() ?: return
        val atomicArtwork = AtomicFile(artworkFile(packageName))
        val output = runCatching { atomicArtwork.startWrite() }.getOrNull() ?: return
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
            atomicArtwork.finishWrite(output)
        } catch (_: Exception) {
            atomicArtwork.failWrite(output)
        }
    }

    private fun artworkFile(packageName: String): File = File(cacheDirectory, "$packageName.$ARTWORK_FILE_EXTENSION")

    private companion object {
        const val CACHE_DIRECTORY_NAME = "application_snapshot"
        const val METADATA_FILE_NAME = "applications.json"
        const val ARTWORK_FILE_EXTENSION = "png"
        const val DEFAULT_ARTWORK_SIZE = 192
        const val MAX_ARTWORK_WIDTH = 384
        const val MAX_ARTWORK_HEIGHT = 216
        const val PNG_QUALITY = 100
    }
}

@Serializable
private data class ApplicationSnapshot(val apps: List<CachedApplication>)

@Serializable
private data class CachedApplication(
    val packageName: String,
    val className: String,
    val label: String,
    val artworkVersion: Long,
    val isTvApp: Boolean
)
