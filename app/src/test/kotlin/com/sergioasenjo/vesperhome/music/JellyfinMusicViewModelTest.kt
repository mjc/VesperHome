package com.sergioasenjo.vesperhome.music

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.sergioasenjo.vesperhome.screensaver.DreamStateTracker
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaItem
import com.sergioasenjo.vesperhome.upcoming.UpcomingMediaType
import com.sergioasenjo.vesperhome.upcoming.UpcomingProvider
import com.sergioasenjo.vesperhome.upcoming.UpcomingProviderId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class JellyfinMusicViewModelTest {
    @Test
    fun unconfiguredLifecycleAndProviderLookupDoNotStartPlayback() = runBlocking {
        val application = RuntimeEnvironment.getApplication()
        val viewModel = JellyfinMusicViewModel(
            application,
            { error("An unconfigured account must not request the API") },
            JellyfinPreferencesRepository(application),
            DreamStateTracker(application)
        )
        val store = ViewModelStore().apply { put("music", viewModel) }
        viewModel.onHostStarted()
        viewModel.playPause()
        viewModel.playPrevious()
        viewModel.playNext()
        viewModel.loadCollections()
        val item = UpcomingMediaItem(
            "item",
            "Title",
            "Detail",
            0,
            null,
            UpcomingMediaType.EPISODE,
            UpcomingProviderId(UpcomingProvider.TVDB, 42)
        )
        assertNull(viewModel.jellyfinItemId(item))
        viewModel.onHostStopped()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        viewModel.onHostStarted()
        store.clear()
        assertTrue(shadowOf(application).boundServiceConnections.isEmpty())
        assertTrue(shadowOf(application).unboundServiceConnections.isEmpty())
    }
}
