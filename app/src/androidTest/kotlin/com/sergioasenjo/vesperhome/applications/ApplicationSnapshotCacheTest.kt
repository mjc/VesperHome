package com.sergioasenjo.vesperhome.applications

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationSnapshotCacheTest {
    private lateinit var directory: File
    private lateinit var context: ContextWrapper

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName.endsWith(".instrumented"))
        directory = File(target.cacheDir, "artwork-test").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun thumbnailIsBoundedAndDoesNotRecycleOriginal() {
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val cache = ApplicationSnapshotCache(context)
        val file = requireNotNull(cache.saveArtwork("large", BitmapDrawable(context.resources, bitmap)))
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path))
        assertEquals(384, decoded.width)
        assertEquals(192, decoded.height)
        assertFalse(bitmap.isRecycled)
        decoded.recycle()
        bitmap.recycle()

        val small = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        assertNotNull(cache.saveArtwork("small", BitmapDrawable(context.resources, small)))
        assertFalse(small.isRecycled)
        small.recycle()
    }

    @Test
    fun persistedArtworkRequiresMatchingVersionAndSurvivesRestart() = runBlocking {
        val cache = ApplicationSnapshotCache(context)
        val app = app(42)
        val file = requireNotNull(cache.saveArtwork(app.packageName, app.artwork))
        cache.save(listOf(app.copy(artworkFile = file)))
        assertEquals(file, cache.cachedArtwork(app.packageName, 42))
        assertNull(cache.cachedArtwork(app.packageName, 43))

        val restarted = ApplicationSnapshotCache(context)
        assertEquals(file, restarted.load().single().artworkFile)
        assertEquals(file, restarted.cachedArtwork(app.packageName, 42))
        restarted.invalidateArtwork(app.packageName)
        assertNull(restarted.cachedArtwork(app.packageName, 42))
        assertTrue(file.delete())
        assertNull(cache.cachedArtwork(app.packageName, 42))
    }

    @Test
    fun failedArtworkRefreshCannotMarkOldImageAsCurrent() = runBlocking {
        val cache = ApplicationSnapshotCache(context)
        val original = app(42)
        val file = requireNotNull(cache.saveArtwork(original.packageName, original.artwork))
        cache.save(listOf(original.copy(artworkFile = file)))
        cache.invalidateArtwork(original.packageName)
        val broken = object : Drawable() {
            override fun draw(canvas: Canvas) = error("Decode failed")
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit

            @Deprecated("Deprecated in Android")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
        assertNull(cache.saveArtwork(original.packageName, broken))
        cache.save(listOf(original.copy(artwork = broken, artworkVersion = 43)))
        assertTrue(file.isFile)
        val restarted = ApplicationSnapshotCache(context)
        assertNull(restarted.load().single().artworkFile)
        assertNull(restarted.cachedArtwork(original.packageName, 43))
    }

    @Test
    fun localeChangeRefreshesLabelsWithoutInvalidatingArtwork() = runBlocking {
        val cache = ApplicationSnapshotCache(context)
        val original = app(42)
        val file = requireNotNull(cache.saveArtwork(original.packageName, original.artwork))
        cache.save(listOf(original.copy(artworkFile = file)))
        assertTrue(cache.labelsMatchLocale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("zz"))
        }
        val changed = object : ContextWrapper(context.createConfigurationContext(configuration)) {
            override fun getFilesDir(): File = directory
        }
        val restarted = ApplicationSnapshotCache(changed)
        restarted.load()
        assertFalse(restarted.labelsMatchLocale)
        assertEquals(file, restarted.cachedArtwork(original.packageName, 42))
    }

    private fun app(version: Long) = LauncherApp(
        componentName = ComponentName("example.app", "example.app.Main"),
        label = "Example",
        artwork = context.packageManager.defaultActivityIcon,
        artworkVersion = version,
        user = Process.myUserHandle()
    )
}
