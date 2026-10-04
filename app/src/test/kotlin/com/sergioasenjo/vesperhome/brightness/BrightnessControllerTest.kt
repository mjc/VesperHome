package com.sergioasenjo.vesperhome.brightness

import android.app.Activity
import android.app.AppOpsManager
import android.app.Application
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class BrightnessControllerTest {
    @Test
    fun unchangedPeriodsDoNotRerenderAndExternalBrightnessChangesAreCorrected() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        shadowOf(activity.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_WRITE_SETTINGS,
            Process.myUid(),
            activity.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        val scope = TestScope()
        val settings = BrightnessSettings(true, 50, 50, 50, 50, 50)
        var renders = 0
        val controller = BrightnessController(activity, scope, { settings }, {}, { _, _ -> }, { renders++ })
        assertTrue(controller.hasPermission())
        controller.render(settings)
        scope.runCurrent()
        assertEquals(128, Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(1, renders)
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertEquals(1, renders)

        Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 8)
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertEquals(128, Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        assertEquals(1, renders)

        controller.render(settings.copy(enabled = false))
        Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 8)
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertEquals(8, Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
        controller.release()
    }
}
