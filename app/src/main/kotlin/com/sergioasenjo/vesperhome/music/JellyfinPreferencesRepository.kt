package com.sergioasenjo.vesperhome.music

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.jellyfinDataStore by preferencesDataStore(name = "jellyfin")

class JellyfinPreferencesRepository(private val context: Context) {
    val credentials: Flow<JellyfinCredentials?> = context.jellyfinDataStore.data.map { preferences ->
        val baseUrl = preferences[BASE_URL]
        val accessToken = preferences[ACCESS_TOKEN]
        val userId = preferences[USER_ID]
        val serverName = preferences[SERVER_NAME]
        if (baseUrl != null && accessToken != null && userId != null && serverName != null) {
            JellyfinCredentials(baseUrl, accessToken, userId, serverName)
        } else {
            null
        }
    }
    val normalizationMode: Flow<JellyfinNormalizationMode> = context.jellyfinDataStore.data.map { preferences ->
        preferences[NORMALIZATION_MODE]
            ?.let { stored -> JellyfinNormalizationMode.entries.firstOrNull { it.name == stored } }
            ?: JellyfinNormalizationMode.OFF
    }

    suspend fun deviceId(): String {
        context.jellyfinDataStore.data.first()[DEVICE_ID]?.let { return it }
        val deviceId = UUID.randomUUID().toString()
        context.jellyfinDataStore.edit { it[DEVICE_ID] = deviceId }
        return deviceId
    }

    suspend fun save(server: JellyfinServer, authentication: AuthenticationResult) {
        context.jellyfinDataStore.edit { preferences ->
            preferences[BASE_URL] = server.baseUrl
            preferences[ACCESS_TOKEN] = authentication.AccessToken
            preferences[USER_ID] = authentication.User.Id
            preferences[SERVER_NAME] = server.Name
        }
    }

    suspend fun clear() {
        context.jellyfinDataStore.edit { preferences ->
            preferences.remove(BASE_URL)
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(USER_ID)
            preferences.remove(SERVER_NAME)
        }
    }

    suspend fun setNormalizationMode(mode: JellyfinNormalizationMode) {
        context.jellyfinDataStore.edit { it[NORMALIZATION_MODE] = mode.name }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val BASE_URL = stringPreferencesKey("base_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val NORMALIZATION_MODE = stringPreferencesKey("normalization_mode")
    }
}
