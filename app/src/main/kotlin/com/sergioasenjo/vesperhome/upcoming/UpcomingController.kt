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
    onItemSelected: (UpcomingMediaItem) -> Unit
) {
    private val adapter = UpcomingAdapter(onItemSelected)
    private var appearance = LauncherAppearance()

    init {
        binding.upcomingItems.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.upcomingItems.adapter = adapter
        binding.upcomingItems.itemAnimator = null
    }

    suspend fun load() {
        val items = try {
            repository.upcoming()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        binding.upcomingSection.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.upcomingCount.text = binding.root.resources.getQuantityString(
            R.plurals.upcoming_item_count,
            items.size,
            items.size
        )
        adapter.submitList(items)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        binding.upcomingAccent.backgroundTintList = ColorStateList.valueOf(appearance.palette.focus)
        binding.upcomingTitle.setTextColor(appearance.palette.primaryText)
        binding.upcomingCount.setTextColor(appearance.palette.focus)
        adapter.setAppearance(appearance)
    }
}
