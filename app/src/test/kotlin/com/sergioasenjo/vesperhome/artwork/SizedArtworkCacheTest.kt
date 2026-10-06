package com.sergioasenjo.vesperhome.artwork

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.widget.ImageView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.target
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.target.ViewTarget
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SizedArtworkCacheTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val directory = File(context.cacheDir, "sized-artwork-test")
    private val source = File(context.cacheDir, "source.png")
    private val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    private val decoded = mutableListOf<ImageRequest>()
    private val delegate = ImageLoader.Builder(context).build()
    private val loader = object : ImageLoader by delegate {
        override suspend fun execute(request: ImageRequest): ImageResult {
            decoded += request
            return SuccessResult(bitmap.asImage(), request)
        }
    }
    private val caches = mutableListOf<SizedArtworkCache>()

    @Before
    fun setUp() {
        directory.deleteRecursively()
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        SingletonImageLoader.setUnsafe(loader)
    }

    @After
    fun tearDown() {
        caches.forEach { it.shutdown() }
        delegate.shutdown()
        SingletonImageLoader.reset()
        bitmap.recycle()
        directory.deleteRecursively()
        source.delete()
    }

    private fun TestScope.cache(): SizedArtworkCache = SizedArtworkCache(
        context,
        backgroundScope,
        StandardTestDispatcher(testScheduler),
        directory,
        now = { testScheduler.currentTime }
    ).also { caches += it }

    private fun request(identity: String = "version-1", data: Any = source, scale: Scale = Scale.FIT): ImageRequest =
        ImageRequest.Builder(context)
            .data(data)
            .target(ImageView(context))
            .scale(scale)
            .cacheSizedArtwork(identity)
            .build()

    private inner class Chain(
        override val request: ImageRequest,
        override val size: Size = Size(200, 160),
        private val requests: MutableList<ImageRequest> = mutableListOf(),
        private val action: (suspend (ImageRequest) -> ImageResult)? = null
    ) : Interceptor.Chain {
        override fun withRequest(request: ImageRequest): Interceptor.Chain = Chain(request, size, requests, action)
        override fun withSize(size: Size): Interceptor.Chain = Chain(request, size, requests, action)
        override suspend fun proceed(): ImageResult {
            requests += request
            action?.let { return it(request) }
            val data = request.data
            if (data is File && data != source) {
                val cached = BitmapFactory.decodeFile(data.path)
                    ?: return ErrorResult(null, request, IllegalStateException("Corrupt entry"))
                return SuccessResult(cached.asImage(), request)
            }
            return SuccessResult(bitmap.asImage(), request)
        }
    }

    private fun TestScope.idle(cache: SizedArtworkCache) {
        cache.setForeground(true)
        advanceTimeBy(2_000)
        runCurrent()
    }

    @Test
    fun fitVariantUsesArtworkBoundsPreservesPixelsAndLeavesSharedInputAlive() = runTest {
        val cache = cache()
        val first = cache.intercept(Chain(request())) as SuccessResult
        assertEquals(source, first.request.data)
        assertEquals(Precision.EXACT, first.request.precision)
        assertTrue(decoded.isEmpty())
        idle(cache)
        assertEquals(1, decoded.size)
        assertEquals(Size(200, 160), decoded.single().sizeResolver.size())
        assertFalse(bitmap.isRecycled)
        val next = cache.intercept(Chain(request())) as SuccessResult
        assertNotEquals(source, next.request.data)
        assertEquals(200, next.image.width)
        assertEquals(100, next.image.height)
        assertEquals(Color.BLUE, (next.image as coil3.BitmapImage).bitmap.getPixel(100, 50))
        assertNotEquals(first.request.memoryCacheKey, next.request.memoryCacheKey)
    }

    @Test
    fun cropVariantMatchesViewDimensionsWithoutStretching() = runTest {
        bitmap.eraseColor(Color.RED)
        for (x in 300 until 500) for (y in 0 until 400) bitmap.setPixel(x, y, Color.GREEN)
        val cache = cache()
        cache.intercept(Chain(request(scale = Scale.FILL)))
        idle(cache)
        val next = cache.intercept(Chain(request(scale = Scale.FILL))) as SuccessResult
        val image = (next.image as coil3.BitmapImage).bitmap
        assertEquals(200, image.width)
        assertEquals(160, image.height)
        assertEquals(Color.GREEN, image.getPixel(100, 80))
        assertEquals(Color.RED, image.getPixel(5, 80))
    }

    @Test
    fun inputRestartsQuietPeriodAndBackgroundingDefersPendingWork() = runTest {
        val cache = cache()
        cache.intercept(Chain(request()))
        cache.setForeground(true)
        advanceTimeBy(1_500)
        cache.onInput()
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(decoded.isEmpty())
        cache.setForeground(false)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(decoded.isEmpty())
        idle(cache)
        assertEquals(1, decoded.size)
    }

    @Test
    fun restartReusesDiskVariantAndRevisionGeometryAndSourceChangesGetDifferentVariants() = runTest {
        val cache = cache()
        cache.intercept(Chain(request()))
        idle(cache)
        cache.shutdown()
        caches.remove(cache)
        val restarted = cache()
        val persisted = restarted.intercept(Chain(request())) as SuccessResult
        assertNotEquals(source, persisted.request.data)
        assertEquals(1, decoded.size)
        val revision = restarted.intercept(Chain(request("version-2"))) as SuccessResult
        assertEquals(source, revision.request.data)
        val resized = restarted.intercept(Chain(request(), Size(300, 200))) as SuccessResult
        assertEquals(source, resized.request.data)
        assertTrue(source.setLastModified(source.lastModified() + 5_000))
        val changed = restarted.intercept(Chain(request())) as SuccessResult
        assertEquals(source, changed.request.data)
        assertNotEquals(revision.request.memoryCacheKey, resized.request.memoryCacheKey)
        assertNotEquals(revision.request.memoryCacheKey, changed.request.memoryCacheKey)
    }

    @Test
    fun duplicateBindingsPrepareOnceAndCorruptVariantFallsBackAndIsRepaired() = runTest {
        val cache = cache()
        repeat(10) { cache.intercept(Chain(request())) }
        idle(cache)
        assertEquals(1, decoded.size)
        val prepared = cache.intercept(Chain(request())) as SuccessResult
        (prepared.request.data as File).writeText("bad png")
        val fallback = cache.intercept(Chain(request())) as SuccessResult
        assertEquals(source, fallback.request.data)
        runCurrent()
        val repaired = cache.intercept(Chain(request())) as SuccessResult
        assertNotEquals(source, repaired.request.data)
        assertEquals(200, repaired.image.width)
        assertEquals(2, decoded.size)
    }

    @Test
    fun remoteArtworkExpiresAndUnmarkedOrNonViewRequestsKeepNormalLoaderBehavior() = runTest {
        val cache = cache()
        val remote = request(data = "https://example.invalid/art.jpg")
        cache.intercept(Chain(remote))
        idle(cache)
        val prepared = cache.intercept(Chain(remote)) as SuccessResult
        val data = prepared.request.data as File
        val metadata = File(data.parentFile, data.name.replaceAfterLast('.', "0"))
        assertTrue(metadata.setLastModified(System.currentTimeMillis() - 25L * 60 * 60 * 1_000))
        val expired = cache.intercept(Chain(remote)) as SuccessResult
        assertEquals(remote.data, expired.request.data)
        val unmarked = ImageRequest.Builder(context).data(source).target(ImageView(context)).build()
        val normal = cache.intercept(Chain(unmarked))
        assertEquals(unmarked, normal.request)
        val targetless = request().newBuilder().target(null).build()
        assertEquals(targetless, cache.intercept(Chain(targetless)).request)
    }

    @Test
    fun platformVariantUsesOriginalBannerRatherThanEnlargingTheThumbnail() = runTest {
        val cache = cache()
        val original = context.applicationInfo.loadBanner(context.packageManager) as BitmapDrawable
        val platform = request().newBuilder().cacheSizedArtwork("installed", context.packageName).build()
        cache.intercept(Chain(platform, Size(600, 400)))
        idle(cache)
        assertTrue(decoded.isEmpty())
        val prepared = cache.intercept(Chain(platform, Size(600, 400))) as SuccessResult
        assertEquals(600, prepared.image.width)
        assertEquals(338, prepared.image.height)
        assertFalse(original.bitmap.isRecycled)
    }

    @Test
    fun pendingLayoutUsesFinalArtworkBoundsAndExcludesPadding() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible()
        try {
            val view = ImageView(controller.get())
            controller.get().setContentView(view)
            view.layout(0, 0, 100, 80)
            view.setPadding(5, 10, 15, 20)
            view.requestLayout()
            val cache = cache()
            val request = request().newBuilder().target(view).build()
            val loading = async { cache.intercept(Chain(request, Size(100, 80))) }
            runCurrent()
            assertFalse(loading.isCompleted)
            view.layout(0, 0, 240, 140)
            view.viewTreeObserver.dispatchOnPreDraw()
            runCurrent()
            val result = loading.await() as SuccessResult
            assertTrue(result.request.memoryCacheKey!!.contains("220x110"))
            idle(cache)
            assertEquals(Size(220, 110), decoded.single().sizeResolver.size())
        } finally {
            controller.pause().stop().destroy()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun realCoilLoadUsesExactSizeAndThenReadsPreparedDiskPixels() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible()
        val view = ImageView(controller.get())
        controller.get().setContentView(view, android.view.ViewGroup.LayoutParams(200, 160))
        val root = controller.get().window.decorView
        root.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(700, android.view.View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1000, 700)
        assertTrue(view.isAttachedToWindow)
        val cache = cache()
        val realLoader = ImageLoader.Builder(context).components { add(cache) }.build()
        SingletonImageLoader.setUnsafe(realLoader)
        try {
            val target = object : ViewTarget<ImageView> {
                override val view = view
            }
            val request = request().newBuilder().target(target).size(200, 160).allowHardware(false).build()
            val first = realLoader.execute(request) as SuccessResult
            assertEquals(200, first.image.width)
            assertEquals(100, first.image.height)
            assertNotNull(first.memoryCacheKey)
            assertNotNull(realLoader.memoryCache!![first.memoryCacheKey!!])
            idle(cache)
            assertNull(realLoader.memoryCache!![first.memoryCacheKey!!])
            assertFalse((first.image as coil3.BitmapImage).bitmap.isRecycled)
            val prepared = realLoader.execute(request) as SuccessResult
            assertNotEquals(source, prepared.request.data)
            assertEquals(200, prepared.image.width)
            assertEquals(100, prepared.image.height)
            assertEquals(setOf(prepared.memoryCacheKey), realLoader.memoryCache!!.keys)
        } finally {
            controller.pause().stop().destroy()
            realLoader.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun resizingExecutesOnTheWorkerDispatcher() = runTest {
        val latch = CountDownLatch(1)
        var workerThread: Thread? = null
        var workerLooper: Looper? = Looper.getMainLooper()
        SingletonImageLoader.setUnsafe(object : ImageLoader by delegate {
            override suspend fun execute(request: ImageRequest): ImageResult {
                workerThread = Thread.currentThread()
                workerLooper = Looper.myLooper()
                latch.countDown()
                return SuccessResult(bitmap.asImage(), request)
            }
        })
        Executors.newSingleThreadExecutor { Thread(it, "artwork-resize-test") }.asCoroutineDispatcher().use { io ->
            val cache = SizedArtworkCache(
                context,
                backgroundScope,
                io,
                directory,
                now = { testScheduler.currentTime }
            ).also { caches += it }
            cache.intercept(Chain(request()))
            idle(cache)
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            withContext(io) { Unit } // Wait for PNG writing on that same dispatcher.
            runCurrent()
            assertNotNull(workerThread)
            assertEquals("artwork-resize-test", workerThread!!.name)
            assertNotEquals(Looper.getMainLooper(), workerLooper)
        }
    }

    @Test
    fun cancellationReleasesDiskSnapshotSoCorruptionCanBeRepaired() = runTest {
        val cache = cache()
        cache.intercept(Chain(request()))
        idle(cache)
        val prepared = cache.intercept(Chain(request())) as SuccessResult
        val reading = async { cache.intercept(Chain(request(), action = { awaitCancellation() })) }
        runCurrent()
        assertFalse(reading.isCompleted)
        reading.cancelAndJoin()
        (prepared.request.data as File).writeText("corrupt png")
        val fallback = cache.intercept(Chain(request())) as SuccessResult
        assertEquals(source, fallback.request.data)
        runCurrent()
        assertNotEquals(source, (cache.intercept(Chain(request())) as SuccessResult).request.data)
    }

    @Test
    fun unavailableDiskCacheDoesNotPreventOriginalArtworkFromDisplaying() = runTest {
        val unavailable = File(context.cacheDir, "not-a-directory").apply { writeText("occupied") }
        try {
            val cache = SizedArtworkCache(
                context,
                backgroundScope,
                StandardTestDispatcher(testScheduler),
                unavailable,
                now = { testScheduler.currentTime }
            ).also { caches += it }
            val key = MemoryCache.Key("original-artwork", mapOf("geometry" to "200x160"))
            delegate.memoryCache!![key] = MemoryCache.Value(bitmap.asImage())
            val original = cache.intercept(
                Chain(request(), action = {
                    SuccessResult(bitmap.asImage(), it, memoryCacheKey = key)
                })
            )
            assertEquals(source, original.request.data)
            idle(cache)
            assertNotNull(delegate.memoryCache!![key])
            assertFalse(bitmap.isRecycled)
            assertEquals(source, cache.intercept(Chain(request())).request.data)
        } finally {
            unavailable.delete()
        }
    }

    @Test
    fun rapidNavigationKeepsPreparationBoundedToRecentArtworkRequests() = runTest {
        val cache = cache()
        repeat(96) { cache.intercept(Chain(request("variant-$it"))) }
        assertTrue(decoded.isEmpty())
        idle(cache)
        assertEquals(64, decoded.size)
        assertTrue(decoded.none { it.memoryCacheKey!!.contains("variant-0:") })
        assertTrue(decoded.any { it.memoryCacheKey!!.contains("variant-95:") })
    }
}
