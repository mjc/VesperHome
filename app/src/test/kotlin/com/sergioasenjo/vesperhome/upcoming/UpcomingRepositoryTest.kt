package com.sergioasenjo.vesperhome.upcoming

import java.time.Instant
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingRepositoryTest {
    private val config = MutableStateFlow(UpcomingServerConfig("https://sonarr.test", "key", "", ""))
    private val requests = AtomicInteger()
    private var code = 200
    private var radarrCode = 200
    private var body = "[]"
    private var beforeResponse: () -> Unit = {}
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        beforeResponse()
        val isRadarr = chain.request().url.host == "radarr.test"
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(if (isRadarr) radarrCode else code).message("Test")
            .body((if (isRadarr) "[]" else body).toResponseBody()).build()
    }.build()
    private val repository = UpcomingRepository(client, Json { ignoreUnknownKeys = true }, config)
    private val now = 1_791_072_000_000L

    @Test
    fun freshResultsAreReusedAndConcurrentLoadsFetchOnce() = runBlocking {
        (1..8).map { async { repository.upcoming(now) } }.awaitAll()
        repository.upcoming(now + 1_000)
        assertEquals(1, requests.get())
    }

    @Test
    fun expiredAndChangedConfigurationAreRefetched() = runBlocking {
        repository.upcoming(now)
        repository.upcoming(now + 5 * 60_000)
        assertEquals(2, requests.get())
        config.value = config.value.copy(sonarrApiKey = "new-key")
        repository.upcoming(now + 5 * 60_000 + 1)
        assertEquals(3, requests.get())
    }

    @Test
    fun failedProviderIsNotCachedAsAnEmptySuccess() = runBlocking {
        code = 503
        assertTrue(repository.upcoming(now).isEmpty())
        code = 200
        repository.upcoming(now + 1_000)
        repository.upcoming(now + 2_000)
        assertEquals(2, requests.get())
    }

    @Test
    fun dayRolloverAndClockRollbackInvalidateFreshResults() = runBlocking {
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        repository.upcoming(midnight - 1)
        repository.upcoming(midnight + 1)
        repository.upcoming(midnight)
        assertEquals(3, requests.get())
    }

    @Test
    fun explicitRefreshBypassesFreshCache() = runBlocking {
        repository.upcoming(now)
        repository.upcoming(now + 1, forceRefresh = true)
        repository.upcoming(now + 2)
        assertEquals(2, requests.get())
    }

    @Test
    fun clearingConfigurationDiscardsCachedItems() = runBlocking {
        repository.upcoming(now)
        val original = config.value
        config.value = UpcomingServerConfig("", "", "", "")
        assertTrue(repository.upcoming(now + 1).isEmpty())
        config.value = original
        repository.upcoming(now + 2)
        assertEquals(2, requests.get())
    }

    @Test
    fun successfulItemsAreRetainedButPartialProviderFailuresAreRetried() = runBlocking {
        body = """[{"id":1,"title":"Episode","airDateUtc":"${Instant.ofEpochMilli(now)}",
            "series":{"title":"Show","tvdbId":42}}]"""
        config.value = config.value.copy(radarrUrl = "https://radarr.test", radarrApiKey = "key")
        radarrCode = 503
        assertEquals("Show", repository.upcoming(now).single().title)
        repository.upcoming(now + 1)
        assertEquals(4, requests.get())
        radarrCode = 200
        val items = repository.upcoming(now + 2)
        assertEquals(items, repository.upcoming(now + 3))
        assertEquals(6, requests.get())
    }

    @Test
    fun overlappingExplicitRefreshesShareTheCompletedRequest() = runBlocking {
        repository.upcoming(now)
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        beforeResponse = {
            started.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
        }
        val loads = (1..8).map {
            async(start = CoroutineStart.UNDISPATCHED) { repository.upcoming(now + 1, forceRefresh = true) }
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        } finally {
            finish.countDown()
        }
        loads.awaitAll()
        assertEquals(2, requests.get())
    }

    @Test
    fun cancelledRefreshDoesNotPublishACacheEntry() = runBlocking {
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        beforeResponse = {
            started.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
        }
        val load = async(start = CoroutineStart.UNDISPATCHED) { repository.upcoming(now) }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            load.cancel()
        } finally {
            finish.countDown()
        }
        load.join()
        beforeResponse = {}
        repository.upcoming(now + 1)
        assertEquals(2, requests.get())
    }
}
