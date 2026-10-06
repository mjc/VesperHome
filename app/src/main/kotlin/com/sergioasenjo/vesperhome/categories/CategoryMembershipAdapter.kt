package com.sergioasenjo.vesperhome.categories

import android.R.attr.state_focused
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.load
import coil3.request.allowHardware
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemCategoryMembershipBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class CategoryMembershipAdapter(private val onClick: (CategoryMembershipItem) -> Unit) :
    ListAdapter<CategoryMembershipItem, CategoryMembershipAdapter.MembershipViewHolder>(MembershipDiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).app.packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MembershipViewHolder {
        val binding = ItemCategoryMembershipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MembershipViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: MembershipViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class MembershipViewHolder(
        private val binding: ItemCategoryMembershipBinding,
        onClick: (CategoryMembershipItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var item: CategoryMembershipItem? = null

        init {
            binding.root.setOnClickListener { item?.let(onClick) }
            binding.root.setOnFocusChangeListener { view, focused -> view.isSelected = focused }
        }

        fun bind(item: CategoryMembershipItem, appearance: LauncherAppearance) {
            this.item = item
            val palette = appearance.palette
            binding.root.background = StateListDrawable().apply {
                addState(
                    intArrayOf(state_focused),
                    GradientDrawable().apply {
                        cornerRadius = dp(binding.root, ITEM_RADIUS_DP).toFloat()
                        setColor(palette.focusedSurface)
                    }
                )
                addState(
                    intArrayOf(),
                    GradientDrawable().apply {
                        cornerRadius = dp(binding.root, ITEM_RADIUS_DP).toFloat()
                        setColor(palette.surface)
                    }
                )
            }
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.artwork.load(item.app.customBannerFile ?: item.app.artworkFile ?: item.app.artwork) {
                if (item.app.customBannerFile == null && item.app.artworkFile != null) {
                    memoryCacheKey("app-banner:${item.app.packageName}:${item.app.artworkVersion}")
                    // Hardware thumbnail imports can stall the graphics buffer queue on NVIDIA TVs.
                    allowHardware(!Build.MANUFACTURER.equals("NVIDIA", ignoreCase = true))
                }
                item.app.customBannerRevision?.let { revision ->
                    memoryCacheKey("custom-banner:${item.app.packageName}:$revision")
                }
                placeholder(item.app.artwork.asImage())
            }
            binding.name.text = item.app.label
            binding.name.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(state_focused), intArrayOf()),
                    intArrayOf(palette.focusedText, palette.primaryText)
                )
            )
            binding.membershipStatus.setText(
                if (item.included) R.string.included_in_category else R.string.not_included_in_category
            )
            binding.membershipStatus.setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(state_focused), intArrayOf()),
                    intArrayOf(palette.focusedText, if (item.included) palette.focus else palette.secondaryText)
                )
            )
            val description = if (item.included) {
                R.string.remove_app_from_category_description
            } else {
                R.string.add_app_to_category_description
            }
            binding.root.contentDescription = binding.root.resources.getString(description, item.app.label)
        }

        private fun dp(view: android.view.View, value: Int): Int =
            (value * view.resources.displayMetrics.density).toInt()

        private companion object {
            const val ITEM_RADIUS_DP = 9
        }
    }

    private object MembershipDiffCallback : DiffUtil.ItemCallback<CategoryMembershipItem>() {
        override fun areItemsTheSame(oldItem: CategoryMembershipItem, newItem: CategoryMembershipItem): Boolean =
            oldItem.app.packageName == newItem.app.packageName

        override fun areContentsTheSame(oldItem: CategoryMembershipItem, newItem: CategoryMembershipItem): Boolean =
            oldItem.app.label == newItem.app.label &&
                oldItem.app.artworkVersion == newItem.app.artworkVersion &&
                oldItem.app.customBannerRevision == newItem.app.customBannerRevision &&
                oldItem.included == newItem.included
    }
}
