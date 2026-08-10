package com.sergioasenjo.vesperhome.inputs

import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import kotlinx.coroutines.launch

class TvInputController(
    private val activity: AppCompatActivity,
    private val binding: ActivityLauncherBinding,
    private val repository: TvInputRepository
) {
    private var inputs: List<TvInput> = emptyList()
    private var enabled = false

    init {
        binding.statusInputs.setOnClickListener { showInputSelector() }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeInputs().collect { inputs ->
                    this@TvInputController.inputs = inputs
                    updateVisibility()
                }
            }
        }
    }

    fun render(enabled: Boolean, appearance: LauncherAppearance) {
        this.enabled = enabled
        updateVisibility()
        val palette = appearance.palette
        binding.statusInputs.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val iconColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        binding.statusInputs.setTextColor(iconColors)
        binding.statusInputs.iconTint = iconColors
    }

    private fun updateVisibility() {
        binding.statusInputs.visibility = if (enabled && inputs.isNotEmpty()) View.VISIBLE else View.GONE
        binding.statusInputs.contentDescription = activity.resources.getQuantityString(
            R.plurals.tv_input_count,
            inputs.size,
            inputs.size
        )
    }

    private fun showInputSelector() {
        if (inputs.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.input_sources)
            .setItems(inputs.map(::inputLabel).toTypedArray()) { dialog, index ->
                dialog.dismiss()
                switchTo(inputs[index])
            }
            .show()
    }

    private fun switchTo(input: TvInput) {
        try {
            activity.startActivity(repository.createSwitchIntent(input))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.tv_input_open_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.tv_input_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun inputLabel(input: TvInput): String {
        val type = activity.getString(
            when (input.type) {
                TvInputType.HDMI -> R.string.input_type_hdmi
                TvInputType.AV -> R.string.input_type_av
                TvInputType.TUNER -> R.string.input_type_tuner
                TvInputType.OTHER -> R.string.input_type_other
            }
        )
        return if (input.connected) {
            activity.getString(R.string.tv_input_label, input.label, type)
        } else {
            activity.getString(R.string.tv_input_label_disconnected, input.label, type)
        }
    }
}
