package com.sergioasenjo.vesperhome.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import com.sergioasenjo.vesperhome.applications.LauncherApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AppPreferencesRepository(context: Context, private val appPreferenceDao: AppPreferenceDao) {
    private val applicationContext = context.applicationContext
    private val customBannerDirectory = File(applicationContext.filesDir, "custom_banners")

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

    suspend fun setManualOrder(apps: List<LauncherApp>) {
        appPreferenceDao.setManualOrder(apps.map(LauncherApp::packageName))
    }

    suspend fun recordLaunch(app: LauncherApp) {
        appPreferenceDao.setLastUsedAt(app.packageName, System.currentTimeMillis())
    }

    fun customBannerFile(packageName: String): File = File(customBannerDirectory, "$packageName.image")

    suspend fun importCustomBanner(app: LauncherApp, source: Uri) = withContext(Dispatchers.IO) {
        customBannerDirectory.mkdirs()
        val temporaryFile = File.createTempFile("custom-banner-", ".image", applicationContext.cacheDir)
        try {
            applicationContext.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "The selected image could not be opened" }
                temporaryFile.outputStream().use(input::copyTo)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporaryFile.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not an image" }

            val atomicFile = AtomicFile(customBannerFile(app.packageName))
            val output = atomicFile.startWrite()
            try {
                temporaryFile.inputStream().use { input -> input.copyTo(output) }
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
            appPreferenceDao.setCustomBanner(app.packageName, present = true)
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun removeCustomBanner(app: LauncherApp) = withContext(Dispatchers.IO) {
        customBannerFile(app.packageName).delete()
        appPreferenceDao.setCustomBanner(app.packageName, present = false)
    }

    suspend fun removeCustomBannersForMissingPackages(
        installedPackages: Set<String>,
        preferences: Collection<AppPreferenceEntity>
    ) = withContext(Dispatchers.IO) {
        preferences.asSequence()
            .filter { it.customBannerRevision != null && it.componentName.substringBefore('/') !in installedPackages }
            .forEach { preference ->
                val packageName = preference.componentName.substringBefore('/')
                customBannerFile(packageName).delete()
                appPreferenceDao.setCustomBanner(packageName, present = false)
            }
    }
}
