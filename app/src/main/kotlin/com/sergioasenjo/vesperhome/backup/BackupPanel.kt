package com.sergioasenjo.vesperhome.backup

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogBackupsBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class BackupPanel(
    context: Context,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onFrequencySelected: () -> Unit,
    onSelected: (BackupFileEntry) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogBackupsBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = BackupAdapter(onSelected)
    private val dialog = Dialog(context, R.style.Theme_VesperHome_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnShowListener {
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
        binding.safetyBackupFrequency.setOnClickListener { onFrequencySelected() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(
        entries: List<BackupFileEntry>,
        frequency: SafetyBackupFrequency,
        busy: Boolean,
        appearance: LauncherAppearance
    ) {
        render(entries, frequency, busy, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(
        entries: List<BackupFileEntry>,
        frequency: SafetyBackupFrequency,
        busy: Boolean,
        appearance: LauncherAppearance
    ) {
        adapter.setAppearance(appearance)
        adapter.submitList(entries)
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.emptyMessage.visibility = if (!busy && entries.isEmpty()) View.VISIBLE else View.GONE
        binding.backups.visibility = if (!busy && entries.isNotEmpty()) View.VISIBLE else View.GONE
        binding.createBackup.isEnabled = !busy
        binding.importBackup.isEnabled = !busy
        binding.safetyBackupFrequency.isEnabled = !busy
        binding.safetyBackupFrequency.text = binding.root.context.getString(
            R.string.safety_backup_frequency_value,
            binding.root.context.getString(frequency.labelRes)
        )
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
        binding.emptyMessage.setTextColor(palette.secondaryText)
        listOf(binding.createBackup, binding.importBackup, binding.safetyBackupFrequency).forEach { button ->
            renderButton(button, appearance)
        }
    }

    private fun renderButton(button: MaterialButton, appearance: LauncherAppearance) {
        val palette = appearance.palette
        button.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        button.iconTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        button.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
        )
    }

    private companion object {
        const val PANEL_WIDTH_DP = 470
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
