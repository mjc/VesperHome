package com.sergioasenjo.ltvlauncher.data

import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferencesRepository(private val appPreferenceDao: AppPreferenceDao) {
    fun observePreferences(): Flow<Map<String, AppPreferenceEntity>> =
        appPreferenceDao.observeAll().map { preferences ->
            preferences.associateBy(AppPreferenceEntity::componentName)
        }

    suspend fun setFavorite(app: LauncherApp, isFavorite: Boolean) {
        appPreferenceDao.setFavorite(app.preferenceKey, isFavorite)
    }

    suspend fun setHidden(app: LauncherApp, isHidden: Boolean) {
        appPreferenceDao.setHidden(app.preferenceKey, isHidden)
    }

    private val LauncherApp.preferenceKey: String
        get() = componentName.flattenToString()
}
