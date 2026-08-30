package com.sergioasenjo.vesperhome.backup

import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemBackupBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import java.util.Date

class BackupAdapter(private val onSelected: (BackupFileEntry) -> Unit) :
    ListAdapter<BackupFileEntry, BackupAdapter.BackupViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder = BackupViewHolder(
        ItemBackupBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onSelected
    )

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class BackupViewHolder(private val binding: ItemBackupBinding, onSelected: (BackupFileEntry) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        private var entry: BackupFileEntry? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.setOnClickListener { entry?.let(onSelected) }
            binding.root.setOnFocusChangeListener { _, focused -> renderBackground(focused) }
        }

        fun bind(entry: BackupFileEntry, appearance: LauncherAppearance) {
            this.entry = entry
            this.appearance = appearance
            val context = binding.root.context
            binding.name.text = when (entry.kind) {
                BackupKind.MANUAL -> entry.file.name

                BackupKind.SAFETY -> context.getString(
                    if (entry.safetyReason == SafetyBackupReason.BEFORE_RESTORE) {
                        R.string.safety_backup_before_restore
                    } else {
                        R.string.safety_backup_scheduled
                    }
                )
            }
            binding.details.text = context.getString(
                R.string.backup_details,
                DateFormat.getMediumDateFormat(context).format(Date(entry.createdAt)),
                DateFormat.getTimeFormat(context).format(Date(entry.createdAt)),
                Formatter.formatShortFileSize(context, entry.size)
            )
            binding.root.contentDescription = context.getString(
                R.string.backup_content_description,
                binding.name.text,
                binding.details.text
            )
            binding.actionHint.setText(
                if (entry.kind == BackupKind.SAFETY) {
                    R.string.select_safety_backup_actions
                } else {
                    R.string.select_backup_actions
                }
            )
            renderBackground(binding.root.hasFocus())
        }

        private fun renderBackground(focused: Boolean) {
            val palette = appearance.palette
            binding.root.setBackgroundColor(if (focused) palette.focusedSurface else palette.surface)
            val primary = if (focused) palette.focusedText else palette.primaryText
            val secondary = if (focused) palette.focusedText else palette.secondaryText
            binding.name.setTextColor(primary)
            binding.details.setTextColor(secondary)
            binding.actionHint.setTextColor(secondary)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BackupFileEntry>() {
        override fun areItemsTheSame(oldItem: BackupFileEntry, newItem: BackupFileEntry): Boolean =
            oldItem.file.path == newItem.file.path

        override fun areContentsTheSame(oldItem: BackupFileEntry, newItem: BackupFileEntry): Boolean =
            oldItem == newItem
    }
}
