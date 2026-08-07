package com.sergioasenjo.ltvlauncher.applications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.databinding.ItemAppBinding

class AppAdapter(
    private val onAppClick: (LauncherApp) -> Unit,
) : ListAdapter<LauncherApp, AppAdapter.AppViewHolder>(AppDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding, onAppClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        onAppClick: (LauncherApp) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var app: LauncherApp? = null

        init {
            binding.root.setOnClickListener { app?.let(onAppClick) }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.isSelected = focused
                val scale = if (focused) 1.07f else 1f
                view.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
            }
        }

        fun bind(app: LauncherApp) {
            this.app = app
            binding.artwork.setImageDrawable(app.artwork)
            binding.name.text = app.label
            binding.root.contentDescription = app.label
        }
    }

    private object AppDiffCallback : DiffUtil.ItemCallback<LauncherApp>() {
        override fun areItemsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem.componentName == newItem.componentName && oldItem.user == newItem.user

        override fun areContentsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem.componentName == newItem.componentName && oldItem.label == newItem.label
    }
}
