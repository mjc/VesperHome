package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.content.Intent
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.accessibility.HomeButtonFixController
import com.sergioasenjo.vesperhome.applications.HiddenAppsActivity
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.categories.CategoryManagementActivity
import com.sergioasenjo.vesperhome.music.JellyfinSetupActivity
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.settings.LauncherSettingsAction

class LauncherSettingsActionController(
    private val activity: Activity,
    private val viewModel: LauncherViewModel,
    private val homeButtonFixController: HomeButtonFixController,
    private val backupController: BackupController,
    private val aboutController: AboutController,
    private val profileController: ProfileController,
    private val showSortDialog: () -> Unit
) {
    fun handle(action: LauncherSettingsAction) {
        when (action) {
            LauncherSettingsAction.SET_DEFAULT_HOME -> viewModel.requestDefaultLauncher()
            LauncherSettingsAction.OPEN_HOME_BUTTON_FIX -> homeButtonFixController.openAccessibilitySettings()
            LauncherSettingsAction.BACKUP_AND_RESTORE -> backupController.show()
            LauncherSettingsAction.MANAGE_PROFILES -> profileController.show()
            LauncherSettingsAction.ABOUT_AND_DIAGNOSTICS -> aboutController.show()
            LauncherSettingsAction.OPEN_SYSTEM_SETTINGS -> viewModel.openSystemSettings()
            LauncherSettingsAction.MANAGE_CATEGORIES -> activity.open(CategoryManagementActivity::class.java)
            LauncherSettingsAction.MANAGE_HIDDEN_APPS -> activity.open(HiddenAppsActivity::class.java)
            LauncherSettingsAction.SORT_APPLICATIONS -> showSortDialog()
            LauncherSettingsAction.SETUP_MEDIA_SERVICES -> activity.open(JellyfinSetupActivity::class.java)
        }
    }

    private fun Activity.open(destination: Class<out Activity>) {
        startActivity(Intent(this, destination))
    }
}
