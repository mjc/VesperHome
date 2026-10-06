package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.content.Intent
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.accessibility.HomeButtonFixController
import com.sergioasenjo.vesperhome.applications.HiddenAppsActivity
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.categories.CategoryManagementActivity
import com.sergioasenjo.vesperhome.music.MediaServicesSetupActivity
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.security.PinController
import com.sergioasenjo.vesperhome.security.PinProtectedArea
import com.sergioasenjo.vesperhome.settings.LauncherSettingsAction
import com.sergioasenjo.vesperhome.update.ReleaseUpdateController

class LauncherSettingsActionController(
    private val activity: Activity,
    private val viewModel: LauncherViewModel,
    private val homeButtonFixController: HomeButtonFixController,
    private val backupController: BackupController,
    private val aboutController: AboutController,
    private val profileController: ProfileController,
    private val pinController: PinController,
    private val releaseUpdateController: ReleaseUpdateController,
    private val showSortDialog: () -> Unit
) {
    fun handle(action: LauncherSettingsAction) {
        when (action) {
            LauncherSettingsAction.SET_DEFAULT_HOME -> viewModel.requestDefaultLauncher()

            LauncherSettingsAction.OPEN_HOME_BUTTON_FIX -> homeButtonFixController.openAccessibilitySettings()

            LauncherSettingsAction.BACKUP_AND_RESTORE -> pinController.authorize(
                PinProtectedArea.BACKUPS_AND_PROFILES,
                backupController::show
            )

            LauncherSettingsAction.MANAGE_PROFILES -> pinController.authorize(
                PinProtectedArea.BACKUPS_AND_PROFILES,
                profileController::show
            )

            LauncherSettingsAction.PIN_AND_SECURITY -> pinController.showSettings()

            LauncherSettingsAction.CHECK_FOR_UPDATES -> releaseUpdateController.show()

            LauncherSettingsAction.ABOUT_AND_DIAGNOSTICS -> aboutController.show()

            LauncherSettingsAction.OPEN_SYSTEM_SETTINGS -> viewModel.openSystemSettings()

            LauncherSettingsAction.MANAGE_CATEGORIES -> protectAppManagement(CategoryManagementActivity::class.java)

            LauncherSettingsAction.MANAGE_HIDDEN_APPS -> protectAppManagement(HiddenAppsActivity::class.java)

            LauncherSettingsAction.SORT_APPLICATIONS -> pinController.authorizeAppManagement(showSortDialog)

            LauncherSettingsAction.SETUP_MEDIA_SERVICES -> pinController.authorize(
                PinProtectedArea.MEDIA_INTEGRATIONS
            ) {
                activity.open(MediaServicesSetupActivity::class.java)
            }
        }
    }

    private fun Activity.open(destination: Class<out Activity>) {
        startActivity(Intent(this, destination))
    }

    private fun protectAppManagement(destination: Class<out Activity>) {
        pinController.authorizeAppManagement { activity.open(destination) }
    }
}
