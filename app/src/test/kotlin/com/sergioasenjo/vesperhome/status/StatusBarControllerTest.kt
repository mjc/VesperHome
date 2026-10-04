package com.sergioasenjo.vesperhome.status

import android.app.Application
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.upcoming.UpcomingPreferencesRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingServerConfig
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class StatusBarControllerTest {
    @Test
    fun clockStopsWithActivityAndRefreshesOnRestart() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = activityController.get().apply { setTheme(R.style.Theme_VesperHome) }
        activityController.create().start().resume()
        val binding = ActivityLauncherBinding.inflate(activity.layoutInflater)
        val settings = StatusBarSettings(timeFormat = "HH:mm:ss")
        val controller = StatusBarController(
            activity, binding, NetworkStatusRepository(activity),
            UpcomingPreferencesRepository(activity, UpcomingServerConfig("", "", "", "")),
            { settings }, {}, {}, {}, {}, {}, {}, {}
        )
        controller.render(settings, LauncherAppearance())
        activityController.pause().stop()
        binding.statusDateTime.text = "Stopped"
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertEquals("Stopped", binding.statusDateTime.text)
        activityController.restart().start().resume()
        assertNotEquals("Stopped", binding.statusDateTime.text)
        controller.release()
        activityController.pause().stop().destroy()
    }
}
