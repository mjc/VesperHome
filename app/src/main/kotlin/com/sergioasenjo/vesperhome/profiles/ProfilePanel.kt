package com.sergioasenjo.vesperhome.profiles

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
import com.sergioasenjo.vesperhome.databinding.DialogProfilesBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherSettings

internal class ProfilePanel(
    context: Context,
    onCreate: () -> Unit,
    onToggleComingNext: () -> Unit,
    onToggleMusic: () -> Unit,
    onSelected: (LayoutProfile) -> Unit,
    private val onDismissed: () -> Unit
) {
    private val binding = DialogProfilesBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = ProfileAdapter(onSelected)
    private val dialog = Dialog(context, R.style.Theme_VesperHome_SettingsPanel).apply {
        setContentView(binding.root)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setLayout(dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.START)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = BACKGROUND_DIM_AMOUNT }
        }
        setOnShowListener { binding.createProfile.post { binding.createProfile.requestFocus() } }
        setOnDismissListener { onDismissed() }
    }

    init {
        binding.profiles.layoutManager = LinearLayoutManager(context)
        binding.profiles.adapter = adapter
        binding.profiles.itemAnimator = null
        binding.createProfile.setOnClickListener { onCreate() }
        binding.showComingNext.setOnClickListener { onToggleComingNext() }
        binding.showMusic.setOnClickListener { onToggleMusic() }
    }

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show(state: ProfileState, settings: LauncherSettings, busy: Boolean, appearance: LauncherAppearance) {
        render(state, settings, busy, appearance)
        if (!dialog.isShowing) dialog.show()
    }

    fun render(state: ProfileState, settings: LauncherSettings, busy: Boolean, appearance: LauncherAppearance) {
        adapter.render(state.activeProfileId, appearance)
        adapter.submitList(state.profiles)
        binding.progress.visibility = if (busy || state.loading) View.VISIBLE else View.GONE
        binding.profiles.visibility = if (busy || state.loading) View.INVISIBLE else View.VISIBLE
        binding.createProfile.isEnabled = !busy
        binding.showComingNext.isEnabled = !busy
        binding.showMusic.isEnabled = !busy
        val context = binding.root.context
        binding.showComingNext.text = context.getString(
            R.string.profile_show_coming_next_value,
            context.getString(if (settings.showComingNext) R.string.setting_on else R.string.setting_off)
        )
        binding.showMusic.text = context.getString(
            R.string.profile_show_jellyfin_music_value,
            context.getString(if (settings.showMusic) R.string.setting_on else R.string.setting_off)
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
        listOf(binding.createProfile, binding.showComingNext, binding.showMusic).forEach { button ->
            renderButton(button, appearance)
        }
    }

    private fun renderButton(button: MaterialButton, appearance: LauncherAppearance) {
        val palette = appearance.palette
        val states = arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf())
        button.backgroundTintList = ColorStateList(states, intArrayOf(palette.focusedSurface, palette.surface))
        button.setTextColor(ColorStateList(states, intArrayOf(palette.focusedText, palette.primaryText)))
        button.iconTint = ColorStateList(states, intArrayOf(palette.focusedText, palette.primaryText))
    }

    private companion object {
        const val PANEL_WIDTH_DP = 470
        const val BACKGROUND_DIM_AMOUNT = 0.62f

        fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    }
}
