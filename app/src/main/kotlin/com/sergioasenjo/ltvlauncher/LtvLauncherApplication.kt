package com.sergioasenjo.ltvlauncher

import android.app.Application
import androidx.room.Room
import com.sergioasenjo.ltvlauncher.applications.ApplicationRepository
import com.sergioasenjo.ltvlauncher.applications.PlatformApplicationRepository
import com.sergioasenjo.ltvlauncher.data.AppPreferencesRepository
import com.sergioasenjo.ltvlauncher.data.LauncherDatabase

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
}
