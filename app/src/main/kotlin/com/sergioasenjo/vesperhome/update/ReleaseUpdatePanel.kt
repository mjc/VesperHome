package com.sergioasenjo.vesperhome.update

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogReleaseUpdateBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import java.util.Date

internal class ReleaseUpdatePanel(context: Context, onRetry: () -> Unit, private val onDismissed: () -> Unit) {
    private val binding = DialogReleaseUpdateBinding.inflate(android.view.LayoutInflater.from(context))
    private val dialog = Dialog(context, R.style.Theme_VesperHome_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnShowListener { binding.checkAgain.post { binding.checkAgain.requestFocus() } }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.checkAgain.setOnClickListener { onRetry() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(update: ReleaseUpdate?, loading: Boolean, failed: Boolean, appearance: LauncherAppearance) {
        render(update, loading, failed, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(update: ReleaseUpdate?, loading: Boolean, failed: Boolean, appearance: LauncherAppearance) {
        val context = binding.root.context
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.error.visibility = if (!loading && failed) View.VISIBLE else View.GONE
        binding.releaseContent.visibility = if (!loading && update != null) View.VISIBLE else View.GONE
        binding.checkAgain.isEnabled = !loading
        update?.let { release ->
            binding.status.setText(
                if (release.updateAvailable) R.string.release_update_available else R.string.release_up_to_date
            )
            binding.releaseName.text = release.title
            binding.version.text = context.getString(
                R.string.release_versions,
                release.currentVersion,
                release.latestVersion
            )
            binding.releaseDate.text = context.getString(
                R.string.release_date,
                DateFormat.getMediumDateFormat(context).format(Date(release.publishedAt))
            )
            binding.releaseNotes.text = release.notes.ifBlank { context.getString(R.string.release_no_notes) }
        }
        applyAppearance(appearance)
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun applyAppearance(appearance: LauncherAppearance) {
        val palette = appearance.palette
        binding.root.setBackgroundColor(palette.panel)
        binding.title.setTextColor(palette.primaryText)
        binding.description.setTextColor(palette.secondaryText)
        binding.status.setTextColor(palette.focus)
        binding.releaseName.setTextColor(palette.primaryText)
        binding.version.setTextColor(palette.secondaryText)
        binding.releaseDate.setTextColor(palette.secondaryText)
        binding.releaseNotes.setTextColor(palette.primaryText)
        binding.error.setTextColor(palette.secondaryText)
        val states = arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf())
        binding.checkAgain.backgroundTintList =
            ColorStateList(states, intArrayOf(palette.focusedSurface, palette.surface))
        binding.checkAgain.setTextColor(ColorStateList(states, intArrayOf(palette.focusedText, palette.primaryText)))
    }

    private companion object {
        const val PANEL_WIDTH_DP = 520
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
