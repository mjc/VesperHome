package com.sergioasenjo.vesperhome.upcoming

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.upcomingDataStore by preferencesDataStore(name = "upcoming_services")

class UpcomingPreferencesRepository(private val context: Context, private val defaults: UpcomingServerConfig) {
    val config: Flow<UpcomingServerConfig> = context.upcomingDataStore.data.map { preferences ->
        UpcomingServerConfig(
            sonarrUrl = preferences[SONARR_URL] ?: defaults.sonarrUrl,
            sonarrApiKey = preferences[SONARR_API_KEY] ?: defaults.sonarrApiKey,
            radarrUrl = preferences[RADARR_URL] ?: defaults.radarrUrl,
            radarrApiKey = preferences[RADARR_API_KEY] ?: defaults.radarrApiKey,
            player = UpcomingPlayer.entries.firstOrNull { it.name == preferences[PLAYER] } ?: UpcomingPlayer.AUTO
        )
    }

    suspend fun saveSonarr(url: String, apiKey: String) {
        context.upcomingDataStore.edit { preferences ->
            preferences[SONARR_URL] = url
            preferences[SONARR_API_KEY] = apiKey
        }
    }

    suspend fun saveRadarr(url: String, apiKey: String) {
        context.upcomingDataStore.edit { preferences ->
            preferences[RADARR_URL] = url
            preferences[RADARR_API_KEY] = apiKey
        }
    }

    suspend fun setPlayer(player: UpcomingPlayer) {
        context.upcomingDataStore.edit { it[PLAYER] = player.name }
    }

    private companion object {
        val SONARR_URL = stringPreferencesKey("sonarr_url")
        val SONARR_API_KEY = stringPreferencesKey("sonarr_api_key")
        val RADARR_URL = stringPreferencesKey("radarr_url")
        val RADARR_API_KEY = stringPreferencesKey("radarr_api_key")
        val PLAYER = stringPreferencesKey("player")
    }
}
