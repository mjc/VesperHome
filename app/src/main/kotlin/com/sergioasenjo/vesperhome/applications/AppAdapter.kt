package com.sergioasenjo.vesperhome.applications

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.asImage
import coil3.dispose
import coil3.load
import coil3.request.ErrorResult
import coil3.request.allowHardware
import coil3.result
import com.sergioasenjo.vesperhome.databinding.ItemAppBinding
import com.sergioasenjo.vesperhome.launcher.absolutePosition
import com.sergioasenjo.vesperhome.launcher.handleContainedHorizontalFocus
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class AppAdapter(
    private val onAppClick: (LauncherApp) -> Unit,
    private val onAppLongClick: (LauncherApp, AppAdapter) -> Unit,
    private val onManualOrderChanged: (List<LauncherApp>) -> Unit = {}
) : ListAdapter<LauncherApp, AppAdapter.AppViewHolder>(AppDiffCallback) {
    private data class AppKey(val packageName: String, val user: android.os.UserHandle)

    private var movingAppKey: AppKey? = null
    private var movingOrder: List<LauncherApp>? = null
    private var recyclerView: RecyclerView? = null
    private var itemHeight = 176
    private var itemWidth = 244
    private var movementStride = 1
    private var appearance = LauncherAppearance()

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
        return AppViewHolder(
            binding,
            onAppClick,
            { app -> onAppLongClick(app, this) },
            ::handleMoveKey,
            { event, source, onEdge ->
                recyclerView?.handleContainedHorizontalFocus(
                    event,
                    source,
                    movementStride.takeIf { it > 1 },
                    onEdge
                ) ?: true
            },
            ::boundaryDirection,
            ::isMovementActive
        )
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            width = itemWidth
            height = itemHeight
        }
        holder.bind(getItem(position), isMoving(getItem(position)), appearance)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (this.recyclerView === recyclerView) this.recyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onViewRecycled(holder: AppViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setItemSize(width: Int, height: Int, gridColumns: Int? = null) {
        movementStride = gridColumns ?: 1
        if (itemWidth == width && itemHeight == height) return
        itemWidth = width
        itemHeight = height
        notifyItemRangeChanged(0, itemCount)
    }

    fun startMoving(app: LauncherApp) {
        movingAppKey = app.key()
        movingOrder = currentList.toList()
        notifyAppChanged(app.key())
        requestMovingAppFocus()
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        notifyItemRangeChanged(0, itemCount)
    }

    private fun handleMoveKey(keyCode: Int): Boolean {
        val movingKey = movingAppKey ?: return false
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_BACK
        ) {
            val finalOrder = movingOrder ?: currentList
            movingAppKey = null
            movingOrder = null
            submitList(finalOrder) { notifyAppChanged(movingKey) }
            onManualOrderChanged(finalOrder)
            return true
        }
        val offset = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> if (movementStride > 1) -movementStride else return false
            KeyEvent.KEYCODE_DPAD_DOWN -> if (movementStride > 1) movementStride else return false
            else -> return false
        }
        val order = movingOrder ?: currentList
        val oldPosition = order.indexOfFirst { it.key() == movingKey }
        if (oldPosition == RecyclerView.NO_POSITION) return true
        val newPosition = (oldPosition + offset).coerceIn(0, order.lastIndex)
        if (oldPosition == newPosition) return true
        val reordered = order.toMutableList().apply { add(newPosition, removeAt(oldPosition)) }
        movingOrder = reordered
        submitList(reordered) { requestMovingAppFocus() }
        return true
    }

    private fun boundaryDirection(app: LauncherApp, keyCode: Int): Int {
        val order = movingOrder ?: currentList
        val targetKey = movingAppKey ?: app.key()
        val position = order.indexOfFirst { it.key() == targetKey }
        if (position == RecyclerView.NO_POSITION) return 0
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                movementStride == 1 && position == 0 -> -1
                movementStride > 1 && position % movementStride == 0 -> -1
                else -> 0
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                movementStride == 1 && position == order.lastIndex -> 1

                movementStride > 1 &&
                    (position % movementStride == movementStride - 1 || position == order.lastIndex) -> 1

                else -> 0
            }

            else -> 0
        }
    }

    private fun isMoving(app: LauncherApp): Boolean = movingAppKey == app.key()

    private fun isMovementActive(): Boolean = movingAppKey != null

    private fun requestMovingAppFocus() {
        val movingKey = movingAppKey ?: return
        val position = currentList.indexOfFirst { it.key() == movingKey }
        if (position == RecyclerView.NO_POSITION) return
        recyclerView?.apply {
            val target = absolutePosition(this@AppAdapter, position)
            if (target == RecyclerView.NO_POSITION) return@apply
            scrollToPosition(target)
            post { findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        }
    }

    private fun notifyAppChanged(appKey: AppKey) {
        currentList.indexOfFirst { it.key() == appKey }
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let(::notifyItemChanged)
    }

    private fun LauncherApp.key(): AppKey = AppKey(packageName, user)

    class AppViewHolder(
        private val binding: ItemAppBinding,
        onAppClick: (LauncherApp) -> Unit,
        onAppLongClick: (LauncherApp) -> Unit,
        onMoveKey: (Int) -> Boolean,
        onHorizontalFocusKey: (KeyEvent, View, (Int) -> Unit) -> Boolean,
        boundaryDirection: (LauncherApp, Int) -> Int,
        isMovementActive: () -> Boolean
    ) : RecyclerView.ViewHolder(binding.root) {
        private var app: LauncherApp? = null
        private var artworkWidth = 0
        private var artworkHeight = 0
        private var appearance = LauncherAppearance()
        private var moving = false
        private var outlineAnimator: ValueAnimator? = null
        private var edgeAnimator: ObjectAnimator? = null
        private val cardBackground = GradientDrawable()

        init {
            binding.root.setOnClickListener { app?.let(onAppClick) }
            binding.root.setOnLongClickListener {
                app?.let(onAppLongClick)
                app != null
            }
            binding.root.setOnKeyListener { _, keyCode, event ->
                val currentApp = app ?: return@setOnKeyListener false
                val movementActive = isMovementActive()
                val direction = boundaryDirection(currentApp, keyCode)
                when {
                    movementActive && keyCode in MOVE_CONTROL_KEYS -> {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            if (direction != 0) {
                                if (event.repeatCount == 0) animateEdgeBump(direction)
                            } else {
                                onMoveKey(keyCode)
                            }
                        }
                        true
                    }

                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onHorizontalFocusKey(event, binding.root, ::animateEdgeBump)
                    }

                    direction != 0 -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) animateEdgeBump(direction)
                        true
                    }

                    event.action == KeyEvent.ACTION_DOWN -> onMoveKey(keyCode)

                    else -> false
                }
            }
            binding.root.setOnFocusChangeListener { view, focused ->
                view.isSelected = focused
                updateFocusAppearance(focused)
            }
            binding.artworkFrame.background = cardBackground
            binding.artworkFrame.clipToOutline = true
        }

        fun bind(app: LauncherApp, moving: Boolean, appearance: LauncherAppearance) {
            val width = binding.root.layoutParams?.width ?: 0
            val height = binding.root.layoutParams?.height ?: 0
            val reloadArtwork = this.app?.hasSameArtwork(app) != true ||
                artworkWidth != width || artworkHeight != height ||
                this.appearance.showAppNames != appearance.showAppNames || binding.artwork.result is ErrorResult
            this.app = app
            artworkWidth = width
            artworkHeight = height
            this.moving = moving
            this.appearance = appearance
            binding.root.isActivated = moving
            binding.root.isSoundEffectsEnabled = appearance.keyClickSounds
            if (reloadArtwork) {
                binding.artwork.load(app.customBannerFile ?: app.artworkFile ?: app.artwork) {
                    if (app.customBannerFile == null && app.artworkFile != null) {
                        memoryCacheKey("app-banner:${app.packageName}:${app.artworkVersion}")
                        // Hardware thumbnail imports can stall the graphics buffer queue on NVIDIA TVs.
                        allowHardware(!Build.MANUFACTURER.equals("NVIDIA", ignoreCase = true))
                    }
                    app.customBannerRevision?.let { revision ->
                        memoryCacheKey("custom-banner:${app.packageName}:$revision")
                    }
                    placeholder(app.artwork.asImage())
                }
            }
            binding.name.text = app.label
            binding.name.setTextColor(appearance.palette.primaryText)
            binding.name.visibility = if (appearance.showAppNames) View.VISIBLE else View.GONE
            binding.root.contentDescription = app.label
            updateFocusAppearance(binding.root.hasFocus())
        }

        fun recycle() {
            app = null
            binding.artwork.dispose()
            binding.artwork.setImageDrawable(null)
            outlineAnimator?.cancel()
            outlineAnimator = null
            edgeAnimator?.cancel()
            edgeAnimator = null
            binding.root.translationX = 0f
            binding.root.animate().cancel()
        }

        private fun animateEdgeBump(direction: Int) {
            if (edgeAnimator?.isRunning == true) return
            edgeAnimator = ObjectAnimator.ofFloat(
                binding.root,
                View.TRANSLATION_X,
                0f,
                dp(EDGE_BUMP_DISTANCE_DP).toFloat() * direction,
                0f
            ).apply {
                duration = EDGE_BUMP_DURATION_MS
                start()
            }
        }

        private fun updateFocusAppearance(focused: Boolean) {
            outlineAnimator?.cancel()
            outlineAnimator = null
            updateBackground(focused, OUTLINE_FULL_ALPHA)

            val scale = if (focused) FOCUSED_SCALE else 1f
            binding.root.animate().cancel()
            if (appearance.selectorTransitionAnimations) {
                binding.root.animate().scaleX(scale).scaleY(scale).setDuration(SELECTOR_TRANSITION_MS).start()
            } else {
                binding.root.scaleX = scale
                binding.root.scaleY = scale
            }

            if (focused && appearance.showFocusOutline && appearance.appCardFocusAnimations && !moving) {
                outlineAnimator = ValueAnimator.ofInt(OUTLINE_MIN_ALPHA, OUTLINE_FULL_ALPHA).apply {
                    duration = OUTLINE_ANIMATION_MS
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { animator -> updateBackground(focused = true, animator.animatedValue as Int) }
                    start()
                }
            }
        }

        private fun updateBackground(focused: Boolean, outlineAlpha: Int) {
            val palette = appearance.palette
            val fillColor = if (focused || moving) palette.focusedSurface else Color.TRANSPARENT
            val strokeColor: Int
            val strokeWidth: Int
            when {
                moving -> {
                    strokeColor = palette.focus
                    strokeWidth = dp(MOVING_STROKE_WIDTH_DP)
                }

                focused && appearance.showFocusOutline -> {
                    strokeColor = palette.focus.withAlpha(outlineAlpha)
                    strokeWidth = dp(FOCUS_STROKE_WIDTH_DP)
                }

                else -> {
                    strokeColor = Color.TRANSPARENT
                    strokeWidth = 0
                }
            }
            cardBackground.apply {
                cornerRadius = dp(BANNER_CORNER_RADIUS_DP).toFloat()
                setColor(fillColor)
                setStroke(strokeWidth, strokeColor)
            }
        }

        private fun dp(value: Int): Int = (value * binding.root.resources.displayMetrics.density).toInt()

        private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        private companion object {
            const val FOCUSED_SCALE = 1.1f
            const val SELECTOR_TRANSITION_MS = 140L
            const val OUTLINE_ANIMATION_MS = 850L
            const val OUTLINE_MIN_ALPHA = 110
            const val OUTLINE_FULL_ALPHA = 255
            const val EDGE_BUMP_DISTANCE_DP = 14
            const val EDGE_BUMP_DURATION_MS = 180L
            const val FOCUS_STROKE_WIDTH_DP = 3
            const val MOVING_STROKE_WIDTH_DP = 4
            const val BANNER_CORNER_RADIUS_DP = 10
            val MOVE_CONTROL_KEYS = setOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BACK
            )
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
                oldItem.artworkVersion == newItem.artworkVersion &&
                oldItem.customBannerRevision == newItem.customBannerRevision &&
                oldItem.customName == newItem.customName &&
                oldItem.manualOrder == newItem.manualOrder &&
                oldItem.lastUsedAt == newItem.lastUsedAt
    }
}
