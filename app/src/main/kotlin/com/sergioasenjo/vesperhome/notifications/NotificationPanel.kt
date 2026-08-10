package com.sergioasenjo.vesperhome.notifications

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.DialogNotificationsBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance

class NotificationPanel(
    context: Context,
    onDismissNotification: (LauncherNotification) -> Unit,
    onDismissAll: () -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogNotificationsBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = NotificationAdapter(onDismissNotification)
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
            binding.root.post {
                if (binding.clearAll.visibility == View.VISIBLE) {
                    binding.clearAll.requestFocus()
                } else {
                    binding.notifications.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                }
            }
        }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.notifications.layoutManager = LinearLayoutManager(context)
        binding.notifications.adapter = adapter
        binding.notifications.itemAnimator = null
        binding.clearAll.setOnClickListener { onDismissAll() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(state: NotificationState, appearance: LauncherAppearance) {
        render(state, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(state: NotificationState, appearance: LauncherAppearance) {
        adapter.setAppearance(appearance)
        binding.emptyMessage.visibility = if (state.notifications.isEmpty()) View.VISIBLE else View.GONE
        binding.notifications.visibility = if (state.notifications.isEmpty()) View.GONE else View.VISIBLE
        binding.clearAll.visibility =
            if (state.notifications.any(LauncherNotification::clearable)) View.VISIBLE else View.GONE
        applyAppearance(appearance)
        adapter.submitList(state.notifications) {
            if (dialog.isShowing && state.notifications.isNotEmpty() && binding.root.findFocus() == null) {
                binding.notifications.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun applyAppearance(appearance: LauncherAppearance) {
        val palette = appearance.palette
        binding.root.setBackgroundColor(palette.panel)
        binding.title.setTextColor(palette.primaryText)
        binding.emptyMessage.setTextColor(palette.secondaryText)
        binding.clearAll.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, palette.surface)
        )
        binding.clearAll.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(palette.focusedText, palette.primaryText)
            )
        )
    }

    private companion object {
        const val PANEL_WIDTH_DP = 440
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
