package com.sergioasenjo.vesperhome.music

import android.app.Application
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityMediaServicesSetupBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, qualifiers = "land-television-xhdpi")
class MediaServicesLayoutTest {
    @Test
    fun musicSelectorCanBeScrolledIntoViewAfterDiscoveryError() {
        assertMusicSelectorReachable(pairing = false, servers = false)
    }

    @Test
    fun pairingAndDiscoveryResultsKeepSelectorsReachableAndServerListBounded() {
        assertMusicSelectorReachable(pairing = true, servers = true)
    }

    private fun assertMusicSelectorReachable(pairing: Boolean, servers: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_VesperHome)
        val binding = ActivityMediaServicesSetupBinding.inflate(LayoutInflater.from(context))
        val page = binding.jellyfinPage
        binding.plexPage.root.visibility = View.GONE
        page.root.visibility = View.VISIBLE
        page.error.visibility = View.VISIBLE
        page.error.setText(R.string.jellyfin_no_servers_found)
        if (pairing) {
            page.quickConnectCode.visibility = View.VISIBLE
            page.quickConnectCode.text = "123456"
            page.quickConnectInstructions.visibility = View.VISIBLE
        }
        if (servers) page.servers.visibility = View.VISIBLE
        val density = context.resources.displayMetrics.density
        val width = (960 * density).toInt()
        val height = (540 * density).toInt()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        binding.root.layout(0, 0, width, height)
        val scroll = page.root as ScrollView
        scroll.isSmoothScrollingEnabled = false
        assertTrue(page.useMusic.requestFocus())
        page.useMusic.requestRectangleOnScreen(Rect(0, 0, page.useMusic.width, page.useMusic.height), true)
        val buttonLocation = IntArray(2).also(page.useMusic::getLocationInWindow)
        val pageLocation = IntArray(2).also(scroll::getLocationInWindow)
        assertTrue(
            "Music selector extends below the visible page",
            buttonLocation[1] + page.useMusic.height <= pageLocation[1] + scroll.height
        )
        assertTrue("Music selector extends above the visible page", buttonLocation[1] >= pageLocation[1])
        if (servers) assertEquals((144 * density).toInt(), page.servers.height)
    }
}
