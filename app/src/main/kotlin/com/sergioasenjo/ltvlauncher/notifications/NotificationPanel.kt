package com.sergioasenjo.ltvlauncher.notifications

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
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.DialogNotificationsBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

class NotificationPanel(
    context: Context,
    onDismissNotification: (LauncherNotification) -> Unit,
    onDismissAll: () -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogNotificationsBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = NotificationAdapter(onDismissNotification)
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
        binding.root.background = GradientDrawable().apply {
            cornerRadii =
                floatArrayOf(
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
        binding.clearAll.strokeColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focus, Color.TRANSPARENT)
        )
        binding.clearAll.strokeWidth = dp(binding.root.context, 2)
    }

    private companion object {
        const val PANEL_WIDTH_DP = 520
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
