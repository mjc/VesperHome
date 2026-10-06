package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.app.Application
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LauncherAppearanceRendererTest {
    @Test
    fun unchangedAppearanceKeepsDrawablesAndFocusListenerButThemeChangesApply() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        activity.setTheme(R.style.Theme_VesperHome)
        val binding = ActivityLauncherBinding.inflate(activity.layoutInflater)
        val content = ViewLauncherContentBinding.bind(binding.launcherContentStub.inflate())
        val renderer = LauncherAppearanceRenderer(binding, content)
        val appearance = LauncherAppearance(selectorTransitionAnimations = false)
        renderer.render(appearance)
        val artworkBackground = content.musicArtwork.background
        val artworkForeground = content.musicArtwork.foreground
        val focusListener = content.musicArtwork.onFocusChangeListener
        renderer.render(appearance.copy())
        assertSame(artworkBackground, content.musicArtwork.background)
        assertSame(artworkForeground, content.musicArtwork.foreground)
        assertSame(focusListener, content.musicArtwork.onFocusChangeListener)

        val updated = appearance.copy(theme = LauncherTheme.LIGHT, keyClickSounds = false)
        renderer.render(updated)
        assertNotSame(artworkBackground, content.musicArtwork.background)
        assertEquals(updated.palette.primaryText, content.musicTitle.currentTextColor)
        assertFalse(binding.openLauncherSettings.isSoundEffectsEnabled)
        assertFalse(content.musicNext.isSoundEffectsEnabled)
    }
}
