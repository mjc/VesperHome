package com.sergioasenjo.vesperhome

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import android.os.UserHandle
import android.view.LayoutInflater
import android.widget.FrameLayout
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.categories.CategoryMembershipAdapter
import com.sergioasenjo.vesperhome.categories.CategoryMembershipItem
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.music.JellyfinMusicUiState
import com.sergioasenjo.vesperhome.music.JellyfinTrack
import com.sergioasenjo.vesperhome.music.renderJellyfinMusic
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import com.sergioasenjo.vesperhome.upcoming.UpcomingAdapter
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaType
import com.sergioasenjo.vesperhome.upcoming.UpcomingProvider
import com.sergioasenjo.vesperhome.upcoming.UpcomingProviderId
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@OptIn(DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ArtworkRequestTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).create().get().apply {
        setTheme(R.style.Theme_VesperHome)
    }
    private val parent = FrameLayout(activity)
    private val requests = mutableListOf<ImageRequest>()
    private val delegate = ImageLoader.Builder(activity).build()
    private val loader = object : ImageLoader by delegate {
        override fun enqueue(request: ImageRequest): Disposable {
            requests += request
            return delegate.enqueue(request)
        }
    }
    private val appearance = LauncherAppearance()
    private val app = LauncherApp(
        ComponentName("example.app", "Main"),
        "Example",
        ColorDrawable(Color.BLUE),
        1,
        Process.myUserHandle()
    )

    @Before
    fun recordEnqueues() {
        SingletonImageLoader.setUnsafe(loader)
    }

    @After
    fun cleanUp() {
        delegate.shutdown()
        SingletonImageLoader.reset()
    }

    @Test
    fun appStyleAndMovementUpdatesKeepRequestButGeometryChangesReload() {
        val adapter = AppAdapter({}, { _, _ -> })
        adapter.submitList(listOf(app))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        repeat(10) { index ->
            holder.bind(app.copy(label = "Renamed"), index % 2 == 0, appearance.copy(theme = LauncherTheme.LIGHT))
        }
        assertEquals(1, requests.size)
        assertEquals("Renamed", holder.itemView.contentDescription)

        holder.bind(app, false, appearance.copy(showAppNames = false))
        assertEquals(2, requests.size)
        holder.bind(app, false, appearance)
        assertEquals(3, requests.size)
        adapter.setItemSize(400, 220)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(4, requests.size)
    }

    @Test
    fun appArtworkInvalidationAndRecyclingEnqueueFreshRequests() {
        val holder = AppAdapter({}, { _, _ -> }).onCreateViewHolder(parent, 0)
        val changedApps = listOf(
            app,
            app.copy(artworkVersion = 2),
            app.copy(artworkFile = File(activity.cacheDir, "platform.png")),
            app.copy(customBannerFile = File(activity.cacheDir, "custom.png"), customBannerRevision = 1),
            app.copy(customBannerFile = File(activity.cacheDir, "custom.png"), customBannerRevision = 2),
            app.copy(
                user = ReflectionHelpers.callConstructor(
                    UserHandle::class.java,
                    ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 10)
                )
            ),
            app.copy(artwork = ColorDrawable(Color.RED))
        )
        changedApps.forEachIndexed { index, changed ->
            holder.bind(changed, false, appearance)
            assertEquals(index + 1, requests.size)
            assertEquals(changed.customBannerFile ?: changed.artworkFile ?: changed.artwork, requests.last().data)
        }
        holder.recycle()
        holder.bind(changedApps.last(), false, appearance)
        assertEquals(changedApps.size + 1, requests.size)
    }

    @Test
    fun membershipChangesKeepArtworkButVersionsAndCustomRevisionsReload() {
        val holder = CategoryMembershipAdapter {}.onCreateViewHolder(parent, 0)
        repeat(10) { index ->
            holder.bind(CategoryMembershipItem(app, index % 2 == 0), appearance.copy(keyClickSounds = false))
        }
        assertEquals(1, requests.size)
        assertFalse(holder.itemView.isSoundEffectsEnabled)
        holder.bind(CategoryMembershipItem(app.copy(artworkVersion = 2), true), appearance)
        assertEquals(2, requests.size)
        val custom = app.copy(customBannerFile = File(activity.cacheDir, "custom.png"), customBannerRevision = 1)
        holder.bind(CategoryMembershipItem(custom, true), appearance)
        holder.bind(CategoryMembershipItem(custom.copy(customBannerRevision = 2), true), appearance)
        assertEquals(4, requests.size)
        holder.recycle()
        holder.bind(CategoryMembershipItem(custom.copy(customBannerRevision = 2), false), appearance)
        assertEquals(5, requests.size)
    }

    @Test
    fun upcomingStyleAndMetadataUpdatesKeepArtworkButNewUrlAndRecyclingReload() {
        val adapter = UpcomingAdapter {}
        val holder = adapter.onCreateViewHolder(parent, 0)
        val item = UpcomingMediaItem(
            "movie",
            "Movie",
            "Detail",
            0,
            "https://example.invalid/a.png",
            UpcomingMediaType.DIGITAL,
            UpcomingProviderId(UpcomingProvider.TMDB, 1)
        )
        repeat(10) { index ->
            holder.bind(item.copy(title = "Title $index"), appearance.copy(theme = LauncherTheme.LIGHT))
        }
        assertEquals(1, requests.size)
        val changed = item.copy(imageUrl = "https://example.invalid/b.png")
        holder.bind(changed, appearance)
        assertEquals(2, requests.size)
        holder.recycle()
        holder.bind(changed, appearance)
        assertEquals(changed.imageUrl, requests.last().data)
        assertEquals(3, requests.count { it.data is String })
    }

    @Test
    fun musicPlaybackUpdatesKeepArtworkAndAlbumAndPlaceholderTransitionsReload() {
        val binding = ViewLauncherContentBinding.inflate(LayoutInflater.from(activity))
        repeat(10) { binding.renderJellyfinMusic(activity, JellyfinMusicUiState(loading = it % 2 == 0)) }
        assertEquals(1, requests.size)
        assertEquals(R.drawable.ic_music, requests.last().data)
        val track = JellyfinTrack("1", "Song", "Artist", "Album", "stream", "https://example.invalid/a.png")
        val state = JellyfinMusicUiState(serverName = "Server", track = track)
        binding.renderJellyfinMusic(activity, state)
        repeat(10) { binding.renderJellyfinMusic(activity, state.copy(playing = it % 2 == 0, loading = true)) }
        assertEquals(2, requests.size)
        assertEquals("Song", binding.musicTitle.text.toString())
        binding.renderJellyfinMusic(
            activity,
            state.copy(track = track.copy(artworkUrl = "https://example.invalid/b.png"))
        )
        assertEquals(3, requests.size)
        binding.renderJellyfinMusic(activity, state.copy(track = null))
        assertEquals(4, requests.size)
        assertEquals(R.drawable.ic_music, requests.last().data)
    }
}
