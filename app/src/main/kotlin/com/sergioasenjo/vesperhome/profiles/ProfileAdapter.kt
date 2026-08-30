package com.sergioasenjo.vesperhome.profiles

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemProfileBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

internal class ProfileAdapter(private val onSelected: (LayoutProfile) -> Unit) :
    ListAdapter<LayoutProfile, ProfileAdapter.ProfileViewHolder>(DiffCallback) {
    private var activeProfileId: String? = null
    private var appearance = LauncherAppearance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder = ProfileViewHolder(
        ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onSelected
    )

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == activeProfileId, appearance)
    }

    fun render(activeProfileId: String?, appearance: LauncherAppearance) {
        if (this.activeProfileId == activeProfileId && this.appearance == appearance) return
        this.activeProfileId = activeProfileId
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    internal class ProfileViewHolder(private val binding: ItemProfileBinding, onSelected: (LayoutProfile) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        private var profile: LayoutProfile? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.setOnClickListener { profile?.let(onSelected) }
            binding.root.setOnFocusChangeListener { _, focused -> renderBackground(focused) }
        }

        fun bind(profile: LayoutProfile, active: Boolean, appearance: LauncherAppearance) {
            this.profile = profile
            this.appearance = appearance
            binding.name.text = profile.name
            binding.status.setText(if (active) R.string.active_profile else R.string.select_profile_actions)
            renderBackground(binding.root.hasFocus())
        }

        private fun renderBackground(focused: Boolean) {
            val palette = appearance.palette
            binding.root.setBackgroundColor(if (focused) palette.focusedSurface else palette.surface)
            binding.name.setTextColor(if (focused) palette.focusedText else palette.primaryText)
            binding.status.setTextColor(if (focused) palette.focusedText else palette.secondaryText)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LayoutProfile>() {
        override fun areItemsTheSame(oldItem: LayoutProfile, newItem: LayoutProfile): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LayoutProfile, newItem: LayoutProfile): Boolean = oldItem == newItem
    }
}
