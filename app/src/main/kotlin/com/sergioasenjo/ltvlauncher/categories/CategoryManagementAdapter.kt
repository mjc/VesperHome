package com.sergioasenjo.ltvlauncher.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ItemCategoryManagementBinding

class CategoryManagementAdapter(private val onCategoryClick: (CategorySummary) -> Unit) :
    ListAdapter<CategorySummary, CategoryManagementAdapter.CategoryViewHolder>(CategoryDiffCallback) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryManagementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CategoryViewHolder(
        private val binding: ItemCategoryManagementBinding,
        onCategoryClick: (CategorySummary) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var category: CategorySummary? = null

        init {
            binding.category.setOnClickListener { category?.let(onCategoryClick) }
        }

        fun bind(category: CategorySummary) {
            this.category = category
            binding.category.text = binding.root.resources.getQuantityString(
                R.plurals.category_application_count,
                category.appCount,
                category.name,
                category.appCount
            )
        }
    }

    private object CategoryDiffCallback : DiffUtil.ItemCallback<CategorySummary>() {
        override fun areItemsTheSame(oldItem: CategorySummary, newItem: CategorySummary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CategorySummary, newItem: CategorySummary): Boolean =
            oldItem == newItem
    }
}
