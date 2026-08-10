package com.sergioasenjo.vesperhome.music

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.GridLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogJellyfinCollectionsBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class JellyfinCollectionDialog(
    private val context: Context,
    onCollectionSelected: (JellyfinMusicCollection) -> Unit,
    onRandomSelected: () -> Unit,
    onRetry: () -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogJellyfinCollectionsBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = JellyfinCollectionAdapter(
        onCollectionClick = { collection ->
            dialog.dismiss()
            onCollectionSelected(collection)
        },
        onRandomClick = {
            dialog.dismiss()
            onRandomSelected()
        }
    )
    private var appearance = LauncherAppearance()
    private var state = JellyfinCollectionPickerState()
    private val dialog = Dialog(context, R.style.Theme_VesperHome_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnShowListener { resizeAndFocus() }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.collections.layoutManager = GridLayoutManager(context, GRID_COLUMNS)
        binding.collections.adapter = adapter
        binding.collections.itemAnimator = null
        binding.retry.setOnClickListener { onRetry() }
    }

    fun show(state: JellyfinCollectionPickerState, appearance: LauncherAppearance) {
        this.appearance = appearance
        render(state)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(state: JellyfinCollectionPickerState) {
        this.state = state
        val showCollections = !state.loading && state.errorRes == null
        binding.collections.visibility = if (showCollections) View.VISIBLE else View.GONE
        binding.status.visibility = if (showCollections) View.GONE else View.VISIBLE
        binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.emptyIcon.visibility = if (state.loading) View.GONE else View.VISIBLE
        binding.retry.visibility = if (state.errorRes != null && !state.loading) View.VISIBLE else View.GONE
        binding.statusMessage.text = context.getString(
            when {
                state.loading -> R.string.jellyfin_collection_loading
                state.errorRes != null -> state.errorRes
                else -> R.string.jellyfin_no_music_collections
            }
        )
        val pickerItems = if (showCollections) {
            listOf(JellyfinPickerItem.Random) + state.collections.map { JellyfinPickerItem.Collection(it) }
        } else {
            emptyList()
        }
        adapter.submitList(pickerItems) {
            if (dialog.isShowing && showCollections && binding.root.findFocus() == null) requestFirstCollectionFocus()
        }
        applyAppearance()
    }

    fun setAppearance(appearance: LauncherAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        applyAppearance()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun resizeAndFocus() {
        val metrics = context.resources.displayMetrics
        dialog.window?.setLayout(
            minOf(dp(DIALOG_WIDTH_DP), metrics.widthPixels - dp(SCREEN_MARGIN_DP)),
            minOf(dp(DIALOG_HEIGHT_DP), metrics.heightPixels - dp(SCREEN_MARGIN_DP))
        )
        binding.root.post {
            if (!state.loading && state.errorRes == null) {
                requestFirstCollectionFocus()
            } else if (state.errorRes != null) {
                binding.retry.requestFocus()
            }
        }
    }

    private fun requestFirstCollectionFocus() {
        binding.collections.scrollToPosition(0)
        binding.collections.post {
            binding.collections.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    private fun applyAppearance() {
        val palette = appearance.palette
        binding.root.setBackgroundColor(palette.panel)
        binding.eyebrow.setTextColor(palette.focus)
        binding.title.setTextColor(palette.primaryText)
        binding.description.setTextColor(palette.secondaryText)
        binding.statusMessage.setTextColor(palette.secondaryText)
        binding.hint.setTextColor(palette.secondaryText)
        binding.emptyIcon.imageTintList = ColorStateList.valueOf(palette.secondaryText)
        binding.loading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
        binding.retry.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        binding.retry.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
        )
        adapter.setAppearance(appearance)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val GRID_COLUMNS = 3
        const val DIALOG_WIDTH_DP = 820
        const val DIALOG_HEIGHT_DP = 570
        const val SCREEN_MARGIN_DP = 48
        const val BACKGROUND_DIM_AMOUNT = 0.72f
    }
}
