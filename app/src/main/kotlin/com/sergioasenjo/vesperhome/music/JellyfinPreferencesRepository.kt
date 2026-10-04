package com.sergioasenjo.vesperhome.music

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.jellyfinDataStore by preferencesDataStore(name = "jellyfin")

class JellyfinPreferencesRepository(private val context: Context) {
    val credentials: Flow<JellyfinCredentials?> = context.jellyfinDataStore.data.map {
        credentialsFrom(it, MusicProvider.JELLYFIN)
    }.distinctUntilChanged()
    val plexCredentials: Flow<JellyfinCredentials?> = context.jellyfinDataStore.data.map {
        credentialsFrom(it, MusicProvider.PLEX)
    }.distinctUntilChanged()
    val musicProvider: Flow<MusicProvider> = context.jellyfinDataStore.data.map {
        providerFrom(it)
    }.distinctUntilChanged()
    val musicCredentials: Flow<JellyfinCredentials?> = context.jellyfinDataStore.data.map {
        credentialsFrom(it, providerFrom(it))
    }.distinctUntilChanged()

    private fun providerFrom(preferences: Preferences): MusicProvider =
        MusicProvider.entries.firstOrNull { it.name == preferences[MUSIC_PROVIDER] } ?: MusicProvider.JELLYFIN

    private fun credentialsFrom(preferences: Preferences, provider: MusicProvider): JellyfinCredentials? {
        val plex = provider == MusicProvider.PLEX
        val baseUrl = preferences[if (plex) PLEX_URL else BASE_URL] ?: return null
        val token = preferences[if (plex) PLEX_TOKEN else ACCESS_TOKEN] ?: return null
        val name = preferences[if (plex) PLEX_NAME else SERVER_NAME] ?: return null
        val userId = if (plex) "" else preferences[USER_ID] ?: return null
        return JellyfinCredentials(baseUrl, token, userId, name, provider)
    }

    suspend fun savePlex(credentials: JellyfinCredentials) {
        require(credentials.provider == MusicProvider.PLEX)
        context.jellyfinDataStore.edit {
            it[PLEX_URL] = credentials.baseUrl
            it[PLEX_TOKEN] = credentials.accessToken
            it[PLEX_NAME] = credentials.serverName
            it[MUSIC_PROVIDER] = MusicProvider.PLEX.name
        }
    }

    suspend fun clearPlex() {
        context.jellyfinDataStore.edit {
            it.remove(PLEX_URL)
            it.remove(PLEX_TOKEN)
            it.remove(PLEX_NAME)
        }
    }

    suspend fun setMusicProvider(provider: MusicProvider) {
        context.jellyfinDataStore.edit { it[MUSIC_PROVIDER] = provider.name }
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
        val PLEX_URL = stringPreferencesKey("plex_url")
        val PLEX_TOKEN = stringPreferencesKey("plex_token")
        val PLEX_NAME = stringPreferencesKey("plex_name")
        val MUSIC_PROVIDER = stringPreferencesKey("music_provider")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val BASE_URL = stringPreferencesKey("base_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val NORMALIZATION_MODE = stringPreferencesKey("normalization_mode")
    }
}
