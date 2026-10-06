package com.sergioasenjo.vesperhome.categories

import android.content.ComponentName
import android.os.Process
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sergioasenjo.vesperhome.applications.LauncherApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryMembershipAdapterTest {
    @Test
    fun artworkVersionChangeRebindsMembershipItem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".instrumented"))
        val app = LauncherApp(
            componentName = ComponentName("example.app", "example.app.Main"),
            label = "Example",
            artwork = context.packageManager.defaultActivityIcon,
            artworkVersion = 42,
            user = Process.myUserHandle()
        )
        val adapter = CategoryMembershipAdapter {}
        val changes = AtomicInteger()
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            adapter.submitList(listOf(CategoryMembershipItem(app, included = true)))
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                    changes.addAndGet(itemCount)
                }
            })
            adapter.submitList(listOf(CategoryMembershipItem(app.copy(artworkVersion = 43), included = true))) {
                committed.countDown()
            }
        }
        assertTrue("Updated list was not committed", committed.await(5, TimeUnit.SECONDS))
        assertEquals("Artwork changes must notify the bound row", 1, changes.get())
        assertEquals(43L, adapter.currentList.single().app.artworkVersion)
    }
}
