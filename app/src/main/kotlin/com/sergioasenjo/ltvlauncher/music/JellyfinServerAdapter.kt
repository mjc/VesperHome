package com.sergioasenjo.ltvlauncher.music

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ItemJellyfinServerBinding

class JellyfinServerAdapter(private val onClick: (JellyfinServer) -> Unit) :
    ListAdapter<JellyfinServer, JellyfinServerAdapter.ServerViewHolder>(ServerDiffCallback) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).Id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemJellyfinServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ServerViewHolder(private val binding: ItemJellyfinServerBinding, onClick: (JellyfinServer) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        private var server: JellyfinServer? = null

        init {
            binding.root.setOnClickListener { server?.let(onClick) }
        }

        fun bind(server: JellyfinServer) {
            this.server = server
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
