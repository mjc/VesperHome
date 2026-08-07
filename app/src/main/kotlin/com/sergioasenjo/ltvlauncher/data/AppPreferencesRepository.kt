package com.sergioasenjo.ltvlauncher.data

import com.sergioasenjo.ltvlauncher.applications.LauncherApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferencesRepository(private val appPreferenceDao: AppPreferenceDao) {
    fun observePreferences(): Flow<Map<String, AppPreferenceEntity>> =
        appPreferenceDao.observeAll().map { preferences ->
            preferences.associateBy { it.componentName.substringBefore('/') }
        }

    suspend fun setFavorite(app: LauncherApp, isFavorite: Boolean) {
        appPreferenceDao.setFavorite(app.packageName, isFavorite)
    }

    suspend fun setHidden(app: LauncherApp, isHidden: Boolean) {
        appPreferenceDao.setHidden(app.packageName, isHidden)
    }
}
