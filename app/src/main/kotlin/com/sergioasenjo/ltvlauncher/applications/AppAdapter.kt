package com.sergioasenjo.ltvlauncher.applications

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.ltvlauncher.databinding.ItemAppBinding

class AppAdapter(
    private val onAppClick: (LauncherApp) -> Unit,
    private val onAppLongClick: (LauncherApp, AppAdapter) -> Unit,
    private val onManualOrderChanged: (List<LauncherApp>) -> Unit = {}
) : ListAdapter<LauncherApp, AppAdapter.AppViewHolder>(AppDiffCallback) {
    private var movingPackageName: String? = null
    private var itemHeight = 176
    private var itemWidth = 244
    private var movementStride = 1

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val app = getItem(position)
        return (app.packageName.hashCode().toLong() shl 32) xor
            app.user.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding, onAppClick, { app -> onAppLongClick(app, this) }, ::handleMoveKey)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            width = itemWidth
            height = itemHeight
        }
        holder.bind(getItem(position), getItem(position).packageName == movingPackageName)
    }

    fun setItemSize(width: Int, height: Int, gridColumns: Int? = null) {
        movementStride = gridColumns ?: 1
        if (itemWidth == width && itemHeight == height) return
        itemWidth = width
        itemHeight = height
        notifyItemRangeChanged(0, itemCount)
    }

    fun startMoving(app: LauncherApp) {
        movingPackageName = app.packageName
        notifyPackageChanged(app.packageName)
    }

    private fun handleMoveKey(app: LauncherApp, keyCode: Int): Boolean {
        if (movingPackageName != app.packageName) return false
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_BACK
        ) {
            movingPackageName = null
            notifyPackageChanged(app.packageName)
            return true
        }
        val offset = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> if (movementStride > 1) -movementStride else return false
            KeyEvent.KEYCODE_DPAD_DOWN -> if (movementStride > 1) movementStride else return false
            else -> return false
        }
        val oldPosition = currentList.indexOfFirst { it.packageName == app.packageName }
        val newPosition = (oldPosition + offset).coerceIn(0, currentList.lastIndex)
        if (oldPosition == newPosition) return true
        val reordered = currentList.toMutableList().apply { add(newPosition, removeAt(oldPosition)) }
        submitList(reordered)
        onManualOrderChanged(reordered)
        return true
    }

    private fun notifyPackageChanged(packageName: String) {
        currentList.indexOfFirst { it.packageName == packageName }
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let(::notifyItemChanged)
    }

    class AppViewHolder(
        private val binding: ItemAppBinding,
        onAppClick: (LauncherApp) -> Unit,
        onAppLongClick: (LauncherApp) -> Unit,
        onMoveKey: (LauncherApp, Int) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private var app: LauncherApp? = null

        init {
            binding.root.setOnClickListener { app?.let(onAppClick) }
            binding.root.setOnLongClickListener {
                app?.let(onAppLongClick)
                app != null
            }
            binding.root.setOnKeyListener { _, keyCode, event ->
                event.action == KeyEvent.ACTION_DOWN && app?.let { onMoveKey(it, keyCode) } == true
            }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.isSelected = focused
                val scale = if (focused) 1.07f else 1f
                view.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
            }
        }

        fun bind(app: LauncherApp, moving: Boolean) {
            this.app = app
            binding.root.isActivated = moving
            binding.artwork.setImageDrawable(app.artwork)
            binding.name.text = app.label
            binding.root.contentDescription = app.label
        }
    }

    private object AppDiffCallback : DiffUtil.ItemCallback<LauncherApp>() {
        override fun areItemsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem.packageName == newItem.packageName && oldItem.user == newItem.user

        override fun areContentsTheSame(oldItem: LauncherApp, newItem: LauncherApp): Boolean =
            oldItem.packageName == newItem.packageName &&
                oldItem.label == newItem.label &&
                oldItem.isFavorite == newItem.isFavorite &&
                oldItem.isHidden == newItem.isHidden &&
                oldItem.isTvApp == newItem.isTvApp &&
                oldItem.artworkVersion == newItem.artworkVersion &&
                oldItem.manualOrder == newItem.manualOrder &&
                oldItem.lastUsedAt == newItem.lastUsedAt
    }
}
