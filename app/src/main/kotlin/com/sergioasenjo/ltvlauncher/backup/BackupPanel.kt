package com.sergioasenjo.ltvlauncher.backup

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.DialogBackupsBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

class BackupPanel(
    context: Context,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onSelected: (BackupFileEntry) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogBackupsBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = BackupAdapter(onSelected)
    private val dialog = Dialog(context, R.style.Theme_LtvLauncher_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        setOnShowListener {
            window?.apply {
                setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.START)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
            }
            binding.createBackup.post { binding.createBackup.requestFocus() }
        }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.backups.layoutManager = LinearLayoutManager(context)
        binding.backups.adapter = adapter
        binding.backups.itemAnimator = null
        binding.createBackup.setOnClickListener { onCreate() }
        binding.importBackup.setOnClickListener { onImport() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(entries: List<BackupFileEntry>, busy: Boolean, appearance: LauncherAppearance) {
        render(entries, busy, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(entries: List<BackupFileEntry>, busy: Boolean, appearance: LauncherAppearance) {
        adapter.setAppearance(appearance)
        adapter.submitList(entries)
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.emptyMessage.visibility = if (!busy && entries.isEmpty()) View.VISIBLE else View.GONE
        binding.backups.visibility = if (!busy && entries.isNotEmpty()) View.VISIBLE else View.GONE
        binding.createBackup.isEnabled = !busy
        binding.importBackup.isEnabled = !busy
        applyAppearance(appearance)
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun applyAppearance(appearance: LauncherAppearance) {
        val palette = appearance.palette
        binding.root.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                0f,
                0f,
                dp(binding.root.context, 24).toFloat(),
                dp(binding.root.context, 24).toFloat(),
                dp(binding.root.context, 24).toFloat(),
                dp(binding.root.context, 24).toFloat(),
                0f,
                0f
            )
            setColor(palette.panel)
            setStroke(dp(binding.root.context, 1), palette.stroke)
        }
        binding.title.setTextColor(palette.primaryText)
        binding.description.setTextColor(palette.secondaryText)
        binding.emptyMessage.setTextColor(palette.secondaryText)
        listOf(binding.createBackup, binding.importBackup).forEach { button -> renderButton(button, appearance) }
    }

    private fun renderButton(button: MaterialButton, appearance: LauncherAppearance) {
        val palette = appearance.palette
        button.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        button.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
        )
        button.strokeColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focus, Color.TRANSPARENT)
        )
        button.strokeWidth = dp(binding.root.context, 2)
    }

    private companion object {
        const val PANEL_WIDTH_DP = 560
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
