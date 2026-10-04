package com.sergioasenjo.vesperhome.launcher

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.Process
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.AppAdapter
import com.sergioasenjo.vesperhome.applications.AppRowView
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.applications.LauncherApp
import com.sergioasenjo.vesperhome.categories.CategoryLayoutType
import com.sergioasenjo.vesperhome.categories.LauncherCategory
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val activity = Robolectric.buildActivity(Activity::class.java).create().get().apply {
        setTheme(R.style.Theme_VesperHome)
    }
    private val binding = ViewLauncherContentBinding.inflate(activity.layoutInflater)
    private val renderer =
        LauncherContentRenderer(activity, binding, binding.musicNext, {}, { _, _ -> }, {}, { _, _ -> })
    private val apps = (0..19).map { index ->
        LauncherApp(
            ComponentName("app.$index", "Main"),
            "App $index",
            ColorDrawable(Color.BLUE),
            1,
            Process.myUserHandle()
        )
    }
    private val category =
        LauncherCategory(1, "Category", 0, ApplicationSortMode.MANUAL, CategoryLayoutType.ROW, 3, 96, apps)
    private val state = LauncherUiState(apps = apps, sections = listOf(category), loading = false)

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
    fun layoutManagersSurviveContentChangesAndGridColumnUpdates() {
        renderer.render(state)
        val row = binding.categoryRows.getChildAt(0) as AppRowView
        val horizontal = row.apps.layoutManager
        val applicationsManager = binding.applicationsRow.apps.layoutManager
        renderer.render(state.copy(applicationSortMode = ApplicationSortMode.ALPHABETICAL))
        assertSame(horizontal, row.apps.layoutManager)
        assertSame(applicationsManager, binding.applicationsRow.apps.layoutManager)
        renderer.render(state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID))))
        val grid = row.apps.layoutManager as GridLayoutManager
        renderer.render(
            state.copy(sections = listOf(category.copy(layoutType = CategoryLayoutType.GRID, gridColumns = 5)))
        )
        assertSame(grid, row.apps.layoutManager)
        assertEquals(5, grid.spanCount)
        renderer.render(state)
        assertTrue(row.apps.layoutManager is LinearLayoutManager)
        assertFalse(row.apps.layoutManager is GridLayoutManager)
        assertEquals(LinearLayoutManager.HORIZONTAL, (row.apps.layoutManager as LinearLayoutManager).orientation)
    }

    @Test
    fun newAndExistingRowsReceiveUpdatedAppearance() {
        val appearance =
            LauncherAppearance(theme = LauncherTheme.LIGHT, showCategoryTitles = false, keyClickSounds = false)
        renderer.render(state.copy(appearance = appearance))
        val original = binding.categoryRows.getChildAt(0) as AppRowView
        renderer.render(
            state.copy(appearance = appearance, sections = listOf(category, category.copy(id = 2, name = "New")))
        )
        assertSame(original, binding.categoryRows.getChildAt(0))
        for (index in 0 until binding.categoryRows.childCount) {
            val row = binding.categoryRows.getChildAt(index) as AppRowView
            assertEquals(View.GONE, row.categoryHeader.visibility)
            assertEquals(appearance.palette.primaryText, row.title.currentTextColor)
            assertFalse(row.apps.isSoundEffectsEnabled)
        }
        renderer.render(state.copy(appearance = appearance.copy(showCategoryTitles = true)))
        assertEquals(1, binding.categoryRows.childCount)
        assertEquals(View.VISIBLE, original.categoryHeader.visibility)
    }

    @Test
    fun emptyGridsAndManualSortChangesKeepManagersAndUpdateGeometry() {
        val gridCategory = category.copy(layoutType = CategoryLayoutType.GRID)
        renderer.render(state.copy(sections = listOf(gridCategory)))
        val row = binding.categoryRows.getChildAt(0) as AppRowView
        val manager = row.apps.layoutManager
        val height = row.apps.layoutParams.height
        val adapter = row.apps.adapter as AppAdapter
        assertTrue(renderer.isReorderable(adapter))
        renderer.render(
            state.copy(
                sections = listOf(gridCategory.copy(apps = emptyList(), sortMode = ApplicationSortMode.ALPHABETICAL))
            )
        )
        assertSame(manager, row.apps.layoutManager)
        assertTrue(row.apps.layoutParams.height < height)
        assertFalse(renderer.isReorderable(adapter))
    }

    private fun layout() {
        shadowOf(Looper.getMainLooper()).idle()
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY)
        )
        binding.root.layout(0, 0, 1000, 700)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
