package com.sergioasenjo.ltvlauncher.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.load
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ItemCategoryMembershipBinding

class CategoryMembershipAdapter(private val onClick: (CategoryMembershipItem) -> Unit) :
    ListAdapter<CategoryMembershipItem, CategoryMembershipAdapter.MembershipViewHolder>(MembershipDiffCallback) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).app.packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MembershipViewHolder {
        val binding = ItemCategoryMembershipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MembershipViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: MembershipViewHolder, position: Int) {
        holder.bind(getItem(position))
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

        fun bind(item: CategoryMembershipItem) {
            this.item = item
            binding.artwork.load(item.app.customBannerFile ?: item.app.artworkFile ?: item.app.artwork) {
                item.app.customBannerRevision?.let { revision ->
                    memoryCacheKey("custom-banner:${item.app.packageName}:$revision")
                }
                placeholder(item.app.artwork.asImage())
            }
            binding.name.text = item.app.label
            binding.membershipStatus.setText(
                if (item.included) R.string.included_in_category else R.string.not_included_in_category
            )
            val description = if (item.included) {
                R.string.remove_app_from_category_description
            } else {
                R.string.add_app_to_category_description
            }
            binding.root.contentDescription = binding.root.resources.getString(description, item.app.label)
        }
    }

    private object MembershipDiffCallback : DiffUtil.ItemCallback<CategoryMembershipItem>() {
        override fun areItemsTheSame(oldItem: CategoryMembershipItem, newItem: CategoryMembershipItem): Boolean =
            oldItem.app.packageName == newItem.app.packageName

        override fun areContentsTheSame(oldItem: CategoryMembershipItem, newItem: CategoryMembershipItem): Boolean =
            oldItem.app.label == newItem.app.label &&
                oldItem.app.customBannerRevision == newItem.app.customBannerRevision &&
                oldItem.included == newItem.included
    }
}
