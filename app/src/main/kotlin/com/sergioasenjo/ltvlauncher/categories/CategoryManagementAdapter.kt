package com.sergioasenjo.ltvlauncher.categories

import android.R.attr.state_focused
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ItemCategoryManagementBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

class CategoryManagementAdapter(private val onSectionClick: (SectionSummary) -> Unit) :
    ListAdapter<SectionSummary, CategoryManagementAdapter.SectionViewHolder>(SectionDiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemCategoryManagementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding, onSectionClick)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class SectionViewHolder(
        private val binding: ItemCategoryManagementBinding,
        onSectionClick: (SectionSummary) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var section: SectionSummary? = null

        init {
            binding.category.setOnClickListener { section?.let(onSectionClick) }
        }

        fun bind(section: SectionSummary, appearance: LauncherAppearance) {
            this.section = section
            val palette = appearance.palette
            binding.category.backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(state_focused), intArrayOf()),
                intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
            )
            val foreground = ColorStateList(
                arrayOf(intArrayOf(state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
            binding.category.setTextColor(foreground)
            binding.category.iconTint = foreground
            binding.category.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.category.text = when (section) {
                is CategorySummary -> binding.root.resources.getQuantityString(
                    R.plurals.category_application_count,
                    section.appCount,
                    section.name,
                    section.appCount
                )

                is SpacerSummary -> binding.root.resources.getString(R.string.spacer_summary, section.height)

                else -> error("Unsupported launcher section")
            }
        }
    }

    private object SectionDiffCallback : DiffUtil.ItemCallback<SectionSummary>() {
        override fun areItemsTheSame(oldItem: SectionSummary, newItem: SectionSummary): Boolean =
            oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: SectionSummary, newItem: SectionSummary): Boolean = when {
            oldItem is CategorySummary && newItem is CategorySummary -> oldItem == newItem
            oldItem is SpacerSummary && newItem is SpacerSummary -> oldItem == newItem
            else -> false
        }
    }
}
