package com.sergioasenjo.vesperhome.upcoming

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingCancellationTest {
    @Test
    fun cancellingAnOldServerRefreshDoesNotBlockLoadingTheNewServer() = runBlocking {
        val oldStarted = CountDownLatch(1)
        val releaseOld = CountDownLatch(1)
        val newStarted = CountDownLatch(1)
        val config = MutableStateFlow(UpcomingServerConfig("https://old.test", "key", "", ""))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.host == "old.test") {
                oldStarted.countDown()
                check(releaseOld.await(5, TimeUnit.SECONDS))
            } else {
                newStarted.countDown()
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("[]".toResponseBody()).build()
        }.build()
        val repository = UpcomingRepository(client, Json { ignoreUnknownKeys = true }, config)
        val old = launch(Dispatchers.IO) { repository.upcoming() }
        var fresh: kotlinx.coroutines.Deferred<List<UpcomingMediaItem>>? = null
        try {
            assertTrue(oldStarted.await(2, TimeUnit.SECONDS))
            old.cancel()
            config.value = config.value.copy(sonarrUrl = "https://new.test")
            fresh = async(Dispatchers.IO) { repository.upcoming(forceRefresh = true) }
            assertTrue(
                "Cancelled IO retains the refresh mutex and blocks the new server",
                newStarted.await(2, TimeUnit.SECONDS)
            )
            fresh.await()
        } finally {
            releaseOld.countDown()
            old.cancelAndJoin()
            fresh?.cancelAndJoin()
        }
        Unit
    }
}
