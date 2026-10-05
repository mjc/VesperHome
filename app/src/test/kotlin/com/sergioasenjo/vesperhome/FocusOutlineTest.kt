package com.sergioasenjo.vesperhome

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Process
import android.view.View
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class FocusOutlineTest {
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
    fun focusPulseChangesOnlyViewOpacityAndFocusLossStopsIt() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        holder.bind(app, false, appearance)
        val frame = holder.itemView.findViewById<FrameLayout>(R.id.artworkFrame)
        val background = frame.background as GradientDrawable
        val outlineView = frame.getChildAt(0)
        val outline = outlineView.background as GradientDrawable
        assertEquals(R.id.artwork, frame.getChildAt(1).id)
        assertFalse(outlineView.hasOverlappingRendering())
        assertFalse(outlineView.isFocusable)
        assertFalse(outlineView.isClickable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, outlineView.importantForAccessibility)
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        val animator = ReflectionHelpers.getField<ValueAnimator>(holder, "outlineAnimator")
        animator.currentPlayTime = 0
        val initialAlpha = outlineView.alpha
        animator.currentPlayTime = 425
        assertEquals(109f / 255f, initialAlpha, 0.0001f)
        assertTrue(outlineView.alpha > initialAlpha)
        assertEquals(850L, animator.duration)
        assertEquals(ValueAnimator.INFINITE, shadowOf(animator).actualRepeatCount)
        assertEquals(ValueAnimator.REVERSE, animator.repeatMode)
        assertEquals(255, outline.alpha)
        assertEquals(255, background.alpha)
        assertEquals(appearance.palette.focusedSurface, background.color!!.defaultColor)
        assertEquals(Color.TRANSPARENT, outline.color!!.defaultColor)

        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, false)
        assertFalse(animator.isStarted)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(Color.TRANSPARENT, background.color!!.defaultColor)
        assertEquals(1f, outlineView.alpha, 0f)
        holder.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun focusOutlineKeepsTheSameGeometryAndOpaqueColor() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        holder.bind(app, false, appearance)
        val frame = holder.itemView.findViewById<FrameLayout>(R.id.artworkFrame)
        frame.findViewById<View>(R.id.artwork).visibility = View.GONE
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY)
        )
        frame.layout(0, 0, 400, 220)
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        val animator = ReflectionHelpers.getField<ValueAnimator>(holder, "outlineAnimator")
        val density = activity.resources.displayMetrics.density
        for (time in listOf(850L)) {
            animator.currentPlayTime = time
            val reference = LayerDrawable(
                arrayOf(
                    GradientDrawable().apply {
                        cornerRadius = (10 * density).toInt().toFloat()
                        setColor(appearance.palette.focusedSurface)
                    },
                    GradientDrawable().apply {
                        cornerRadius = (10 * density).toInt().toFloat()
                        setColor(Color.TRANSPARENT)
                        setStroke((3 * density).toInt(), appearance.palette.focus)
                        alpha = animator.animatedValue as Int
                    }
                )
            )
            val expected = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888)
            reference.setBounds(0, 0, 400, 220)
            reference.draw(Canvas(expected))
            frame.draw(Canvas(actual))
            assertTrue("Outline geometry and color differ", expected.sameAs(actual))
        }
        holder.recycle()
    }

    @Test
    fun movementAndDisabledFocusAnimationsKeepAnOpaqueOutlineWithoutAPulse() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        val frame = holder.itemView.findViewById<FrameLayout>(R.id.artworkFrame)
        val outlineView = frame.getChildAt(0)
        holder.bind(app, true, appearance)
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(1f, outlineView.alpha, 0f)
        assertEquals(
            appearance.palette.focusedSurface,
            (frame.background as GradientDrawable).color!!.defaultColor
        )

        holder.bind(app, false, appearance.copy(appCardFocusAnimations = false))
        holder.itemView.onFocusChangeListener!!.onFocusChange(holder.itemView, true)
        assertNull(ReflectionHelpers.getField<ValueAnimator?>(holder, "outlineAnimator"))
        assertEquals(1f, outlineView.alpha, 0f)
        holder.recycle()
    }
}
