package com.sergioasenjo.ltvlauncher.applications

import android.content.Intent
import android.net.Uri
import com.sergioasenjo.ltvlauncher.data.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManagedApplicationsRepository(
    private val applicationRepository: ApplicationRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {
    fun observeApplications(): Flow<List<LauncherApp>> = combine(
        applicationRepository.observeApplications(),
        appPreferencesRepository.observePreferences()
    ) { apps, preferences ->
        if (apps.isNotEmpty()) {
            appPreferencesRepository.removeCustomBannersForMissingPackages(
                apps.mapTo(mutableSetOf(), LauncherApp::packageName),
                preferences.values
            )
        }
        apps.map { app ->
            val preference = preferences[app.packageName]
            val customBannerFile = preference?.customBannerRevision
                ?.let { appPreferencesRepository.customBannerFile(app.packageName) }
                ?.takeIf { it.isFile }
            app.copy(
                customBannerFile = customBannerFile,
                customBannerRevision = customBannerFile?.let { preference.customBannerRevision },
                isFavorite = preference?.isFavorite == true,
                isHidden = preference?.isHidden == true,
                manualOrder = preference?.manualOrder,
                lastUsedAt = preference?.lastUsedAt
            )
        }
    }

    fun launch(app: LauncherApp): Boolean = applicationRepository.launch(app.componentName, app.user)

    fun createApplicationDetailsIntent(app: LauncherApp): Intent =
        applicationRepository.createApplicationDetailsIntent(app.packageName)

    fun createUninstallIntent(app: LauncherApp): Intent = applicationRepository.createUninstallIntent(app.packageName)

    suspend fun setFavorite(app: LauncherApp, isFavorite: Boolean) {
        appPreferencesRepository.setFavorite(app, isFavorite)
    }

    suspend fun setHidden(app: LauncherApp, isHidden: Boolean) {
        appPreferencesRepository.setHidden(app, isHidden)
    }

    suspend fun setManualOrder(apps: List<LauncherApp>) {
        appPreferencesRepository.setManualOrder(apps)
    }

    suspend fun recordLaunch(app: LauncherApp) {
        appPreferencesRepository.recordLaunch(app)
    }

    suspend fun importCustomBanner(app: LauncherApp, source: Uri) {
        appPreferencesRepository.importCustomBanner(app, source)
    }

    suspend fun removeCustomBanner(app: LauncherApp) {
        appPreferencesRepository.removeCustomBanner(app)
    }
}
