package com.sergioasenjo.vesperhome.media

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.mediaSearchDataStore by preferencesDataStore(name = "media_search")

class MediaSearchPreferencesRepository(private val context: Context) {
    suspend fun defaults(provider: MediaProvider): MediaProviderDefaults {
        val values = context.mediaSearchDataStore.data.first()
        val prefix = provider.name.lowercase()
        return MediaProviderDefaults(
            rootFolderId = values[intPreferencesKey("${prefix}_root_folder")],
            qualityProfileId = values[intPreferencesKey("${prefix}_quality_profile")],
            monitor = values[stringPreferencesKey("${prefix}_monitor")],
            seriesType = values[stringPreferencesKey("${prefix}_series_type")]
                ?.let { value -> SeriesType.entries.firstOrNull { it.apiValue == value } }
                ?: SeriesType.STANDARD,
            seasonFolder = values[booleanPreferencesKey("${prefix}_season_folder")] ?: true,
            minimumAvailability = values[stringPreferencesKey("${prefix}_minimum_availability")]
                ?.let { value -> MinimumAvailability.entries.firstOrNull { it.apiValue == value } }
                ?: MinimumAvailability.RELEASED,
            searchMissing = values[booleanPreferencesKey("${prefix}_search_missing")] ?: true,
            searchCutoffUnmet = values[booleanPreferencesKey("${prefix}_search_cutoff")] ?: false
        )
    }

    suspend fun save(provider: MediaProvider, selection: MediaAddSelection) {
        val prefix = provider.name.lowercase()
        context.mediaSearchDataStore.edit { values ->
            values[intPreferencesKey("${prefix}_root_folder")] = selection.rootFolderId
            values[intPreferencesKey("${prefix}_quality_profile")] = selection.qualityProfileId
            values[stringPreferencesKey("${prefix}_monitor")] = selection.monitor
            values[stringPreferencesKey("${prefix}_series_type")] = selection.seriesType.apiValue
            values[booleanPreferencesKey("${prefix}_season_folder")] = selection.seasonFolder
            values[stringPreferencesKey("${prefix}_minimum_availability")] = selection.minimumAvailability.apiValue
            values[booleanPreferencesKey("${prefix}_search_missing")] = selection.searchMissing
            values[booleanPreferencesKey("${prefix}_search_cutoff")] = selection.searchCutoffUnmet
        }
    }
}
