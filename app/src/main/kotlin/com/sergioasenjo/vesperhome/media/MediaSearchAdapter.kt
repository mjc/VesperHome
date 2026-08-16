package com.sergioasenjo.vesperhome.media

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemMediaSearchResultBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class MediaSearchAdapter(private val onClick: (MediaCardItem) -> Unit) :
    ListAdapter<MediaCardItem, MediaSearchAdapter.MediaViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder = MediaViewHolder(
        ItemMediaSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class MediaViewHolder(private val binding: ItemMediaSearchResultBinding, onClick: (MediaCardItem) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {
        private var item: MediaCardItem? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.setOnClickListener { item?.let(onClick) }
            binding.root.setOnFocusChangeListener { _, focused -> updateFocus(focused) }
        }

        fun bind(item: MediaCardItem, appearance: LauncherAppearance) {
            this.item = item
            this.appearance = appearance
            val context = binding.root.context
            val providerLabel = context.getString(
                if (item.provider == MediaProvider.SONARR) R.string.sonarr else R.string.radarr
            )
            val status = when (item) {
                is MediaSearchResult -> if (item.alreadyAdded) {
                    context.getString(
                        R.string.media_badge_added
                    )
                } else {
                    providerLabel
                }

                is TrackedMedia -> trackedStatus(item)
            }
            val metadata = buildList {
                if (item.year > 0) add(item.year.toString())
                add(providerLabel)
                if (item is TrackedMedia && item.detail.isNotBlank()) {
                    add(context.getString(R.string.media_episode_availability, item.detail))
                }
            }.joinToString("  ·  ")
            binding.title.text = item.title
            binding.metadata.text = metadata
            binding.badge.text = status
            binding.progress.visibility = if (item is TrackedMedia && item.progress != null) View.VISIBLE else View.GONE
            binding.progress.progress = (item as? TrackedMedia)?.progress ?: 0
            binding.poster.imageTintList = null
            binding.poster.load(item.posterUrl) {
                val fallback = if (item.provider ==
                    MediaProvider.SONARR
                ) {
                    R.drawable.ic_tv_series
                } else {
                    R.drawable.ic_movie
                }
                placeholder(fallback)
                error(fallback)
            }
            binding.root.contentDescription = context.getString(
                R.string.media_card_description,
                item.title,
                item.year,
                providerLabel,
                status
            )
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.title.setTextColor(appearance.palette.primaryText)
            binding.metadata.setTextColor(appearance.palette.secondaryText)
            binding.badge.setTextColor(appearance.palette.primaryText)
            binding.root.setCardBackgroundColor(appearance.palette.surface)
            binding.root.setStrokeColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(appearance.palette.focus, Color.TRANSPARENT)
                )
            )
            updateFocus(binding.root.hasFocus())
        }

        fun recycle() {
            binding.poster.load(null)
            binding.root.animate().cancel()
            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
        }

        private fun trackedStatus(item: TrackedMedia): String {
            val context = binding.root.context
            return when (item.state) {
                TrackedMediaState.MONITORED -> context.getString(R.string.media_status_monitored)

                TrackedMediaState.SEARCHING -> context.getString(R.string.media_status_searching)

                TrackedMediaState.QUEUED -> context.getString(R.string.media_status_queued)

                TrackedMediaState.DOWNLOADING -> context.getString(
                    R.string.media_status_downloading,
                    item.progress ?: 0
                )

                TrackedMediaState.AVAILABLE -> context.getString(R.string.media_status_available)

                TrackedMediaState.FAILED -> context.getString(R.string.media_status_failed)
            }
        }

        private fun updateFocus(focused: Boolean) {
            binding.root.strokeWidth = if (focused && appearance.showFocusOutline) dp(2) else 0
            val scale = if (focused) 1.045f else 1f
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

    private object DiffCallback : DiffUtil.ItemCallback<MediaCardItem>() {
        override fun areItemsTheSame(oldItem: MediaCardItem, newItem: MediaCardItem): Boolean =
            oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: MediaCardItem, newItem: MediaCardItem): Boolean = oldItem == newItem
    }
}
