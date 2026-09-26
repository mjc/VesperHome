package com.sergioasenjo.vesperhome.livetv

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liveTvDataStore by preferencesDataStore(name = "live_tv")

class LiveTvPreferencesRepository(private val context: Context) {
    val lastChannel: Flow<LastLiveTvChannel?> = context.liveTvDataStore.data.map { preferences ->
        val plugin = preferences[LAST_PLUGIN]?.let(ComponentName::unflattenFromString)
        val channelId = preferences[LAST_CHANNEL_ID]
        if (plugin != null && channelId != null) LastLiveTvChannel(plugin, channelId) else null
    }

    suspend fun setLastChannel(channel: LiveTvChannel) {
        context.liveTvDataStore.edit { preferences ->
            preferences[LAST_PLUGIN] = channel.plugin.flattenToString()
            preferences[LAST_CHANNEL_ID] = channel.id
        }
    }

    private companion object {
        val LAST_PLUGIN = stringPreferencesKey("last_plugin")
        val LAST_CHANNEL_ID = stringPreferencesKey("last_channel_id")
    }
}
