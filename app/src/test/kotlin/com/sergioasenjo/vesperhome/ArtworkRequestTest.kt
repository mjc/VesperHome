package com.sergioasenjo.vesperhome

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Process
import android.widget.FrameLayout
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ArtworkRequestTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).create().get().apply {
        setTheme(R.style.Theme_VesperHome)
    }
    private val parent = FrameLayout(activity)
    private val appearance = LauncherAppearance()
    private val app = LauncherApp(
        ComponentName("example.app", "Main"),
        "Example",
        ColorDrawable(Color.BLUE),
        1,
        Process.myUserHandle()
    )

    @Test
    fun focusPulseChangesOnlyOutlineOpacityAndFocusLossStopsIt() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        holder.bind(app, false, appearance)
        val frame = holder.itemView.findViewById<FrameLayout>(R.id.artworkFrame)
        val layers = frame.background as LayerDrawable
        val background = layers.getDrawable(0) as GradientDrawable
        val outline = layers.getDrawable(1) as GradientDrawable
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        val animator = ReflectionHelpers.getField<ValueAnimator>(holder, "outlineAnimator")
        animator.currentPlayTime = 0
        val initialAlpha = outline.alpha
        animator.currentPlayTime = 425
        assertTrue(outline.alpha > initialAlpha)
        assertEquals(255, background.alpha)
        assertEquals(appearance.palette.focusedSurface, background.color!!.defaultColor)
        assertEquals(Color.TRANSPARENT, outline.color!!.defaultColor)

        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, false)
        assertFalse(animator.isStarted)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(Color.TRANSPARENT, background.color!!.defaultColor)
        assertEquals(255, outline.alpha)
        holder.recycle()
    }

    @Test
    fun movementAndDisabledFocusAnimationsKeepAnOpaqueOutlineWithoutAPulse() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        val frame = holder.itemView.findViewById<FrameLayout>(R.id.artworkFrame)
        val layers = frame.background as LayerDrawable
        holder.bind(app, true, appearance)
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(255, layers.getDrawable(1).alpha)
        assertEquals(
            appearance.palette.focusedSurface,
            (layers.getDrawable(0) as GradientDrawable).color!!.defaultColor
        )

        holder.bind(app, false, appearance.copy(appCardFocusAnimations = false))
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(255, layers.getDrawable(1).alpha)
        holder.recycle()
    }

}
