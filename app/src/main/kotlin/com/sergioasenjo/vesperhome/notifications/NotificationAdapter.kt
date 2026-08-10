package com.sergioasenjo.vesperhome.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.vesperhome.databinding.ItemNotificationBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class NotificationAdapter(private val onDismiss: (LauncherNotification) -> Unit) :
    ListAdapter<LauncherNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder = NotificationViewHolder(
        ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onDismiss
    )

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class NotificationViewHolder(
        private val binding: ItemNotificationBinding,
        onDismiss: (LauncherNotification) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var notification: LauncherNotification? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.setOnClickListener {
                notification?.takeIf(LauncherNotification::clearable)?.let(onDismiss)
            }
            binding.root.setOnFocusChangeListener { _, focused -> renderBackground(focused) }
        }

        fun bind(notification: LauncherNotification, appearance: LauncherAppearance) {
            this.notification = notification
            this.appearance = appearance
            binding.appLabel.text = notification.appLabel
            binding.title.text = notification.title
            binding.title.visibility = if (notification.title.isBlank()) View.GONE else View.VISIBLE
            binding.text.text = notification.text
            binding.text.visibility = if (notification.text.isBlank()) View.GONE else View.VISIBLE
            binding.actionHint.visibility = if (notification.clearable) View.VISIBLE else View.GONE
            binding.appLabel.setTextColor(appearance.palette.focus)
            binding.title.setTextColor(appearance.palette.primaryText)
            binding.text.setTextColor(appearance.palette.secondaryText)
            binding.actionHint.setTextColor(appearance.palette.secondaryText)
            binding.root.contentDescription = listOf(
                notification.appLabel,
                notification.title,
                notification.text
            ).filter(String::isNotBlank).joinToString(". ")
            renderBackground(binding.root.hasFocus())
        }

        private fun renderBackground(focused: Boolean) {
            val palette = appearance.palette
            binding.root.setBackgroundColor(if (focused) palette.focusedSurface else palette.surface)
            binding.title.setTextColor(if (focused) palette.focusedText else palette.primaryText)
            binding.text.setTextColor(if (focused) palette.focusedText else palette.secondaryText)
            binding.actionHint.setTextColor(if (focused) palette.focusedText else palette.secondaryText)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LauncherNotification>() {
        override fun areItemsTheSame(oldItem: LauncherNotification, newItem: LauncherNotification): Boolean =
            oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: LauncherNotification, newItem: LauncherNotification): Boolean =
            oldItem == newItem
    }
}
