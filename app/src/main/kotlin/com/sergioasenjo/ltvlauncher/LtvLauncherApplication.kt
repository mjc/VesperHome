package com.sergioasenjo.ltvlauncher

import android.app.Application
import androidx.room.Room
import com.sergioasenjo.ltvlauncher.applications.ApplicationRepository
import com.sergioasenjo.ltvlauncher.applications.ManagedApplicationsRepository
import com.sergioasenjo.ltvlauncher.applications.PlatformApplicationRepository
import com.sergioasenjo.ltvlauncher.data.AppPreferencesRepository
import com.sergioasenjo.ltvlauncher.data.CategoryRepository
import com.sergioasenjo.ltvlauncher.data.LauncherDatabase
import com.sergioasenjo.ltvlauncher.music.JellyfinApiRepository
import com.sergioasenjo.ltvlauncher.music.JellyfinDiscoveryRepository
import com.sergioasenjo.ltvlauncher.music.JellyfinPreferencesRepository
import com.sergioasenjo.ltvlauncher.platform.HomeRepository
import com.sergioasenjo.ltvlauncher.platform.PlatformHomeRepository
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class LtvLauncherApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = Room.databaseBuilder(
        application,
        LauncherDatabase::class.java,
        "launcher.db"
    ).build()

    val applicationRepository: ApplicationRepository = PlatformApplicationRepository(application)
    val appPreferencesRepository = AppPreferencesRepository(database.appPreferenceDao())
    val managedApplicationsRepository = ManagedApplicationsRepository(applicationRepository, appPreferencesRepository)
    val categoryRepository = CategoryRepository(database.categoryDao())
    val homeRepository: HomeRepository = PlatformHomeRepository(application)
    val launcherSettingsRepository = LauncherSettingsRepository(application)
    private val json by lazy { Json { ignoreUnknownKeys = true } }
    private val httpClient by lazy { OkHttpClient() }
    val jellyfinPreferencesRepository by lazy { JellyfinPreferencesRepository(application) }
    val jellyfinDiscoveryRepository by lazy { JellyfinDiscoveryRepository(json) }
    val jellyfinApiRepository by lazy { JellyfinApiRepository(httpClient, json, jellyfinPreferencesRepository) }
}
