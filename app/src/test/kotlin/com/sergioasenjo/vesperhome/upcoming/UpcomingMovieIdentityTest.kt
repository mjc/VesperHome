package com.sergioasenjo.vesperhome.upcoming

import android.app.Application
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class UpcomingMovieIdentityTest {
    @Test
    fun calendarPreservesProductionYearInsteadOfUsingTheScheduledReleaseYear() = runBlocking {
        for ((yearField, expectedYear) in listOf(
            "\"year\":1982," to 1982,
            "" to null,
            "\"year\":0," to null
        )) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(
                        """[{"id":1,"title":"The Thing","tmdbId":1091,$yearField"digitalRelease":"2026-10-07"}]"""
                            .toResponseBody()
                    ).build()
            }.build()
            val preferences = UpcomingPreferencesRepository(
                RuntimeEnvironment.getApplication(),
                UpcomingServerConfig("", "", "https://radarr.test", "key")
            )
            val item = UpcomingRepository(client, Json { ignoreUnknownKeys = true }, preferences.config)
                .upcoming(Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()).single()
            assertEquals(expectedYear, item.productionYear)
            assertEquals(UpcomingMediaType.DIGITAL, item.type)
            assertEquals(UpcomingProviderId(UpcomingProvider.TMDB, 1091), item.providerId)
        }
    }
}
