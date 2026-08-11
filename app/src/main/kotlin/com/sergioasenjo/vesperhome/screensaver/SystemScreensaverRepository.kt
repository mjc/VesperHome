package com.sergioasenjo.vesperhome.screensaver

import android.content.Context
import android.provider.Settings

class SystemScreensaverRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun applyStartDelay(delay: ScreensaverStartDelay): Boolean = try {
        Settings.System.putLong(resolver, Settings.System.SCREEN_OFF_TIMEOUT, delay.milliseconds.toLong())
    } catch (_: SecurityException) {
        false
    }
}
