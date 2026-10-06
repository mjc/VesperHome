package com.sergioasenjo.vesperhome.artwork

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import coil3.BitmapImage
import coil3.Extras
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.imageLoader
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import coil3.target.ViewTarget
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

private data class ArtworkSource(val identity: String, val packageName: String?)
private val artworkSourceKey = Extras.Key<ArtworkSource?>(default = null)

fun ImageRequest.Builder.cacheSizedArtwork(identity: String, packageName: String? = null) = apply {
    extras[artworkSourceKey] = ArtworkSource(identity, packageName)
}

/** A bounded disk cache of static artwork prepared only after foreground input has settled. */
class SizedArtworkCache(
    context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    directory: File = File(context.cacheDir, "sized_artwork"),
    maxSizeBytes: Long = 32L * 1024 * 1024,
    private val now: () -> Long = SystemClock::uptimeMillis
) : Interceptor {
    private val context = context.applicationContext
    private val diskCache = DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(maxSizeBytes).build()
    private data class Activity(val foreground: Boolean, val lastInput: Long)
    private data class Work(
        val key: String,
        val source: Any,
        val artwork: ArtworkSource,
        val width: Int,
        val height: Int,
        val scale: Scale,
        val loader: ImageLoader,
        val memoryCacheKey: MemoryCache.Key?
    )
    private val activity = MutableStateFlow(Activity(false, 0))
    private val pending = linkedMapOf<String, Work>()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    private val worker = scope.launch {
        for (signal in wakeups) {
            while (true) {
                if (synchronized(pending) { pending.isEmpty() }) break
                awaitIdle()
                val work = synchronized(pending) {
                    pending.entries.firstOrNull()?.let { pending.remove(it.key) }
                } ?: break
                try {
                    prepare(work)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Cache failures must never prevent the original image from displaying.
                    Log.w("SizedArtworkCache", "Unable to prepare artwork", error)
                }
            }
        }
    }

    fun setForeground(foreground: Boolean) {
        activity.value = Activity(foreground, now())
    }

    fun onInput() {
        activity.value = activity.value.copy(lastInput = now())
    }

    private suspend fun awaitIdle() {
        while (true) {
            val state = activity.first { it.foreground }
            delay((IDLE_DELAY_MS - (now() - state.lastInput)).coerceAtLeast(0))
            if (activity.value == state) return
        }
    }

    internal fun shutdown() {
        worker.cancel()
        diskCache.shutdown()
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val artwork = request.extras[artworkSourceKey] ?: return chain.proceed()
        val view = (request.target as? ViewTarget<*>)?.view as? ImageView ?: return chain.proceed()
        val source = request.data
        if (source !is File && source !is String) return chain.proceed()
        val measuredChain = chain.withSize(measuredSize(view, chain.size))
        val width = (measuredChain.size.width as? Dimension.Pixels)?.px ?: return chain.proceed()
        val height = (measuredChain.size.height as? Dimension.Pixels)?.px ?: return chain.proceed()
        val key = withContext(ioDispatcher) {
            val revision = if (source is File) "${source.lastModified()}:${source.length()}" else "remote"
            "sized-v1:${artwork.identity}:$source:$revision:${width}x$height:${request.scale}"
        }
        val exact = request.newBuilder().memoryCacheKey(key).precision(Precision.EXACT).build()
        var snapshot: DiskCache.Snapshot? = null
        val cachedResult = try {
            withContext(ioDispatcher) { snapshot = runCatching { diskCache.openSnapshot(key) }.getOrNull() }
            snapshot?.let { cached ->
                val timestamp = withContext(ioDispatcher) { cached.metadata.toFile().lastModified() }
                if (source is File || System.currentTimeMillis() - timestamp < REMOTE_MAX_AGE_MS) {
                    measuredChain.withRequest(
                        exact.newBuilder()
                            .data(cached.data.toFile())
                            .memoryCacheKey("$key:prepared:$timestamp")
                            .build()
                    ).proceed()
                } else {
                    null
                }
            }
        } finally {
            withContext(NonCancellable + ioDispatcher) { snapshot?.close() }
        }
        if (cachedResult is SuccessResult) return cachedResult
        if (snapshot != null) withContext(ioDispatcher) { runCatching { diskCache.remove(key) } }
        val result = measuredChain.withRequest(exact).proceed()
        if (result is SuccessResult) {
            synchronized(pending) {
                if (pending.size >= MAX_PENDING && key !in pending) pending.remove(pending.keys.first())
                pending[key] = Work(
                    key,
                    source,
                    artwork,
                    width,
                    height,
                    request.scale,
                    context.imageLoader,
                    result.memoryCacheKey
                )
            }
            wakeups.trySend(Unit)
        }
        return result
    }

    private suspend fun measuredSize(view: ImageView, fallback: Size): Size = withContext(Dispatchers.Main.immediate) {
        // A rebind can run before changed row/grid/name dimensions have reached the ImageView.
        if (view.isAttachedToWindow &&
            generateSequence(view as View?) { it.parent as? View }.any { it.isLayoutRequested }
        ) {
            suspendCancellableCoroutine { continuation ->
                val observer = view.viewTreeObserver
                val listener = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (observer.isAlive) observer.removeOnPreDrawListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                        return true
                    }
                }
                observer.addOnPreDrawListener(listener)
                continuation.invokeOnCancellation {
                    if (observer.isAlive) observer.removeOnPreDrawListener(listener)
                }
            }
        }
        val width = view.width - view.paddingLeft - view.paddingRight
        val height = view.height - view.paddingTop - view.paddingBottom
        if (width > 0 && height > 0) Size(width, height) else fallback
    }

    private suspend fun prepare(work: Work) = withContext(ioDispatcher) {
        diskCache.openSnapshot(work.key)?.let {
            it.close()
            work.memoryCacheKey?.let { key -> work.loader.memoryCache?.remove(key) }
            return@withContext
        }
        val platformArtwork = work.artwork.packageName?.let { packageName ->
            val manager = context.packageManager
            val info = manager.getApplicationInfo(packageName, 0)
            info.loadBanner(manager)
                ?: context.getSystemService(LauncherApps::class.java)
                    .getActivityList(packageName, Process.myUserHandle()).firstOrNull()?.getBadgedIcon(0)
                ?: info.loadIcon(manager)
        }
        val image = if (platformArtwork == null) {
            val result = work.loader.execute(
                ImageRequest.Builder(context)
                    .data(work.source)
                    .size(work.width, work.height)
                    .scale(work.scale)
                    .precision(Precision.EXACT)
                    .allowHardware(false)
                    .memoryCacheKey(work.key)
                    .memoryCachePolicy(CachePolicy.READ_ONLY)
                    .build()
            ) as? SuccessResult ?: return@withContext
            (result.image as? BitmapImage)?.bitmap ?: return@withContext
        } else {
            null
        }
        val bitmap = render(platformArtwork, image, work.width, work.height, work.scale)
        try {
            val editor = diskCache.openEditor(work.key) ?: return@withContext
            try {
                editor.data.toFile().outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                editor.metadata.toFile().writeText("${bitmap.width}x${bitmap.height}")
                editor.commit()
            } catch (error: Exception) {
                editor.abort()
                throw error
            }
            // Future bindings use the prepared variant. Drop the duplicate original from Coil's cache,
            // without recycling pixels that a visible ImageView may still hold.
            work.memoryCacheKey?.let { key -> work.loader.memoryCache?.remove(key) }
        } finally {
            // An exact-size input can be written directly and may still be displayed or cached by Coil.
            if (bitmap !== image) bitmap.recycle()
        }
    }

    private fun render(drawable: Drawable?, image: Bitmap?, width: Int, height: Int, scale: Scale): Bitmap {
        val sourceWidth = drawable?.intrinsicWidth?.takeIf { it > 0 } ?: image?.width ?: width
        val sourceHeight = drawable?.intrinsicHeight?.takeIf { it > 0 } ?: image?.height ?: height
        val factor = if (scale == Scale.FIT) {
            minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        } else {
            maxOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        }
        val outputWidth = if (scale == Scale.FIT) (sourceWidth * factor).roundToInt().coerceIn(1, width) else width
        val outputHeight = if (scale == Scale.FIT) (sourceHeight * factor).roundToInt().coerceIn(1, height) else height
        if (drawable == null && image?.width == outputWidth && image.height == outputHeight) return image
        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val drawnWidth = sourceWidth * factor
            val drawnHeight = sourceHeight * factor
            val left = (outputWidth - drawnWidth) / 2
            val top = (outputHeight - drawnHeight) / 2
            if (drawable != null) {
                canvas.translate(left, top)
                canvas.scale(factor, factor)
                drawable.setBounds(0, 0, sourceWidth, sourceHeight)
                drawable.draw(canvas)
            } else {
                canvas.drawBitmap(
                    requireNotNull(image),
                    Rect(0, 0, sourceWidth, sourceHeight),
                    RectF(left, top, left + drawnWidth, top + drawnHeight),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            }
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    private companion object {
        const val IDLE_DELAY_MS = 2_000L
        const val REMOTE_MAX_AGE_MS = 24 * 60 * 60 * 1_000L
        const val MAX_PENDING = 64
    }
}
