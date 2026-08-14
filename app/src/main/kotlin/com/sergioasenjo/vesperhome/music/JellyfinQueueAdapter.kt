package com.sergioasenjo.vesperhome.music

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemJellyfinQueueTrackBinding
import com.sergioasenjo.vesperhome.launcher.handleContainedHorizontalFocus
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import java.util.Locale

internal data class JellyfinQueueTrackUi(val track: JellyfinTrack, val current: Boolean, val playing: Boolean)

internal class JellyfinQueueAdapter(private val onTrackClick: (JellyfinTrack) -> Unit) :
    ListAdapter<JellyfinQueueTrackUi, JellyfinQueueAdapter.TrackViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()
    private var recyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).track.id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder = TrackViewHolder(
        ItemJellyfinQueueTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onTrackClick,
        { event, source -> recyclerView?.handleContainedHorizontalFocus(event, source, columns = 1) ?: true }
    )

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position), position, appearance)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    class TrackViewHolder(
        private val binding: ItemJellyfinQueueTrackBinding,
        onTrackClick: (JellyfinTrack) -> Unit,
        onHorizontalFocusKey: (KeyEvent, View) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private val background = GradientDrawable()
        private var item: JellyfinQueueTrackUi? = null
        private var appearance = LauncherAppearance()

        init {
            binding.root.background = background
            binding.root.setOnClickListener { item?.track?.let(onTrackClick) }
            binding.root.setOnKeyListener { view, _, event -> onHorizontalFocusKey(event, view) }
            binding.root.setOnFocusChangeListener { _, focused -> updateFocus(focused) }
        }

        fun bind(item: JellyfinQueueTrackUi, position: Int, appearance: LauncherAppearance) {
            this.item = item
            this.appearance = appearance
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.number.text = (position + 1).toString()
            binding.title.text = item.track.title
            binding.artist.text = item.track.artist
            binding.duration.text = formatDuration(item.track.durationMillis)
            binding.state.text = when {
                item.current && item.playing -> binding.root.context.getString(R.string.now_playing)
                item.current -> binding.root.context.getString(R.string.current_track)
                else -> ""
            }
            binding.state.visibility = if (item.current) View.VISIBLE else View.INVISIBLE
            binding.state.setTextColor(if (item.current) appearance.palette.panel else Color.TRANSPARENT)
            binding.state.background = GradientDrawable().apply {
                cornerRadius = dp(STATE_CORNER_RADIUS_DP).toFloat()
                setColor(if (item.current) appearance.palette.primaryText else Color.TRANSPARENT)
            }
            binding.state.setPadding(
                dp(STATE_PADDING_HORIZONTAL_DP),
                dp(STATE_PADDING_VERTICAL_DP),
                dp(STATE_PADDING_HORIZONTAL_DP),
                dp(STATE_PADDING_VERTICAL_DP)
            )
            binding.number.setTextColor(
                if (item.current) appearance.palette.focus else appearance.palette.secondaryText
            )
            binding.title.setTextColor(
                if (item.current) appearance.palette.focus else appearance.palette.primaryText
            )
            binding.artist.setTextColor(appearance.palette.secondaryText)
            binding.duration.setTextColor(appearance.palette.secondaryText)
            binding.root.contentDescription = binding.root.context.getString(
                R.string.queue_track_description,
                position + 1,
                item.track.title,
                item.track.artist,
                binding.duration.text,
                binding.state.text
            )
            updateFocus(binding.root.hasFocus())
        }

        private fun updateFocus(focused: Boolean) {
            val palette = appearance.palette
            background.apply {
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setColor(
                    if (focused) {
                        palette.focusedSurface
                    } else if (item?.current == true) {
                        palette.focus.withAlpha(CURRENT_ROW_FILL_ALPHA)
                    } else {
                        Color.TRANSPARENT
                    }
                )
                setStroke(
                    when {
                        focused && appearance.showFocusOutline -> dp(FOCUS_STROKE_DP)
                        item?.current == true -> dp(CURRENT_ROW_STROKE_DP)
                        else -> 0
                    },
                    if (focused || item?.current == true) palette.focus else Color.TRANSPARENT
                )
            }
        }

        private fun dp(value: Int): Int = (value * binding.root.resources.displayMetrics.density).toInt()

        private companion object {
            const val CORNER_RADIUS_DP = 8
            const val FOCUS_STROKE_DP = 2
            const val CURRENT_ROW_STROKE_DP = 2
            const val CURRENT_ROW_FILL_ALPHA = 72
            const val STATE_CORNER_RADIUS_DP = 5
            const val STATE_PADDING_HORIZONTAL_DP = 7
            const val STATE_PADDING_VERTICAL_DP = 3

            fun formatDuration(durationMillis: Long?): String {
                val totalSeconds = durationMillis?.div(1_000)?.coerceAtLeast(0) ?: return "--:--"
                val hours = totalSeconds / 3_600
                val minutes = totalSeconds % 3_600 / 60
                val seconds = totalSeconds % 60
                return if (hours > 0) {
                    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.US, "%d:%02d", minutes, seconds)
                }
            }
        }

        private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private object DiffCallback : DiffUtil.ItemCallback<JellyfinQueueTrackUi>() {
        override fun areItemsTheSame(oldItem: JellyfinQueueTrackUi, newItem: JellyfinQueueTrackUi): Boolean =
            oldItem.track.id == newItem.track.id

        override fun areContentsTheSame(oldItem: JellyfinQueueTrackUi, newItem: JellyfinQueueTrackUi): Boolean =
            oldItem == newItem
    }
}
