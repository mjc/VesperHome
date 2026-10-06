package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.widget.TextView
import com.sergioasenjo.vesperhome.AppContainer
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.about.AboutController
import com.sergioasenjo.vesperhome.backup.BackupController
import com.sergioasenjo.vesperhome.brightness.BrightnessController
import com.sergioasenjo.vesperhome.brightness.BrightnessSettings
import com.sergioasenjo.vesperhome.profiles.ProfileController
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import com.sergioasenjo.vesperhome.update.ReleaseUpdateController
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LauncherPanelsTest {
    class CountingActivity : Activity() {
        var inflaterRequests = 0

        override fun getSystemService(name: String): Any? {
            if (name == Context.LAYOUT_INFLATER_SERVICE) inflaterRequests++
            return super.getSystemService(name)
        }
    }

    @Test
    fun dormantPanelsAreNotInflatedByRenderingOrCleanup() {
        val activity = Robolectric.buildActivity(CountingActivity::class.java).create().get()
        activity.setTheme(R.style.Theme_VesperHome)
        val container = AppContainer(RuntimeEnvironment.getApplication())
        val scope = TestScope()
        val appearance = { LauncherAppearance() }
        activity.inflaterRequests = 0
        val host = settingsHost(activity, scope)
        val backup = BackupController(
            activity,
            scope,
            container.backupRepository,
            container.safetyBackupSettingsRepository,
            appearance,
            {},
            {}
        )
        val profile = ProfileController(
            activity,
            scope,
            container.profileRepository,
            container.launcherSettingsRepository,
            { LauncherSettings() },
            appearance,
            {}
        )
        val about = AboutController(activity, container.diagnosticsRepository, appearance, {})
        val update = ReleaseUpdateController(activity, scope, container.releaseUpdateRepository, appearance, {})
        assertNull(host.panel)
        backup.renderAppearance()
        profile.render(LauncherSettings(), appearance())
        about.refresh()
        update.renderAppearance()
        host.release()
        backup.release()
        profile.release()
        about.release()
        update.release()
        assertEquals("Unopened panels must never request a LayoutInflater", 0, activity.inflaterRequests)
    }

    @Test
    fun firstOpenRendersCurrentSettingsAndRestoredPage() {
        val activity = Robolectric.buildActivity(CountingActivity::class.java).create().get()
        activity.setTheme(R.style.Theme_VesperHome)
        val host = settingsHost(activity, TestScope())
        val state =
            LauncherUiState(isDefaultLauncher = true, appearance = LauncherAppearance(theme = LauncherTheme.LIGHT))
        assertNull(host.panel)
        host.show(state)
        val panel = requireNotNull(host.panel)
        assertTrue(panel.isShowing)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        assertEquals(
            activity.getString(R.string.home_status_default),
            dialog.findViewById<TextView>(R.id.homeStatus).text
        )
        host.show(state, com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage.BRIGHTNESS)
        assertEquals(com.sergioasenjo.vesperhome.settings.LauncherSettingsPanelPage.BRIGHTNESS, panel.currentPage)
        host.release()
        assertFalse(panel.isShowing)
    }

    @Test
    fun otherPanelsOpenOnDemandAndKeepDismissCallbacks() {
        val activity = Robolectric.buildActivity(CountingActivity::class.java).create().get()
        activity.setTheme(R.style.Theme_VesperHome)
        val container = AppContainer(RuntimeEnvironment.getApplication())
        val scope = TestScope()
        val appearance = { LauncherAppearance(theme = LauncherTheme.LIGHT) }
        var dismissals = 0
        val dismissed = {
            dismissals++
            Unit
        }
        val backup = BackupController(
            activity,
            scope,
            container.backupRepository,
            container.safetyBackupSettingsRepository,
            appearance,
            {},
            dismissed
        )
        val profile = ProfileController(
            activity,
            scope,
            container.profileRepository,
            container.launcherSettingsRepository,
            { LauncherSettings(showComingNext = false) },
            appearance,
            dismissed
        )
        val about = AboutController(activity, container.diagnosticsRepository, appearance, dismissed)
        val update = ReleaseUpdateController(activity, scope, container.releaseUpdateRepository, appearance, dismissed)
        val panels = listOf(
            backup::show to backup::release,
            profile::show to profile::release,
            about::show to about::release,
            update::show to update::release
        )
        panels.forEachIndexed { index, (show, release) ->
            show()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            dialog.dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(index + 1, dismissals)
            show()
            release()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(ShadowDialog.getLatestDialog().isShowing)
            assertEquals("Cleanup must not invoke the user dismissal callback", index + 1, dismissals)
        }
    }

    private fun settingsHost(activity: Activity, scope: TestScope): LauncherSettingsHost {
        val brightness = BrightnessController(activity, scope, { BrightnessSettings() }, {}, { _, _ -> }, {})
        return LauncherSettingsHost(activity, { false }, brightness, {}, {}, {}, {}, {}, {})
    }
}
