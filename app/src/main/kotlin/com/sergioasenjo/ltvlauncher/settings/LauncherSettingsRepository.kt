package com.sergioasenjo.ltvlauncher.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherSettingsDataStore by preferencesDataStore(name = "launcher_settings")

enum class LauncherTheme {
    DARK,
    LIGHT
}

data class LauncherAppearance(
    val theme: LauncherTheme = LauncherTheme.DARK,
    val showAppNames: Boolean = true,
    val showCategoryTitles: Boolean = true,
    val showFocusOutline: Boolean = true,
    val appCardFocusAnimations: Boolean = true,
    val selectorTransitionAnimations: Boolean = true,
    val keyClickSounds: Boolean = true
) {
    val palette: LauncherPalette
        get() = when (theme) {
            LauncherTheme.DARK -> LauncherPalette.DARK
            LauncherTheme.LIGHT -> LauncherPalette.LIGHT
        }
}

data class LauncherPalette(
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val surface: Int,
    val focusedSurface: Int,
    val focusedText: Int,
    val focus: Int,
    val stroke: Int,
    val panel: Int
) {
    companion object {
        val DARK = LauncherPalette(
            backgroundStart = 0xFF18212B.toInt(),
            backgroundEnd = 0xFF080A0E.toInt(),
            primaryText = 0xFFF7F9FC.toInt(),
            secondaryText = 0xFFAEB8C4.toInt(),
            surface = 0xFF263340.toInt(),
            focusedSurface = 0xFFF7F9FC.toInt(),
            focusedText = 0xFF111820.toInt(),
            focus = 0xFF8EC5FF.toInt(),
            stroke = 0x55FFFFFF,
            panel = 0xFF1E252D.toInt()
        )
        val LIGHT = LauncherPalette(
            backgroundStart = 0xFFF7FAFC.toInt(),
            backgroundEnd = 0xFFDCE7EF.toInt(),
            primaryText = 0xFF17212B.toInt(),
            secondaryText = 0xFF5D6975.toInt(),
            surface = 0xFFE5ECF2.toInt(),
            focusedSurface = 0xFF17212B.toInt(),
            focusedText = 0xFFF7F9FC.toInt(),
            focus = 0xFF2563A8.toInt(),
            stroke = 0x4017212B,
            panel = 0xFFF7FAFC.toInt()
        )
    }
}

data class LauncherSettings(
    val applicationSortMode: ApplicationSortMode = ApplicationSortMode.MANUAL,
    val appearance: LauncherAppearance = LauncherAppearance()
)

class LauncherSettingsRepository(private val context: Context) {
    val settings: Flow<LauncherSettings> = context.launcherSettingsDataStore.data.map { preferences ->
        LauncherSettings(
            applicationSortMode = preferences.enumValue(APPLICATION_SORT_MODE, ApplicationSortMode.MANUAL),
            appearance = LauncherAppearance(
                theme = preferences.enumValue(THEME, LauncherTheme.DARK),
                showAppNames = preferences[SHOW_APP_NAMES] ?: true,
                showCategoryTitles = preferences[SHOW_CATEGORY_TITLES] ?: true,
                showFocusOutline = preferences[SHOW_FOCUS_OUTLINE] ?: true,
                appCardFocusAnimations = preferences[APP_CARD_FOCUS_ANIMATIONS] ?: true,
                selectorTransitionAnimations = preferences[SELECTOR_TRANSITION_ANIMATIONS] ?: true,
                keyClickSounds = preferences[KEY_CLICK_SOUNDS] ?: true
            )
        )
    }
    val applicationSortMode: Flow<ApplicationSortMode> = settings.map { it.applicationSortMode }
    val appearance: Flow<LauncherAppearance> = settings.map { it.appearance }

    suspend fun setApplicationSortMode(sortMode: ApplicationSortMode) {
        setEnum(APPLICATION_SORT_MODE, sortMode)
    }

    suspend fun setTheme(theme: LauncherTheme) {
        setEnum(THEME, theme)
    }

    suspend fun setShowAppNames(show: Boolean) {
        setBoolean(SHOW_APP_NAMES, show)
    }

    suspend fun setShowCategoryTitles(show: Boolean) {
        setBoolean(SHOW_CATEGORY_TITLES, show)
    }

    suspend fun setShowFocusOutline(show: Boolean) {
        setBoolean(SHOW_FOCUS_OUTLINE, show)
    }

    suspend fun setAppCardFocusAnimations(enabled: Boolean) {
        setBoolean(APP_CARD_FOCUS_ANIMATIONS, enabled)
    }

    suspend fun setSelectorTransitionAnimations(enabled: Boolean) {
        setBoolean(SELECTOR_TRANSITION_ANIMATIONS, enabled)
    }

    suspend fun setKeyClickSounds(enabled: Boolean) {
        setBoolean(KEY_CLICK_SOUNDS, enabled)
    }

    private suspend fun <T : Enum<T>> setEnum(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: T
    ) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value.name }
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value }
    }

    private companion object {
        val APPLICATION_SORT_MODE = stringPreferencesKey("application_sort_mode")
        val THEME = stringPreferencesKey("theme")
        val SHOW_APP_NAMES = booleanPreferencesKey("show_app_names")
        val SHOW_CATEGORY_TITLES = booleanPreferencesKey("show_category_titles")
        val SHOW_FOCUS_OUTLINE = booleanPreferencesKey("show_focus_outline")
        val APP_CARD_FOCUS_ANIMATIONS = booleanPreferencesKey("app_card_focus_animations")
        val SELECTOR_TRANSITION_ANIMATIONS = booleanPreferencesKey("selector_transition_animations")
        val KEY_CLICK_SOUNDS = booleanPreferencesKey("key_click_sounds")
    }
}

private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enumValue(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    defaultValue: T
): T = this[key]?.let { storedValue -> enumValues<T>().firstOrNull { it.name == storedValue } } ?: defaultValue
