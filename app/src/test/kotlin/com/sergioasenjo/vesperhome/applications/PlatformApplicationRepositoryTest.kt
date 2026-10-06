package com.sergioasenjo.vesperhome.applications

import android.app.Application
import android.content.ContextWrapper
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.UserHandle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLauncherApps

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class, shadows = [BlockingLauncherApps::class])
class PlatformApplicationRepositoryTest {
    private lateinit var directory: File
    private lateinit var context: ContextWrapper

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("application-refresh-test").toFile()
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = directory
        }
        BlockingLauncherApps.onQuery = {}
        BlockingLauncherApps.registered = CountDownLatch(0)
    }

    @After
    fun tearDown() {
        BlockingLauncherApps.onQuery = {}
        directory.deleteRecursively()
    }

    @Test
    fun concurrentCollectorsCannotRefreshTheSharedSnapshotAtTheSameTime() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val queries = AtomicInteger()
        BlockingLauncherApps.registered = CountDownLatch(2)
        BlockingLauncherApps.onQuery = {
            if (queries.incrementAndGet() == 1) {
                firstStarted.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            } else {
                secondStarted.countDown()
            }
        }
        val repository = PlatformApplicationRepository(context)
        val first = async(Dispatchers.IO) { repository.observeApplications().first() }
        var second: kotlinx.coroutines.Deferred<List<LauncherApp>>? = null
        try {
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            second = async(Dispatchers.IO) { repository.observeApplications().first() }
            assertTrue(BlockingLauncherApps.registered.await(5, TimeUnit.SECONDS))
            assertFalse(
                "A second collector started loading before the first transaction completed",
                secondStarted.await(1, TimeUnit.SECONDS)
            )
            releaseFirst.countDown()
            withTimeout(5000) {
                assertTrue(first.await().isEmpty())
                assertTrue(second.await().isEmpty())
            }
            assertEquals(2, queries.get())
            assertTrue(File(directory, "application_snapshot/applications.json").isFile)
        } finally {
            releaseFirst.countDown()
            first.cancelAndJoin()
            second?.cancelAndJoin()
        }
        Unit
    }

    @Test
    fun freshSnapshotsArePersistedBeforeTheyArePublished() = runBlocking {
        val repository = PlatformApplicationRepository(context)
        assertTrue(repository.observeApplications().first().isEmpty())
        val metadata = File(directory, "application_snapshot/applications.json")
        assertTrue(metadata.isFile)
        assertTrue(metadata.readText().contains("\"apps\":[]"))
    }
}

@Implements(LauncherApps::class)
class BlockingLauncherApps : ShadowLauncherApps() {
    @Implementation
    public override fun getActivityList(packageName: String?, user: UserHandle): MutableList<LauncherActivityInfo> {
        onQuery()
        return super.getActivityList(packageName, user)
    }

    @Implementation
    @Synchronized
    public override fun registerCallback(callback: LauncherApps.Callback, handler: Handler?) {
        super.registerCallback(callback, handler)
        registered.countDown()
    }

    @Implementation
    @Synchronized
    public override fun unregisterCallback(callback: LauncherApps.Callback) {
        super.unregisterCallback(callback)
    }

    companion object {
        var onQuery: () -> Unit = {}
        var registered = CountDownLatch(0)
    }
}
