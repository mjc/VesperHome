package com.sergioasenjo.ltvlauncher

import android.app.Application
import com.sergioasenjo.ltvlauncher.applications.ApplicationRepository
import com.sergioasenjo.ltvlauncher.applications.PlatformApplicationRepository

class LtvLauncherApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            applicationRepository = PlatformApplicationRepository(this),
        )
    }
}

class AppContainer(
    val applicationRepository: ApplicationRepository,
)
