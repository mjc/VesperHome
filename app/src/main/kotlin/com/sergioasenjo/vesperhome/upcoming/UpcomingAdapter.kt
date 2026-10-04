package com.sergioasenjo.vesperhome.upcoming

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.ErrorResult
import coil3.request.error
import coil3.request.placeholder
import coil3.result
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ItemUpcomingMediaBinding
import com.sergioasenjo.vesperhome.launcher.handleContainedHorizontalFocus
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UpcomingAdapter(private val onClick: (UpcomingMediaItem) -> Unit) :
    ListAdapter<UpcomingMediaItem, UpcomingAdapter.UpcomingViewHolder>(DiffCallback) {
    private var appearance = LauncherAppearance()
    private var recyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpcomingViewHolder = UpcomingViewHolder(
        ItemUpcomingMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
        { event, source -> recyclerView?.handleContainedHorizontalFocus(event, source) ?: true }
    )

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: UpcomingViewHolder, position: Int) {
        holder.bind(getItem(position), appearance)
    }

    override fun onBindViewHolder(holder: UpcomingViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(DATE_LABEL_PAYLOAD)) {
            holder.bindDateLabel(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: UpcomingViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    fun refreshDateLabels() {
        notifyItemRangeChanged(0, itemCount, DATE_LABEL_PAYLOAD)
    }

    class UpcomingViewHolder(
        private val binding: ItemUpcomingMediaBinding,
        onClick: (UpcomingMediaItem) -> Unit,
        onHorizontalFocusKey: (KeyEvent, View) -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private val background = GradientDrawable()
        private val artworkBackground = GradientDrawable()
        private var appearance = LauncherAppearance()
        private var item: UpcomingMediaItem? = null

        init {
            binding.root.background = background
            binding.root.clipToOutline = true
            binding.artworkBackground.background = artworkBackground
            binding.artworkBackground.clipToOutline = true
            binding.root.setOnClickListener { item?.let(onClick) }
            binding.root.setOnKeyListener { view, _, event -> onHorizontalFocusKey(event, view) }
            binding.root.setOnFocusChangeListener { _, focused -> updateFocus(focused) }
        }

        fun bind(item: UpcomingMediaItem, appearance: LauncherAppearance) {
            val reloadArtwork = this.item == null || this.item?.imageUrl != item.imageUrl ||
                (item.imageUrl != null && binding.artwork.result is ErrorResult)
            this.item = item
            this.appearance = appearance
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            binding.whenText.setTextColor(appearance.palette.focus)
            binding.title.text = item.title
            binding.title.setTextColor(appearance.palette.primaryText)
            binding.detail.text = item.detail.ifBlank { binding.root.context.getString(R.string.movie) }
            binding.detail.setTextColor(appearance.palette.secondaryText)
            artworkBackground.apply {
                cornerRadius = dp(ARTWORK_CORNER_RADIUS_DP).toFloat()
                setColor(appearance.palette.surface)
            }
            binding.artwork.imageTintList = null
            if (reloadArtwork) {
                binding.artwork.load(item.imageUrl) {
                    placeholder(R.drawable.ic_upcoming)
                    error(R.drawable.ic_upcoming)
                }
            }
            bindDateLabel(item)
            updateFocus(binding.root.hasFocus())
        }

        fun bindDateLabel(item: UpcomingMediaItem) {
            binding.whenText.text = formatWhen(binding.root.context, item)
            binding.root.contentDescription = binding.root.context.getString(
                R.string.upcoming_open_jellyfin,
                binding.whenText.text,
                item.title,
                binding.detail.text
            )
        }

        fun recycle() {
            item = null
            binding.artwork.dispose()
            binding.artwork.setImageDrawable(null)
            binding.root.animate().cancel()
            binding.root.scaleX = 1f
            binding.root.scaleY = 1f
        }

        private fun updateFocus(focused: Boolean) {
            val palette = appearance.palette
            background.apply {
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setColor(if (focused) palette.focusedSurface else Color.TRANSPARENT)
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
            const val CORNER_RADIUS_DP = 9
            const val ARTWORK_CORNER_RADIUS_DP = 10
            const val FOCUS_STROKE_DP = 2
            const val FOCUSED_SCALE = 1.05f
            const val FOCUS_DURATION_MS = 140L
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<UpcomingMediaItem>() {
        override fun areItemsTheSame(oldItem: UpcomingMediaItem, newItem: UpcomingMediaItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UpcomingMediaItem, newItem: UpcomingMediaItem): Boolean =
            oldItem == newItem
    }

    private companion object {
        val DATE_LABEL_PAYLOAD = Any()

        fun formatWhen(context: Context, item: UpcomingMediaItem): String {
            val day = dayLabel(context, item.startsAtMillis)
            val timing = if (item.type == UpcomingMediaType.EPISODE) {
                DateFormat.getTimeFormat(context).format(Date(item.startsAtMillis))
            } else {
                context.getString(
                    when (item.type) {
                        UpcomingMediaType.CINEMA -> R.string.in_cinemas
                        UpcomingMediaType.DIGITAL -> R.string.digital_release
                        UpcomingMediaType.PHYSICAL -> R.string.physical_release
                        UpcomingMediaType.EPISODE -> error("Episode timing is handled separately")
                    }
                )
            }
            return context.getString(R.string.upcoming_day_and_time, day, timing)
        }

        fun dayLabel(context: Context, startsAtMillis: Long): String {
            val target = Calendar.getInstance().apply { timeInMillis = startsAtMillis }
            val today = Calendar.getInstance()
            if (target.sameDay(today)) return context.getString(R.string.today)
            today.add(Calendar.DAY_OF_YEAR, 1)
            if (target.sameDay(today)) return context.getString(R.string.tomorrow)
            return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(startsAtMillis))
        }

        fun Calendar.sameDay(other: Calendar): Boolean = get(Calendar.ERA) == other.get(Calendar.ERA) &&
            get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    }
}
