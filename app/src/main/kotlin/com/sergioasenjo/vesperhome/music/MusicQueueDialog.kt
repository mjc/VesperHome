package com.sergioasenjo.vesperhome.music

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogMusicQueueBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

internal class MusicQueueDialog(
    private val context: Context,
    onTrackSelected: (MusicTrack) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogMusicQueueBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = MusicQueueAdapter(onTrackSelected)
    private var currentTrackId: String? = null
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
        binding.tracks.layoutManager = LinearLayoutManager(context)
        binding.tracks.adapter = adapter
        binding.tracks.itemAnimator = null
    }

    fun show(state: MusicUiState, appearance: LauncherAppearance) {
        render(state, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(state: MusicUiState, appearance: LauncherAppearance) {
        val collection = state.activeCollection ?: return
        currentTrackId = state.track?.id
        binding.eyebrow.text = context.getString(
            if (collection.type == MusicCollectionType.PLAYLIST) R.string.playlist else R.string.album
        )
        binding.title.text = collection.name
        binding.description.text = context.resources.getQuantityString(
            R.plurals.music_collection_track_count,
            state.queue.size,
            state.queue.size
        )
        val palette = appearance.palette
        binding.root.setBackgroundColor(palette.panel)
        binding.eyebrow.setTextColor(palette.focus)
        binding.title.setTextColor(palette.primaryText)
        binding.description.setTextColor(palette.secondaryText)
        binding.hint.setTextColor(palette.secondaryText)
        adapter.setAppearance(appearance)
        adapter.submitList(
            state.queue.map { track ->
                MusicQueueTrackUi(track, track.id == state.track?.id, state.playing && track.id == state.track?.id)
            }
        )
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    private fun resizeAndFocus() {
        val metrics = context.resources.displayMetrics
        dialog.window?.setLayout(
            minOf(dp(DIALOG_WIDTH_DP), metrics.widthPixels - dp(SCREEN_MARGIN_DP)),
            minOf(dp(DIALOG_HEIGHT_DP), metrics.heightPixels - dp(SCREEN_MARGIN_DP))
        )
        val position = adapter.currentList.indexOfFirst { it.track.id == currentTrackId }.coerceAtLeast(0)
        binding.tracks.scrollToPosition(position)
        binding.tracks.post {
            binding.tracks.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DIALOG_WIDTH_DP = 760
        const val DIALOG_HEIGHT_DP = 570
        const val SCREEN_MARGIN_DP = 48
        const val BACKGROUND_DIM_AMOUNT = 0.72f
    }
}
