package com.sergioasenjo.vesperhome.livetv

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemLiveTvChannelBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class LiveTvChannelAdapter(
    private val onClick: (LiveTvChannel) -> Unit,
    private val onKey: (Int, KeyEvent) -> Boolean
) : ListAdapter<LiveTvChannel, LiveTvChannelAdapter.ChannelViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val channel = getItem(position)
        return "${channel.plugin.flattenToShortString()}:${channel.id}".hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder = ChannelViewHolder(
        ItemLiveTvChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
        onKey
    )

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    override fun onViewRecycled(holder: ChannelViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class ChannelViewHolder(
        private val binding: ItemLiveTvChannelBinding,
        onClick: (LiveTvChannel) -> Unit,
        onKey: (Int, KeyEvent) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private val background = GradientDrawable()
        private val artworkBackground = GradientDrawable()
        private var channel: LiveTvChannel? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.background = background
            binding.root.clipToOutline = true
            binding.artworkBackground.background = artworkBackground
            binding.artworkBackground.clipToOutline = true
            binding.root.setOnClickListener { channel?.let(onClick) }
            binding.root.setOnKeyListener { _, _, event ->
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onKey(it, event) } ?: false
            }
            binding.root.setOnFocusChangeListener { _, focused -> updateFocus(focused) }
        }

        fun bind(channel: LiveTvChannel, appearance: LauncherAppearance) {
            this.channel = channel
            this.appearance = appearance
            val palette = appearance.palette
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.channelNumber.isVisible = !channel.number.isNullOrBlank()
            binding.channelNumber.text = channel.number?.let { number ->
                binding.root.context.getString(R.string.live_tv_channel_number, number)
            }
            binding.channelNumber.setTextColor(palette.focus)
            binding.channelName.text = channel.name
            binding.channelName.setTextColor(palette.primaryText)
            binding.currentProgram.isVisible = !channel.currentProgram.isNullOrBlank()
            binding.currentProgram.text = channel.currentProgram
            binding.currentProgram.setTextColor(palette.secondaryText)
            binding.root.contentDescription = binding.root.context.getString(
                R.string.live_tv_channel_description,
                channel.number ?: channel.name,
                channel.currentProgram ?: channel.name
            )
            artworkBackground.apply {
                cornerRadius = dp(10).toFloat()
                setColor(palette.surface)
            }
            binding.artwork.imageTintList = if (channel.imageUrl == null) {
                ColorStateList.valueOf(palette.primaryText)
            } else {
                null
            }
            binding.artwork.load(channel.imageUrl) {
                placeholder(R.drawable.ic_live_tv)
                error(R.drawable.ic_live_tv)
            }
            updateFocus(binding.root.hasFocus())
        }

        fun recycle() {
            binding.artwork.load(null)
            binding.root.animate().cancel()
            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
        }

        private fun updateFocus(focused: Boolean) {
            val palette = appearance.palette
            background.apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (focused) palette.focusedSurface else palette.surface)
                setStroke(
                    if (focused && appearance.showFocusOutline) dp(2) else 0,
                    if (focused) palette.focus else Color.TRANSPARENT
                )
            }
            val scale = if (focused && appearance.appCardFocusAnimations) 1.04f else 1f
            binding.root.animate().cancel()
            if (appearance.selectorTransitionAnimations) {
                binding.root.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
            } else {
                binding.root.scaleX = scale
                binding.root.scaleY = scale
            }
        }

        private fun dp(value: Int): Int = (value * binding.root.resources.displayMetrics.density).toInt()
    }

    private object DiffCallback : DiffUtil.ItemCallback<LiveTvChannel>() {
        override fun areItemsTheSame(oldItem: LiveTvChannel, newItem: LiveTvChannel): Boolean =
            oldItem.plugin == newItem.plugin && oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LiveTvChannel, newItem: LiveTvChannel): Boolean = oldItem == newItem
    }
}
