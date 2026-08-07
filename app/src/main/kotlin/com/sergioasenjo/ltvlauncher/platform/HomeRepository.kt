package com.sergioasenjo.ltvlauncher.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

interface HomeRepository {
    fun isDefaultLauncher(): Boolean

    fun createDefaultLauncherIntent(): Intent

    fun createSystemSettingsIntent(): Intent
}

class PlatformHomeRepository(private val context: Context) : HomeRepository {
    private val packageManager = context.packageManager

    override fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome = packageManager.resolveActivity(homeIntent, 0)
        return resolvedHome?.activityInfo?.packageName == context.packageName
    }

    override fun createDefaultLauncherIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }

        return resolvableIntent(Settings.ACTION_HOME_SETTINGS)
    }

    override fun createSystemSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)

    private fun resolvableIntent(action: String): Intent {
        val intent = Intent(action)
        return if (intent.resolveActivity(packageManager) != null) {
            intent
        } else {
            createSystemSettingsIntent()
        }
    }
}
