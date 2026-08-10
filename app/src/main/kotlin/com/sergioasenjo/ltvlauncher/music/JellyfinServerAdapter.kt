package com.sergioasenjo.ltvlauncher.music

import android.R.attr.state_focused
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ItemJellyfinServerBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

class JellyfinServerAdapter(private val onClick: (JellyfinServer) -> Unit) :
    ListAdapter<JellyfinServer, JellyfinServerAdapter.ServerViewHolder>(ServerDiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).Id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemJellyfinServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class ServerViewHolder(private val binding: ItemJellyfinServerBinding, onClick: (JellyfinServer) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        private var server: JellyfinServer? = null

        init {
            binding.root.setOnClickListener { server?.let(onClick) }
        }

        fun bind(server: JellyfinServer, appearance: LauncherAppearance) {
            this.server = server
            val palette = appearance.palette
            binding.root.backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(state_focused), intArrayOf()),
                intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
            )
            val foreground = ColorStateList(
                arrayOf(intArrayOf(state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
            binding.root.setTextColor(foreground)
            binding.root.iconTint = foreground
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.root.text = server.Name
            binding.root.contentDescription = binding.root.resources.getString(
                R.string.connect_to_jellyfin_server,
                server.Name,
                server.Address
            )
        }
    }

    private object ServerDiffCallback : DiffUtil.ItemCallback<JellyfinServer>() {
        override fun areItemsTheSame(oldItem: JellyfinServer, newItem: JellyfinServer): Boolean =
            oldItem.Id == newItem.Id

        override fun areContentsTheSame(oldItem: JellyfinServer, newItem: JellyfinServer): Boolean =
            oldItem.Name == newItem.Name && oldItem.Address == newItem.Address
    }
}
