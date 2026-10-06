package com.sergioasenjo.vesperhome.upcoming

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewLauncherContentBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import kotlinx.coroutines.CancellationException

class UpcomingController(
    context: Context,
    private val binding: ViewLauncherContentBinding,
    private val repository: UpcomingRepository,
    actionDescription: Int,
    onItemSelected: (UpcomingMediaItem) -> Unit
) {
    private val adapter = UpcomingAdapter(actionDescription, onItemSelected)
    private var appearance = LauncherAppearance()
    private var enabled = true
    private var items: List<UpcomingMediaItem> = emptyList()

    init {
        binding.upcomingItems.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.upcomingItems.adapter = adapter
        binding.upcomingItems.itemAnimator = null
    }

    suspend fun load(forceRefresh: Boolean = false) {
        if (!enabled) return
        items = try {
            repository.upcoming(forceRefresh = forceRefresh)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        renderVisibility()
        binding.upcomingCount.text = binding.root.resources.getQuantityString(
            R.plurals.upcoming_item_count,
            items.size,
            items.size
        )
        adapter.refreshDateLabels()
        adapter.submitList(items)
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (this.enabled == enabled) return false
        this.enabled = enabled
        renderVisibility()
        return enabled
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        binding.upcomingAccent.backgroundTintList = ColorStateList.valueOf(appearance.palette.focus)
        binding.upcomingTitle.setTextColor(appearance.palette.primaryText)
        binding.upcomingCount.setTextColor(appearance.palette.focus)
        adapter.setAppearance(appearance)
    }

    fun setActionDescription(actionDescription: Int) {
        adapter.setActionDescription(actionDescription)
    }

    private fun renderVisibility() {
        binding.upcomingSection.visibility = if (enabled && items.isNotEmpty()) View.VISIBLE else View.GONE
    }
}
