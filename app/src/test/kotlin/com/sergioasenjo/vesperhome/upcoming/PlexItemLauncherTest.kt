package com.sergioasenjo.vesperhome.upcoming

import android.app.Application
import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class PlexItemLauncherTest {
    @Test
    fun ambiguousExactTitlesDoNotOpenAnArbitraryRemakeOrFallBackToAnAlias() {
        assertNull(
            find(
                "The Thing",
                listOf(
                    row("The Thing", "plex://movie/duplicate"),
                    row("The Thing", "plex://movie/1982"),
                    row("John Carpenter's The Thing", "plex://movie/alias")
                )
            )
        )
    }

    @Test
    fun uniqueExactTitleWinsOverAliasesAndDuplicateSuggestions() {
        assertEquals(
            Uri.parse("plex://movie/exact"),
            find(
                "The Thing",
                listOf(
                    row("John Carpenter's The Thing", "plex://movie/alias"),
                    row("THE THING", "plex://movie/exact"),
                    row("The Thing", "plex://movie/exact")
                )
            )
        )
    }

    @Test
    fun acceptsOnlyUniqueSuffixAliases() {
        assertEquals(
            Uri.parse("plex://series/1"),
            find(
                "House of Games",
                listOf(
                    row("Richard Osman's House of Games", "plex://series/1", null)
                ),
                UpcomingMediaType.EPISODE
            )
        )
        assertNull(
            find(
                "House of Games",
                listOf(
                    row("Richard Osman's House of Games", "plex://series/1", null),
                    row("Another House of Games", "plex://series/2", null)
                ),
                UpcomingMediaType.EPISODE
            )
        )
    }

    @Test
    fun seriesSearchSkipsVideosAndNonPlexLinks() {
        assertEquals(
            Uri.parse("plex://series/1"),
            find(
                "Show",
                listOf(
                    row("Show", "plex://episode/1", "video/mp4"),
                    row("Show", "https://other.test/show", null),
                    row("Show", "plex://series/1", null)
                ),
                UpcomingMediaType.EPISODE
            )
        )
    }

    @Test
    fun movieSearchRejectsSeriesAndAudioWithoutMakingTheMovieAmbiguous() {
        val otherTypes = listOf(
            row("Fargo", "plex://series/fargo", null),
            row("Fargo", "plex://audio/fargo", "audio/mpeg")
        )
        for (type in listOf(UpcomingMediaType.CINEMA, UpcomingMediaType.DIGITAL, UpcomingMediaType.PHYSICAL)) {
            assertNull(find("Fargo", otherTypes, type))
            assertEquals(
                Uri.parse("plex://movie/fargo"),
                find("Fargo", otherTypes + listOf(row("Fargo", "plex://movie/fargo")), type)
            )
        }
    }

    @Test
    fun movieSearchRejectsASoleWrongRemakeAndSelectsTheMatchingProductionYear() {
        val wrong = row("The Thing", "plex://movie/2011", year = "2011")
        assertNull(find("The Thing", listOf(wrong)))
        assertNull(find("The Thing", listOf(row("Another The Thing", "plex://movie/alias", year = "2011"))))
        assertEquals(
            Uri.parse("plex://movie/1982"),
            find("The Thing", listOf(wrong, row("The Thing", "plex://movie/1982")))
        )
    }

    @Test
    fun movieSearchRequiresContentTypeAndKnownProductionYears() {
        val match = listOf(row("The Thing", "plex://movie/1982"))
        assertNull(find("The Thing", match, productionYear = null))
        assertNull(find("The Thing", match, omitYearColumn = true))
        assertNull(find("The Thing", match, omitTypeColumn = true))
        for (year in listOf(null, "", "unknown", "0")) {
            assertNull(find("The Thing", listOf(row("The Thing", "plex://movie/1982", year = year))))
        }
    }

    private fun row(title: String, uri: String, mime: String? = "video/mp4", year: String? = "1982") =
        arrayOf(title, uri, mime, year)

    private fun find(
        title: String,
        rows: List<Array<String?>>,
        type: UpcomingMediaType = UpcomingMediaType.DIGITAL,
        productionYear: Int? = 1982,
        omitYearColumn: Boolean = false,
        omitTypeColumn: Boolean = false
    ): Uri? {
        val columns = listOf(
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR
        ).filterIndexed { index, _ -> !((omitTypeColumn && index == 2) || (omitYearColumn && index == 3)) }
        val cursor = MatrixCursor(columns.toTypedArray()).apply {
            rows.forEach { row ->
                addRow(
                    row.filterIndexed { index, _ ->
                        !(
                            (omitTypeColumn && index == 2) ||
                                (omitYearColumn && index == 3)
                            )
                    }
                )
            }
        }
        val context = RuntimeEnvironment.getApplication()
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor = cursor
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?
            ) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "com.plexapp.android.SearchProvider" })
        ShadowContentResolver.registerProviderInternal("com.plexapp.android.SearchProvider", provider)
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_VesperHome)
        val activity = controller.create().get()
        try {
            return runBlocking {
                PlexItemLauncher(activity).findItem(
                    UpcomingMediaItem(
                        "item",
                        title,
                        "1982",
                        0,
                        null,
                        type,
                        UpcomingProviderId(UpcomingProvider.TMDB, 1091),
                        productionYear
                    )
                )
            }.also { assertTrue("Search cursor must be closed", cursor.isClosed) }
        } finally {
            controller.destroy()
        }
    }
}
