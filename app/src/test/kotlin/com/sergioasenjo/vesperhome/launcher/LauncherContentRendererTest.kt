package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.AppRowView
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.categories.CategoryLayoutType
import com.sergioasenjo.vesperhome.categories.LauncherCategory
import com.sergioasenjo.vesperhome.categories.LauncherSpacer
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LauncherContentRendererTest {
    private val activity = Robolectric.buildActivity(
        Activity::class.java
    ).create().start().resume().visible().get().apply {
        setTheme(R.style.Theme_VesperHome)
    }
    private val binding = ViewLauncherContentBinding.inflate(activity.layoutInflater)
    private val orders = mutableListOf<Pair<Long, List<LauncherApp>>>()
    private val renderer = LauncherContentRenderer(
        activity,
        binding,
        binding.musicNext,
        {},
        { _, _ -> },
        {},
        { id, apps -> orders += id to apps }
    )
    private val apps = catalog(20)
    private val category =
        LauncherCategory(1, "Category", 0, ApplicationSortMode.MANUAL, CategoryLayoutType.ROW, 3, 96, apps)
    private val state = LauncherUiState(apps = apps, sections = listOf(category), loading = false)
    private val concat get() = binding.contentRows.adapter as ConcatAdapter
    private fun rows(): List<AppRowView> = concat.adapters.filterIsInstance<LauncherRowAdapter>()
        .map { it.view }.filterIsInstance<AppRowView>()
        .filter { it !== binding.favoriteRow && it !== binding.applicationsRow }
    private fun grids() = concat.adapters.filterIsInstance<AppAdapter>()

    @Test
    fun largeGridOnlyCreatesCardsNearViewport() {
        activity.setContentView(binding.root)
        renderer.render(
            state.copy(
                apps = emptyList(),
                sections = listOf(
                    category.copy(
                        layoutType = CategoryLayoutType.GRID,
                        apps = catalog(300)
                    )
                )
            )
        )
        layout()
        val grid = grids().single()
        val cards = gridHolders(grid)
        assertEquals(300, grid.itemCount)
        assertTrue("Attached ${cards.size} cards for a 700px viewport", cards.size in 3..29)
        val lastVisible = cards.filter { it.itemView.top < 700 }.maxOf { it.bindingAdapterPosition }
        assertTrue(cards.maxOf { it.bindingAdapterPosition } > lastVisible)
        assertTrue(cards.maxOf { it.bindingAdapterPosition } <= lastVisible + category.gridColumns)
        assertNull(rows().single().apps.adapter)
    }

    @Test
    fun scrollingLargeGridRecyclesOldCardsAndKeepsBoundedPopulation() {
        activity.setContentView(binding.root)
        renderer.render(
            state.copy(
                apps = emptyList(),
                sections = listOf(
                    category.copy(
                        layoutType = CategoryLayoutType.GRID,
                        apps = catalog(300)
                    )
                )
            )
        )
        layout()
        val grid = grids().single()
        val oldFirst = gridHolders(grid).minBy { it.bindingAdapterPosition }.itemView
        val manager = binding.contentRows.layoutManager as LinearLayoutManager
        manager.scrollToPositionWithOffset(binding.contentRows.absolutePosition(grid, 240), 0)
        layout()
        val cards = gridHolders(grid)
        assertTrue(cards.size in 3..29)
        assertTrue(cards.minOf { it.bindingAdapterPosition } >= 237)
        assertTrue(cards.maxOf { it.bindingAdapterPosition } < 270)
        // A recycled view may be reused, but no holder still represents the old first item.
        assertNull(binding.contentRows.findViewHolderForAdapterPosition(binding.contentRows.absolutePosition(grid, 0)))
        assertTrue(
            oldFirst.parent == null || binding.contentRows.getChildViewHolder(oldFirst).bindingAdapterPosition > 200
        )
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    fun largeGridRequestsOnlyArtworkNearViewport() {
        val requests = mutableListOf<ImageRequest>()
        val delegate = ImageLoader.Builder(activity).build()
        val loader = object : ImageLoader by delegate {
            override fun enqueue(request: ImageRequest): Disposable {
                requests += request
                return delegate.enqueue(request)
            }
        }
        SingletonImageLoader.setUnsafe(loader)
        try {
            activity.setContentView(binding.root)
            renderer.render(
                state.copy(
                    apps = emptyList(),
                    sections = listOf(
                        category.copy(
                            layoutType = CategoryLayoutType.GRID,
                            apps = catalog(300)
                        )
                    )
                )
            )
            layout()
            val grid = grids().single()
            val uniqueArtwork = requests.map { it.data }.distinct()
            assertTrue("Requested ${uniqueArtwork.size} artworks", uniqueArtwork.size in 3..29)
            assertEquals(gridHolders(grid).size, uniqueArtwork.size)
        } finally {
            binding.contentRows.adapter = null
            delegate.shutdown()
            SingletonImageLoader.reset()
        }
    }

    @Test
    fun rowGridTransitionsPreserveFocusedApp() {
        activity.setContentView(binding.root)
        renderer.render(state)
        layout()
        val row = rows().single()
        assertTrue(requireNotNull(row.apps.findViewHolderForAdapterPosition(2)).itemView.requestFocus())
        renderer.render(state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID))))
        layout()
        val grid = grids().single()
        val holder = binding.contentRows.getChildViewHolder(requireNotNull(binding.contentRows.focusedChild))
        assertSame(grid, holder.bindingAdapter)
        assertEquals(2, holder.bindingAdapterPosition)
        renderer.render(state)
        layout()
        assertSame(requireNotNull(row.apps.findViewHolderForAdapterPosition(2)).itemView, row.apps.focusedChild)
    }

    @Test
    fun removedGridDoesNotLeaveItsHoldersInTheRecyclePool() {
        activity.setContentView(binding.root)
        renderer.render(
            state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID, apps = catalog(300))))
        )
        layout()
        val grid = grids().single()
        val type = gridHolders(grid).first().itemViewType
        (binding.contentRows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            binding.contentRows.absolutePosition(grid, 240),
            0
        )
        layout()
        renderer.render(state.copy(sections = emptyList()))
        layout()
        assertEquals(0, binding.contentRows.recycledViewPool.getRecycledViewCount(type))
        assertTrue(gridHolders(grid).isEmpty())
        assertFalse(renderer.isReorderable(grid))
    }

    @Test
    fun offscreenHorizontalRowsReleaseCardsAndRestoreScrollPosition() {
        activity.setContentView(binding.root)
        val sections = (1L..20L).map { category.copy(id = it, name = "Row $it") }
        renderer.render(state.copy(apps = emptyList(), sections = sections))
        layout()
        val first = rows().first()
        val manager = first.apps.layoutManager as LinearLayoutManager
        manager.scrollToPositionWithOffset(8, 0)
        layout()
        val position = manager.findFirstVisibleItemPosition()
        val count = first.apps.childCount
        val visibleCount = (0 until count).count { first.apps.getChildAt(it).left < first.apps.width }
        assertTrue(count <= visibleCount + 1)
        assertTrue(count < apps.size)
        assertTrue(rows().count { it.apps.childCount > 0 } < 8)
        val last = concat.adapters.filterIsInstance<LauncherRowAdapter>().first { it.view === rows().last() }
        (binding.contentRows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            binding.contentRows.absolutePosition(last, 0),
            0
        )
        layout()
        assertNull(first.apps.adapter)
        assertEquals(0, first.apps.childCount)
        val firstAdapter = concat.adapters.filterIsInstance<LauncherRowAdapter>().first { it.view === first }
        (binding.contentRows.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            binding.contentRows.absolutePosition(firstAdapter, 0),
            0
        )
        layout()
        assertNotNull(first.apps.adapter)
        assertEquals(position, manager.findFirstVisibleItemPosition())
    }

    @Test
    fun unrelatedUpdatesPreserveFocusedCardAndScrollPosition() {
        activity.setContentView(binding.root)
        renderer.render(state)
        val row = binding.applicationsRow.apps
        val manager = row.layoutManager as LinearLayoutManager
        manager.scrollToPositionWithOffset(8, 0)
        layout()
        val card = requireNotNull(row.findViewHolderForAdapterPosition(8)).itemView
        assertTrue(card.requestFocus())
        val position = manager.findFirstVisibleItemPosition()
        renderer.render(state.copy(statusBar = state.statusBar.copy(showNetwork = false)))
        layout()
        assertSame(manager, row.layoutManager)
        assertSame(card, row.focusedChild)
        assertEquals(position, manager.findFirstVisibleItemPosition())
    }

    @Test
    fun rowGridTransitionsAndColumnUpdatesKeepContentManagerAndAdapter() {
        renderer.render(state)
        val row = rows().single()
        val horizontal = row.apps.layoutManager
        val adapter = row.apps.adapter as AppAdapter
        val contentManager = binding.contentRows.layoutManager as GridLayoutManager
        renderer.render(state.copy(applicationSortMode = ApplicationSortMode.ALPHABETICAL))
        assertSame(horizontal, row.apps.layoutManager)
        renderer.render(state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID))))
        assertSame(adapter, grids().single())
        assertNull(row.apps.adapter)
        assertEquals(3, contentManager.spanCount)
        renderer.render(
            state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID, gridColumns = 5)))
        )
        assertSame(contentManager, binding.contentRows.layoutManager)
        assertEquals(5, contentManager.spanCount)
        renderer.render(state)
        assertTrue(grids().isEmpty())
        assertSame(adapter, row.apps.adapter)
        assertSame(horizontal, row.apps.layoutManager)
    }

    @Test
    fun mixedColumnGridsAndSpacersPreserveSectionOrderAndSpans() {
        renderer.render(
            state.copy(
                sections = listOf(
                    category.copy(layoutType = CategoryLayoutType.GRID, gridColumns = 5),
                    LauncherSpacer(4, 1, 24),
                    category.copy(id = 2, name = "Second", layoutType = CategoryLayoutType.GRID, gridColumns = 7)
                )
            )
        )
        val manager = binding.contentRows.layoutManager as GridLayoutManager
        assertEquals(35, manager.spanCount)
        val first = grids()[0]
        val second = grids()[1]
        assertEquals(7, manager.spanSizeLookup.getSpanSize(binding.contentRows.absolutePosition(first, 0)))
        assertEquals(5, manager.spanSizeLookup.getSpanSize(binding.contentRows.absolutePosition(second, 0)))
        val spacer = concat.adapters.filterIsInstance<LauncherRowAdapter>().single { it.view is android.widget.Space }
        val betweenGrids = (concat.adapters.indexOf(first) + 1) until concat.adapters.indexOf(second)
        assertTrue(concat.adapters.indexOf(spacer) in betweenGrids)
        assertEquals(35, manager.spanSizeLookup.getSpanSize(binding.contentRows.absolutePosition(spacer, 0)))
        renderer.render(state.copy(sections = listOf(category.copy(id = 2, name = "Second"), category)))
        assertEquals(listOf("Second", "Category"), rows().map { it.title.text.toString() })
    }

    @Test
    fun newAndExistingRowsReceiveUpdatedAppearance() {
        val appearance = LauncherAppearance(
            theme = LauncherTheme.LIGHT,
            showCategoryTitles = false,
            keyClickSounds = false
        )
        renderer.render(state.copy(appearance = appearance))
        val original = rows().single()
        renderer.render(
            state.copy(appearance = appearance, sections = listOf(category, category.copy(id = 2, name = "New")))
        )
        assertSame(original, rows().first())
        for (row in rows()) {
            assertEquals(View.GONE, row.categoryHeader.visibility)
            assertEquals(appearance.palette.primaryText, row.title.currentTextColor)
            assertFalse(row.apps.isSoundEffectsEnabled)
        }
        renderer.render(state.copy(appearance = appearance.copy(showCategoryTitles = true)))
        assertEquals(1, rows().size)
        assertEquals(View.VISIBLE, original.categoryHeader.visibility)
    }

    @Test
    fun emptyGridsAndSortChangesPreserveManagerAndRemoveCards() {
        val gridCategory = category.copy(layoutType = CategoryLayoutType.GRID)
        renderer.render(state.copy(sections = listOf(gridCategory)))
        val manager = binding.contentRows.layoutManager
        val adapter = grids().single()
        assertTrue(renderer.isReorderable(adapter))
        val emptyCategory = gridCategory.copy(apps = emptyList(), sortMode = ApplicationSortMode.ALPHABETICAL)
        renderer.render(state.copy(sections = listOf(emptyCategory)))
        await { adapter.itemCount == 0 }
        assertSame(manager, binding.contentRows.layoutManager)
        assertEquals(0, adapter.itemCount)
        assertFalse(renderer.isReorderable(adapter))
    }

    @Test
    fun removedCategoriesDetachAdaptersForBothLayoutsAndSortModes() {
        for (layoutType in CategoryLayoutType.entries) {
            for (sortMode in ApplicationSortMode.entries) {
                val configuredCategory = category.copy(sortMode = sortMode, layoutType = layoutType)
                renderer.render(state.copy(sections = listOf(configuredCategory)))
                val row = rows().single()
                val adapter = if (layoutType == CategoryLayoutType.GRID) {
                    grids().single()
                } else {
                    row.apps.adapter as AppAdapter
                }
                renderer.render(state.copy(sections = emptyList()))
                assertTrue(rows().isEmpty())
                assertFalse(renderer.isReorderable(adapter))
                assertNull(row.apps.adapter)
                assertEquals(0, row.apps.childCount)
                assertTrue(renderer.isReorderable(binding.applicationsRow.apps.adapter as AppAdapter))
            }
        }
    }

    @Test
    fun repeatedCategoryRemovalReleasesOldRowsAndPreservesRemainingCategory() {
        activity.setContentView(binding.root)
        val remaining = category.copy(id = 2, name = "Remaining")
        renderer.render(state.copy(sections = listOf(category, remaining)))
        layout()
        val remainingRow = rows()[1]
        val remainingAdapter = remainingRow.apps.adapter as AppAdapter
        var previousAdapter: AppAdapter? = null
        repeat(5) {
            renderer.render(state.copy(sections = listOf(category, remaining)))
            layout()
            val removedRow = rows()[0]
            val removedAdapter = removedRow.apps.adapter as AppAdapter
            assertTrue(removedRow.apps.childCount > 0)
            assertNotSame(previousAdapter, removedAdapter)
            renderer.render(state.copy(sections = listOf(remaining)))
            layout()
            assertEquals(1, rows().size)
            assertSame(remainingRow, rows().single())
            assertSame(remainingAdapter, remainingRow.apps.adapter)
            assertTrue(renderer.isReorderable(remainingAdapter))
            assertFalse(renderer.isReorderable(removedAdapter))
            assertNull(removedRow.apps.adapter)
            assertEquals(0, removedRow.apps.childCount)
            previousAdapter = removedAdapter
        }
    }

    @Test
    fun gridDpadAndManualReorderUseCategoryLocalPositions() {
        activity.setContentView(binding.root)
        val gridCategory = category.copy(id = 9, layoutType = CategoryLayoutType.GRID)
        renderer.render(state.copy(sections = listOf(category, gridCategory)))
        layout()
        val grid = grids().single()
        fun card(position: Int) = requireNotNull(
            binding.contentRows.findViewHolderForAdapterPosition(
                binding.contentRows.absolutePosition(grid, position)
            )
        ).itemView
        assertTrue(card(0).requestFocus())
        assertTrue(card(0).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
        layout()
        assertSame(card(1), binding.contentRows.focusedChild)
        card(1).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        layout()
        card(2).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        layout()
        assertSame(card(2), binding.contentRows.focusedChild)
        grid.startMoving(apps[2])
        layout()
        card(2).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        await { grid.currentList[5] == apps[2] }
        layout()
        assertEquals(apps[2], grid.currentList[5])
        assertSame(card(5), binding.contentRows.focusedChild)
        card(5).dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(9L, orders.single().first)
        assertEquals(apps[2], orders.single().second[5])
    }

    @Test
    fun verticalFocusSearchScrollsGridAndReachesTheFollowingRow() {
        activity.setContentView(binding.root)
        val gridCategory = category.copy(layoutType = CategoryLayoutType.GRID, apps = catalog(300))
        val following = category.copy(id = 2, name = "Following")
        renderer.render(state.copy(apps = emptyList(), sections = listOf(gridCategory, following)))
        layout()
        val grid = grids().single()
        val list = binding.contentRows
        val first = requireNotNull(list.findViewHolderForAdapterPosition(list.absolutePosition(grid, 0))).itemView
        assertTrue(first.requestFocus())
        repeat(20) { index ->
            val focused = requireNotNull(list.focusedChild)
            val next = requireNotNull(focused.focusSearch(View.FOCUS_DOWN))
            assertTrue(next.requestFocus())
            layout()
            val holder = list.getChildViewHolder(requireNotNull(list.focusedChild))
            assertSame(grid, holder.bindingAdapter)
            assertEquals((index + 1) * 3, holder.bindingAdapterPosition)
        }
        assertTrue(gridHolders(grid).size < 30)
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(list.absolutePosition(grid, 297), 100)
        layout()
        val last = requireNotNull(list.findViewHolderForAdapterPosition(list.absolutePosition(grid, 297))).itemView
        assertTrue(last.requestFocus())
        assertTrue(requireNotNull(last.focusSearch(View.FOCUS_DOWN)).requestFocus())
        layout()
        assertTrue(rows().last().apps.hasFocus())
    }

    private fun gridHolders(adapter: AppAdapter) = (0 until binding.contentRows.childCount)
        .map { binding.contentRows.getChildViewHolder(binding.contentRows.getChildAt(it)) }
        .filter { it.bindingAdapter === adapter }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue(condition())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun catalog(count: Int) = (0 until count).map { index ->
        LauncherApp(
            ComponentName("app.$index", "Main"),
            "App $index",
            ColorDrawable(Color.BLUE),
            1,
            Process.myUserHandle()
        )
    }

    private fun layout() {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY)
            )
            binding.root.layout(0, 0, 1000, 700)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}
