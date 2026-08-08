package com.sergioasenjo.ltvlauncher.about

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.DialogAboutBinding
import com.sergioasenjo.ltvlauncher.settings.LauncherAppearance

class AboutPanel(
    context: Context,
    onOpenSource: () -> Unit,
    onOpenAdbGuide: () -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogAboutBinding.inflate(android.view.LayoutInflater.from(context))
    private val dialog = Dialog(context, R.style.Theme_LtvLauncher_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnShowListener {
            binding.sourceRepository.post { binding.sourceRepository.requestFocus() }
        }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.sourceRepository.setOnClickListener { onOpenSource() }
        binding.adbGuide.setOnClickListener { onOpenAdbGuide() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(state: DiagnosticsState, appearance: LauncherAppearance) {
        render(state, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(state: DiagnosticsState, appearance: LauncherAppearance) {
        val context = binding.root.context
        binding.version.text = context.getString(R.string.about_version, state.versionName, state.versionCode)
        binding.packageName.text = context.getString(R.string.about_package, state.packageName)
        binding.device.text = context.getString(R.string.about_device, state.deviceName)
        binding.androidVersion.text = context.getString(
            R.string.about_android_version,
            state.androidVersion,
            state.apiLevel
        )
        renderStatus(binding.defaultHome, R.string.diagnostic_default_home, state.defaultHome, appearance)
        renderStatus(binding.homeButtonFix, R.string.diagnostic_home_button_fix, state.homeButtonFixEnabled, appearance)
        renderStatus(
            binding.notificationAccess,
            R.string.diagnostic_notification_access,
            state.notificationAccess,
            appearance
        )
        renderStatus(binding.overlayAccess, R.string.diagnostic_overlay_access, state.overlayAccess, appearance)
        renderStatus(
            binding.brightnessAccess,
            R.string.diagnostic_brightness_access,
            state.brightnessAccess,
            appearance
        )
        applyAppearance(appearance)
    }

    fun release() {
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    private fun renderStatus(
        view: android.widget.TextView,
        labelRes: Int,
        granted: Boolean,
        appearance: LauncherAppearance
    ) {
        val context = binding.root.context
        view.text = context.getString(
            R.string.diagnostic_status_value,
            context.getString(labelRes),
            context.getString(if (granted) R.string.diagnostic_available else R.string.diagnostic_unavailable)
        )
        view.setTextColor(if (granted) appearance.palette.focus else appearance.palette.secondaryText)
    }

    private fun applyAppearance(appearance: LauncherAppearance) {
        val palette = appearance.palette
        binding.root.setBackgroundColor(palette.panel)
        binding.title.setTextColor(palette.primaryText)
        binding.description.setTextColor(palette.secondaryText)
        binding.version.setTextColor(palette.primaryText)
        binding.packageName.setTextColor(palette.secondaryText)
        binding.device.setTextColor(palette.secondaryText)
        binding.androidVersion.setTextColor(palette.secondaryText)
        binding.permissionsTitle.setTextColor(palette.focus)
        listOf(binding.sourceRepository, binding.adbGuide).forEach { button -> renderButton(button, appearance) }
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
