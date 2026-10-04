package com.sergioasenjo.vesperhome

import android.app.Application
import androidx.room.Room
import com.sergioasenjo.vesperhome.about.DiagnosticsRepository
import com.sergioasenjo.vesperhome.applications.ApplicationRepository
import com.sergioasenjo.vesperhome.applications.ManagedApplicationsRepository
import com.sergioasenjo.vesperhome.applications.PlatformApplicationRepository
import com.sergioasenjo.vesperhome.backup.BackupRepository
import com.sergioasenjo.vesperhome.backup.SafetyBackupSettingsRepository
import com.sergioasenjo.vesperhome.data.AppPreferencesRepository
import com.sergioasenjo.vesperhome.data.CategoryRepository
import com.sergioasenjo.vesperhome.data.LauncherDatabase
import com.sergioasenjo.vesperhome.inputs.TvInputRepository
import com.sergioasenjo.vesperhome.livetv.LiveTvPluginRepository
import com.sergioasenjo.vesperhome.livetv.LiveTvPreferencesRepository
import com.sergioasenjo.vesperhome.media.ArrApiClient
import com.sergioasenjo.vesperhome.media.MediaSearchPreferencesRepository
import com.sergioasenjo.vesperhome.media.MediaSearchRepository
import com.sergioasenjo.vesperhome.media.TrackedMediaRepository
import com.sergioasenjo.vesperhome.music.JellyfinApiRepository
import com.sergioasenjo.vesperhome.music.JellyfinDiscoveryRepository
import com.sergioasenjo.vesperhome.music.JellyfinPreferencesRepository
import com.sergioasenjo.vesperhome.notifications.NotificationRepository
import com.sergioasenjo.vesperhome.platform.HomeRepository
import com.sergioasenjo.vesperhome.platform.PlatformHomeRepository
import com.sergioasenjo.vesperhome.profiles.ProfileRepository
import com.sergioasenjo.vesperhome.screensaver.DreamStateTracker
import com.sergioasenjo.vesperhome.screensaver.SystemScreensaverRepository
import com.sergioasenjo.vesperhome.security.PinRepository
import com.sergioasenjo.vesperhome.settings.LauncherSettingsRepository
import com.sergioasenjo.vesperhome.status.NetworkStatusRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingPreferencesRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingServerConfig
import com.sergioasenjo.vesperhome.update.ReleaseUpdateRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class VesperHomeApplication : Application() {
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
    val appPreferencesRepository = AppPreferencesRepository(application, database.appPreferenceDao())
    val managedApplicationsRepository = ManagedApplicationsRepository(applicationRepository, appPreferencesRepository)
    val categoryRepository = CategoryRepository(database.categoryDao())
    val homeRepository: HomeRepository = PlatformHomeRepository(application)
    val launcherSettingsRepository = LauncherSettingsRepository(application)
    val systemScreensaverRepository = SystemScreensaverRepository(application)
    val dreamStateTracker = DreamStateTracker(application)
    val wallpaperRepository = WallpaperRepository(application)
    val networkStatusRepository = NetworkStatusRepository(application)
    val tvInputRepository = TvInputRepository(application)
    val notificationRepository = NotificationRepository(application)
    private val json by lazy { Json { ignoreUnknownKeys = true } }
    private val httpClient by lazy { OkHttpClient() }
    val jellyfinPreferencesRepository by lazy { JellyfinPreferencesRepository(application) }
    val jellyfinDiscoveryRepository by lazy { JellyfinDiscoveryRepository(json) }
    val jellyfinApiRepository by lazy { JellyfinApiRepository(httpClient, json, jellyfinPreferencesRepository) }
    val liveTvPluginRepository by lazy { LiveTvPluginRepository(application, jellyfinPreferencesRepository) }
    val liveTvPreferencesRepository by lazy { LiveTvPreferencesRepository(application) }
    val upcomingPreferencesRepository by lazy {
        UpcomingPreferencesRepository(
            application,
            UpcomingServerConfig(
                BuildConfig.SONARR_URL,
                BuildConfig.SONARR_API_KEY,
                BuildConfig.RADARR_URL,
                BuildConfig.RADARR_API_KEY
            )
        )
    }
    val upcomingRepository by lazy { UpcomingRepository(httpClient, json, upcomingPreferencesRepository.config) }
    private val arrApiClient by lazy { ArrApiClient(httpClient, json, upcomingPreferencesRepository) }
    private val mediaSearchPreferencesRepository by lazy { MediaSearchPreferencesRepository(application) }
    val mediaSearchRepository by lazy {
        MediaSearchRepository(arrApiClient, mediaSearchPreferencesRepository, database.trackedMediaDao())
    }
    val trackedMediaRepository by lazy { TrackedMediaRepository(arrApiClient, database.trackedMediaDao()) }
    val backupRepository by lazy {
        BackupRepository(application, database, launcherSettingsRepository, wallpaperRepository, json)
    }
    val safetyBackupSettingsRepository by lazy { SafetyBackupSettingsRepository(application) }
    val profileRepository by lazy { ProfileRepository(application, backupRepository, json) }
    val pinRepository by lazy { PinRepository(application) }
    val releaseUpdateRepository by lazy {
        ReleaseUpdateRepository(httpClient, json, BuildConfig.VERSION_NAME)
    }
    val diagnosticsRepository = DiagnosticsRepository(application, homeRepository)
}
