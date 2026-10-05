package com.sergioasenjo.vesperhome.status

import android.app.Application
import android.util.LruCache
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.upcoming.UpcomingPreferencesRepository
import com.sergioasenjo.vesperhome.upcoming.UpcomingServerConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class StatusClockFormatterTest {
    @Test
    fun clockReusesFormattersAndTextButRefreshesLocaleTimeZoneAndCustomPatterns() {
        val originalLocale = Locale.getDefault()
        val originalTimeZone = TimeZone.getDefault()
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java)
        val activity = activityController.get().apply { setTheme(R.style.Theme_VesperHome) }
        activityController.create().start().resume()
        val binding = ActivityLauncherBinding.inflate(activity.layoutInflater)
        var settings = StatusBarSettings(dateFormat = "MMMM", timeFormat = "HH z")
        val controller = StatusBarController(
            activity, binding, NetworkStatusRepository(activity),
            UpcomingPreferencesRepository(activity, UpcomingServerConfig("", "", "", "")),
            { settings }, {}, {}, {}, {}, {}, {}, {}
        )
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            controller.render(settings, LauncherAppearance())
            val cache = ReflectionHelpers.getField<LruCache<String, SimpleDateFormat>>(controller, "dateTimeFormatters")
            val first = cache.snapshot()
            var textChanges = 0
            binding.statusDateTime.doAfterTextChanged { textChanges++ }
            repeat(20) { controller.render(settings, LauncherAppearance()) }
            assertEquals(0, textChanges)
            assertSame(first[settings.dateFormat], cache[settings.dateFormat])
            assertSame(first[settings.timeFormat], cache[settings.timeFormat])

            Locale.setDefault(Locale.FRANCE)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"))
            controller.render(settings, LauncherAppearance())
            val now = Date()
            val expected = listOf(settings.dateFormat, settings.timeFormat).joinToString("  ·  ") {
                SimpleDateFormat(it, Locale.FRANCE).format(now)
            }
            assertEquals(expected, binding.statusDateTime.text.toString())
            assertEquals("America/Denver", cache[settings.timeFormat]!!.timeZone.id)
            assertNotEquals(
                first[settings.dateFormat]!!.dateFormatSymbols.months.toList(),
                cache[settings.dateFormat]!!.dateFormatSymbols.months.toList()
            )

            settings = settings.copy(dateFormat = "'Custom date'", timeFormat = "'Custom time'")
            controller.render(settings, LauncherAppearance())
            assertEquals("Custom date  ·  Custom time", binding.statusDateTime.text.toString())
            assertEquals(setOf(settings.dateFormat, settings.timeFormat), cache.snapshot().keys)
            settings = settings.copy(timeFormat = "invalid[")
            controller.render(settings, LauncherAppearance())
            assertEquals("Custom date  ·  ", binding.statusDateTime.text.toString())
        } finally {
            Locale.setDefault(originalLocale)
            TimeZone.setDefault(originalTimeZone)
            controller.release()
            activityController.pause().stop().destroy()
        }
    }
}
