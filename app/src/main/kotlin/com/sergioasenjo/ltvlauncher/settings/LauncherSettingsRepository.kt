package com.sergioasenjo.ltvlauncher.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherSettingsDataStore by preferencesDataStore(name = "launcher_settings")

class LauncherSettingsRepository(private val context: Context) {
    val applicationSortMode: Flow<ApplicationSortMode> = context.launcherSettingsDataStore.data.map { preferences ->
        preferences[APPLICATION_SORT_MODE]
            ?.let { storedValue -> ApplicationSortMode.entries.firstOrNull { it.name == storedValue } }
            ?: ApplicationSortMode.MANUAL
    }

    suspend fun setApplicationSortMode(sortMode: ApplicationSortMode) {
        context.launcherSettingsDataStore.edit { preferences ->
            preferences[APPLICATION_SORT_MODE] = sortMode.name
        }
    }

    private companion object {
        val APPLICATION_SORT_MODE = stringPreferencesKey("application_sort_mode")
    }
}
