package com.sergioasenjo.ltvlauncher.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.wallpaperDataStore by preferencesDataStore(name = "wallpaper_settings")

enum class BuiltInWallpaper(val displayName: String, val colors: IntArray, val angle: Int) {
    PITCH_BLACK("Pitch Black", intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt()), 0),
    MIDNIGHT("Midnight", intArrayOf(0xFF18212B.toInt(), 0xFF080A0E.toInt()), 315),
    ARCTIC("Arctic", intArrayOf(0xFF6991C7.toInt(), 0xFFA3BDED.toInt()), 315),
    SLATE("Slate", intArrayOf(0xFF29323C.toInt(), 0xFF485563.toInt()), 90),
    VIOLET_HAZE("Violet Haze", intArrayOf(0xFF6E45E2.toInt(), 0xFF88D3CE.toInt()), 45),
    ROSE_DAWN("Rose Dawn", intArrayOf(0xFF9795F0.toInt(), 0xFFFBC8D4.toInt()), 180),
    EMBER("Ember", intArrayOf(0xFFFF6B95.toInt(), 0xFFFFC796.toInt()), 135),
    DUNE("Dune", intArrayOf(0xFFC79081.toInt(), 0xFFDFA579.toInt()), 180),
    OCEAN("Ocean", intArrayOf(0xFF093028.toInt(), 0xFF237A57.toInt()), 45),
    SILVER("Silver", intArrayOf(0xFFF5F7FA.toInt(), 0xFFC3CFE2.toInt()), 45),
    AURORA("Aurora", intArrayOf(0xFF39F3BB.toInt(), 0xFF3A6073.toInt()), 315)
}

enum class WallpaperTarget {
    MAIN,
    DAY,
    NIGHT
}

sealed interface WallpaperSelection {
    data class BuiltIn(val wallpaper: BuiltInWallpaper) : WallpaperSelection

    data object Custom : WallpaperSelection
}

data class WallpaperSettings(
    val timeBasedEnabled: Boolean = false,
    val main: WallpaperSelection = WallpaperSelection.BuiltIn(BuiltInWallpaper.MIDNIGHT),
    val day: WallpaperSelection = WallpaperSelection.BuiltIn(BuiltInWallpaper.ARCTIC),
    val night: WallpaperSelection = WallpaperSelection.BuiltIn(BuiltInWallpaper.MIDNIGHT),
    val revision: Long = 0L
) {
    fun selection(target: WallpaperTarget): WallpaperSelection = when (target) {
        WallpaperTarget.MAIN -> main
        WallpaperTarget.DAY -> day
        WallpaperTarget.NIGHT -> night
    }
}

data class WallpaperState(
    val settings: WallpaperSettings = WallpaperSettings(),
    val activeTarget: WallpaperTarget = WallpaperTarget.MAIN,
    val activeSelection: WallpaperSelection = settings.main,
    val activeCustomFile: File? = null
)

class WallpaperRepository(private val context: Context) {
    private val wallpaperDirectory = File(context.filesDir, "wallpapers")
    private val settings = context.wallpaperDataStore.data.map(::readSettings)

    val state: Flow<WallpaperState> = combine(settings, observeDayPeriod()) { settings, isDay ->
        val activeTarget = when {
            !settings.timeBasedEnabled -> WallpaperTarget.MAIN
            isDay -> WallpaperTarget.DAY
            else -> WallpaperTarget.NIGHT
        }
        val selection = settings.selection(activeTarget)
        val customFile = customFile(activeTarget).takeIf { selection == WallpaperSelection.Custom && it.isFile }
        if (selection == WallpaperSelection.Custom && customFile == null) {
            WallpaperState(
                settings = settings,
                activeTarget = activeTarget,
                activeSelection = WallpaperSelection.BuiltIn(BuiltInWallpaper.MIDNIGHT)
            )
        } else {
            WallpaperState(settings, activeTarget, selection, customFile)
        }
    }

    suspend fun setTimeBasedEnabled(enabled: Boolean) {
        context.wallpaperDataStore.edit { preferences -> preferences[TIME_BASED_ENABLED] = enabled }
    }

    suspend fun setBuiltIn(target: WallpaperTarget, wallpaper: BuiltInWallpaper) {
        context.wallpaperDataStore.edit { preferences ->
            preferences[target.preferenceKey] = wallpaper.name
        }
    }

    suspend fun importCustom(target: WallpaperTarget, source: Uri) = withContext(Dispatchers.IO) {
        wallpaperDirectory.mkdirs()
        val temporaryFile = File.createTempFile("wallpaper-", ".image", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "The selected image could not be opened" }
                temporaryFile.outputStream().use(input::copyTo)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporaryFile.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not an image" }

            val atomicFile = AtomicFile(customFile(target))
            val output = atomicFile.startWrite()
            try {
                temporaryFile.inputStream().use { input -> input.copyTo(output) }
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
            context.wallpaperDataStore.edit { preferences ->
                preferences[target.preferenceKey] = CUSTOM_SELECTION
                preferences[REVISION] = (preferences[REVISION] ?: 0L) + 1L
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private fun customFile(target: WallpaperTarget): File = File(
        wallpaperDirectory,
        when (target) {
            WallpaperTarget.MAIN -> "main.image"
            WallpaperTarget.DAY -> "day.image"
            WallpaperTarget.NIGHT -> "night.image"
        }
    )

    private fun readSettings(preferences: Preferences): WallpaperSettings = WallpaperSettings(
        timeBasedEnabled = preferences[TIME_BASED_ENABLED] ?: false,
        main = preferences.selection(MAIN_SELECTION, BuiltInWallpaper.MIDNIGHT),
        day = preferences.selection(DAY_SELECTION, BuiltInWallpaper.ARCTIC),
        night = preferences.selection(NIGHT_SELECTION, BuiltInWallpaper.MIDNIGHT),
        revision = preferences[REVISION] ?: 0L
    )

    private fun observeDayPeriod(): Flow<Boolean> = flow {
        while (true) {
            emit(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in DAY_START_HOUR until NIGHT_START_HOUR)
            delay(DAY_PERIOD_CHECK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    private val WallpaperTarget.preferenceKey: Preferences.Key<String>
        get() = when (this) {
            WallpaperTarget.MAIN -> MAIN_SELECTION
            WallpaperTarget.DAY -> DAY_SELECTION
            WallpaperTarget.NIGHT -> NIGHT_SELECTION
        }

    private companion object {
        const val CUSTOM_SELECTION = "CUSTOM"
        const val DAY_START_HOUR = 6
        const val NIGHT_START_HOUR = 18
        const val DAY_PERIOD_CHECK_INTERVAL_MS = 60_000L
        val TIME_BASED_ENABLED = booleanPreferencesKey("time_based_enabled")
        val MAIN_SELECTION = stringPreferencesKey("main_selection")
        val DAY_SELECTION = stringPreferencesKey("day_selection")
        val NIGHT_SELECTION = stringPreferencesKey("night_selection")
        val REVISION = longPreferencesKey("revision")
    }
}

private fun Preferences.selection(key: Preferences.Key<String>, default: BuiltInWallpaper): WallpaperSelection {
    val storedValue = this[key] ?: return WallpaperSelection.BuiltIn(default)
    if (storedValue == "CUSTOM") return WallpaperSelection.Custom
    return WallpaperSelection.BuiltIn(
        BuiltInWallpaper.entries.firstOrNull { it.name == storedValue } ?: default
    )
}
