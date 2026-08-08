package com.sergioasenjo.ltvlauncher.launcher

import android.app.Activity
import android.content.Intent
import com.sergioasenjo.ltvlauncher.about.AboutController
import com.sergioasenjo.ltvlauncher.accessibility.HomeButtonFixController
import com.sergioasenjo.ltvlauncher.applications.HiddenAppsActivity
import com.sergioasenjo.ltvlauncher.backup.BackupController
import com.sergioasenjo.ltvlauncher.categories.CategoryManagementActivity
import com.sergioasenjo.ltvlauncher.music.JellyfinSetupActivity
import com.sergioasenjo.ltvlauncher.settings.LauncherSettingsAction

class LauncherSettingsActionController(
    private val activity: Activity,
    private val viewModel: LauncherViewModel,
    private val homeButtonFixController: HomeButtonFixController,
    private val backupController: BackupController,
    private val aboutController: AboutController,
    private val showSortDialog: () -> Unit
) {
    fun handle(action: LauncherSettingsAction) {
        when (action) {
            LauncherSettingsAction.SET_DEFAULT_HOME -> viewModel.requestDefaultLauncher()
            LauncherSettingsAction.OPEN_HOME_BUTTON_FIX -> homeButtonFixController.openAccessibilitySettings()
            LauncherSettingsAction.BACKUP_AND_RESTORE -> backupController.show()
            LauncherSettingsAction.ABOUT_AND_DIAGNOSTICS -> aboutController.show()
            LauncherSettingsAction.OPEN_SYSTEM_SETTINGS -> viewModel.openSystemSettings()
            LauncherSettingsAction.MANAGE_CATEGORIES -> activity.open(CategoryManagementActivity::class.java)
            LauncherSettingsAction.MANAGE_HIDDEN_APPS -> activity.open(HiddenAppsActivity::class.java)
            LauncherSettingsAction.SORT_APPLICATIONS -> showSortDialog()
            LauncherSettingsAction.SETUP_JELLYFIN -> activity.open(JellyfinSetupActivity::class.java)
        }
    }

    private fun Activity.open(destination: Class<out Activity>) {
        startActivity(Intent(this, destination))
    }
}
