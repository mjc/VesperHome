package com.sergioasenjo.vesperhome.applications

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.AtomicFile
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.ConfigurationCompat
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ApplicationSnapshotCache(private val context: Context) {
    private val cacheDirectory = File(context.filesDir, CACHE_DIRECTORY_NAME)
    private val metadataFile = AtomicFile(File(cacheDirectory, METADATA_FILE_NAME))
    private val defaultArtwork = context.packageManager.defaultActivityIcon
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedVersions = emptyMap<String, Long>()

    @Volatile
    private var cachedLabelLocales = ""

    val labelsMatchLocale: Boolean get() = cachedLabelLocales == currentLocales()

    fun cachedArtwork(packageName: String, artworkVersion: Long): File? = artworkFile(packageName).takeIf {
        artworkVersion >= 0 && cachedVersions[packageName] == artworkVersion &&
            it.isFile
    }

    fun invalidateArtwork(packageName: String) {
        cachedVersions = cachedVersions - packageName
    }

    suspend fun load(): List<LauncherApp> {
        val snapshot = runCatching {
            metadataFile.openRead().bufferedReader().use { reader ->
                json.decodeFromString<ApplicationSnapshot>(reader.readText())
            }
        }.getOrNull() ?: return emptyList()
        cachedVersions = snapshot.apps.associate { it.packageName to it.artworkVersion }
        cachedLabelLocales = snapshot.locales
        val user = Process.myUserHandle()
        return snapshot.apps.map { cachedApp ->
            LauncherApp(
                componentName = ComponentName(cachedApp.packageName, cachedApp.className),
                label = cachedApp.label,
                artwork = defaultArtwork,
                artworkVersion = cachedApp.artworkVersion,
                user = user,
                artworkFile = cachedArtwork(cachedApp.packageName, cachedApp.artworkVersion)
            )
        }
    }

    fun save(apps: List<LauncherApp>) {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return
        val persistedVersions = apps.associate { app ->
            val file = app.artworkFile ?: cachedArtwork(app.packageName, app.artworkVersion)
                ?: saveArtwork(app.packageName, app.artwork)
            app.packageName to if (file != null) app.artworkVersion else -1L
        }
        val activePackages = apps.mapTo(mutableSetOf(), LauncherApp::packageName)
        cacheDirectory.listFiles()
            ?.filter { it.extension == ARTWORK_FILE_EXTENSION && it.nameWithoutExtension !in activePackages }
            ?.forEach(File::delete)

        val snapshot = ApplicationSnapshot(
            locales = currentLocales(),
            apps = apps.map { app ->
                CachedApplication(
                    packageName = app.packageName,
                    className = app.componentName.className,
                    label = app.label,
                    artworkVersion = persistedVersions.getValue(app.packageName)
                )
            }
        )
        val output = runCatching { metadataFile.startWrite() }.getOrNull() ?: return
        try {
            output.write(json.encodeToString(snapshot).toByteArray())
            output.flush()
            metadataFile.finishWrite(output)
            cachedVersions = persistedVersions
            cachedLabelLocales = snapshot.locales
        } catch (_: Exception) {
            metadataFile.failWrite(output)
        }
    }

    fun saveArtwork(packageName: String, artwork: Drawable): File? {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return null
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
        }.getOrNull() ?: return null
        return try {
            val atomicArtwork = AtomicFile(artworkFile(packageName))
            val output = runCatching { atomicArtwork.startWrite() }.getOrNull() ?: return null
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
                atomicArtwork.finishWrite(output)
                atomicArtwork.baseFile
            } catch (_: Exception) {
                atomicArtwork.failWrite(output)
                null
            }
        } finally {
            // toBitmap can return a BitmapDrawable's original bitmap. Only recycle our own copy.
            if (artwork !is BitmapDrawable || bitmap !== artwork.bitmap) bitmap.recycle()
        }
    }

    private fun artworkFile(packageName: String): File = File(cacheDirectory, "$packageName.$ARTWORK_FILE_EXTENSION")

    private fun currentLocales(): String =
        ConfigurationCompat.getLocales(context.resources.configuration).toLanguageTags()

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
private data class ApplicationSnapshot(val apps: List<CachedApplication>, val locales: String = "")

@Serializable
private data class CachedApplication(
    val packageName: String,
    val className: String,
    val label: String,
    val artworkVersion: Long
)
