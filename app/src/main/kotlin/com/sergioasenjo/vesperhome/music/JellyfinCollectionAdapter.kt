package com.sergioasenjo.vesperhome.music

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemJellyfinCollectionBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

sealed interface JellyfinPickerItem {
    data object Random : JellyfinPickerItem

    data class Collection(val collection: JellyfinMusicCollection) : JellyfinPickerItem
}

class JellyfinCollectionAdapter(
    private val onCollectionClick: (JellyfinMusicCollection) -> Unit,
    private val onRandomClick: () -> Unit
) : ListAdapter<JellyfinPickerItem, JellyfinCollectionAdapter.CollectionViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
        JellyfinPickerItem.Random -> Long.MIN_VALUE
        is JellyfinPickerItem.Collection -> item.collection.id.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollectionViewHolder = CollectionViewHolder(
        ItemJellyfinCollectionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onCollectionClick,
        onRandomClick
    )

    override fun onBindViewHolder(holder: CollectionViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    override fun onViewRecycled(holder: CollectionViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class CollectionViewHolder(
        private val binding: ItemJellyfinCollectionBinding,
        private val onCollectionClick: (JellyfinMusicCollection) -> Unit,
        private val onRandomClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val artworkBackground = GradientDrawable()
        private var item: JellyfinPickerItem? = null
        private var appearance = LauncherAppearance()

        init {
            binding.artworkFrame.background = artworkBackground
            binding.artworkFrame.clipToOutline = true
            binding.root.setOnClickListener {
                when (val selectedItem = item) {
                    JellyfinPickerItem.Random -> onRandomClick()
                    is JellyfinPickerItem.Collection -> onCollectionClick(selectedItem.collection)
                    null -> Unit
                }
            }
            binding.root.setOnFocusChangeListener { _, focused -> updateFocus(focused) }
        }

        fun bind(item: JellyfinPickerItem, appearance: LauncherAppearance) {
            this.item = item
            this.appearance = appearance
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            val collection = (item as? JellyfinPickerItem.Collection)?.collection
            val isRandom = item == JellyfinPickerItem.Random
            binding.name.text = collection?.name ?: binding.root.context.getString(R.string.random_songs)
            binding.name.setTextColor(appearance.palette.primaryText)
            binding.count.text = if (collection == null) {
                binding.root.context.getString(R.string.random_songs_description)
            } else {
                binding.root.resources.getQuantityString(
                    R.plurals.music_collection_track_count,
                    collection.trackCount,
                    collection.trackCount
                )
            }
            binding.count.setTextColor(appearance.palette.secondaryText)
            binding.type.text = binding.root.context.getString(
                when {
                    isRandom -> R.string.all_music
                    collection?.type == JellyfinCollectionType.PLAYLIST -> R.string.playlist
                    else -> R.string.album
                }
            )
            binding.type.setTextColor(appearance.palette.focus)
            binding.root.contentDescription = "${binding.name.text}, ${binding.type.text}, ${binding.count.text}"
            if (collection?.artworkUrl == null) {
                val padding = dp(PLACEHOLDER_PADDING_DP)
                binding.artwork.setPadding(padding, padding, padding, padding)
                binding.artwork.imageTintList = ColorStateList.valueOf(appearance.palette.secondaryText)
                binding.artwork.load(if (isRandom) R.drawable.ic_shuffle else R.drawable.ic_music_library)
            } else {
                binding.artwork.setPadding(0, 0, 0, 0)
                binding.artwork.imageTintList = null
                binding.artwork.load(collection.artworkUrl) {
                    error(R.drawable.ic_music_library)
                }
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
            artworkBackground.apply {
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setColor(if (focused) palette.focusedSurface else palette.surface)
                setStroke(
                    if (focused && appearance.showFocusOutline) dp(FOCUS_STROKE_DP) else 0,
                    if (focused) palette.focus else Color.TRANSPARENT
                )
            }
            val scale = if (focused) FOCUSED_SCALE else 1f
            binding.root.animate().cancel()
            if (appearance.selectorTransitionAnimations) {
                binding.root.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_DURATION_MS).start()
            } else {
                binding.root.scaleX = scale
                binding.root.scaleY = scale
            }
        }

        private fun dp(value: Int): Int = (value * binding.root.resources.displayMetrics.density).toInt()

        private companion object {
            const val PLACEHOLDER_PADDING_DP = 52
            const val CORNER_RADIUS_DP = 12
            const val FOCUS_STROKE_DP = 3
            const val FOCUSED_SCALE = 1.06f
            const val FOCUS_DURATION_MS = 140L
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<JellyfinPickerItem>() {
        override fun areItemsTheSame(oldItem: JellyfinPickerItem, newItem: JellyfinPickerItem): Boolean = when {
            oldItem == JellyfinPickerItem.Random && newItem == JellyfinPickerItem.Random -> true

            oldItem is JellyfinPickerItem.Collection && newItem is JellyfinPickerItem.Collection ->
                oldItem.collection.id == newItem.collection.id

            else -> false
        }

        override fun areContentsTheSame(oldItem: JellyfinPickerItem, newItem: JellyfinPickerItem): Boolean =
            oldItem == newItem
    }
}
