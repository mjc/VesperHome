package com.sergioasenjo.vesperhome.settings

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.brightness.BrightnessPeriod
import com.sergioasenjo.vesperhome.brightness.BrightnessSettings
import com.sergioasenjo.vesperhome.databinding.ItemBrightnessPeriodBinding
import com.sergioasenjo.vesperhome.databinding.ViewBrightnessSettingsBinding

sealed interface BrightnessSettingsAction {
    data object ToggleScheduler : BrightnessSettingsAction

    data object ConfigurePermission : BrightnessSettingsAction

    data class SetPeriodBrightness(val period: BrightnessPeriod, val percentage: Int) : BrightnessSettingsAction
}

class BrightnessSettingsPanelBinder(
    private val binding: ViewBrightnessSettingsBinding,
    private val onAction: (BrightnessSettingsAction) -> Unit
) {
    private val periods = listOf(
        PeriodBinding(BrightnessPeriod.MORNING, binding.morningPeriod, R.string.brightness_period_morning),
        PeriodBinding(BrightnessPeriod.DAY, binding.dayPeriod, R.string.brightness_period_day),
        PeriodBinding(BrightnessPeriod.AFTERNOON, binding.afternoonPeriod, R.string.brightness_period_afternoon),
        PeriodBinding(BrightnessPeriod.EVENING, binding.eveningPeriod, R.string.brightness_period_evening),
        PeriodBinding(BrightnessPeriod.NIGHT, binding.nightPeriod, R.string.brightness_period_night)
    )
    val buttons: List<MaterialButton> = listOf(binding.configurePermission, binding.enabled)

    init {
        binding.configurePermission.setOnClickListener { onAction(BrightnessSettingsAction.ConfigurePermission) }
        binding.enabled.setOnClickListener { onAction(BrightnessSettingsAction.ToggleScheduler) }
        periods.forEach { item ->
            item.binding.slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    onAction(BrightnessSettingsAction.SetPeriodBrightness(item.period, value.toInt()))
                }
            }
            item.binding.slider.setLabelFormatter { value ->
                binding.root.context.getString(R.string.brightness_percentage_value, value.toInt())
            }
        }
        periods.zipWithNext().forEach { (current, next) ->
            current.binding.slider.nextFocusDownId = next.binding.slider.id
            next.binding.slider.nextFocusUpId = current.binding.slider.id
        }
    }

    fun render(settings: BrightnessSettings, hasPermission: Boolean, appearance: LauncherAppearance) {
        val context = binding.root.context
        val currentPeriod = BrightnessPeriod.current()
        binding.permissionStatus.setText(
            if (hasPermission) R.string.brightness_permission_granted else R.string.brightness_permission_required
        )
        binding.configurePermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
        binding.enabled.text = context.getString(
            R.string.brightness_scheduler_enabled_value,
            context.getString(if (settings.enabled) R.string.setting_on else R.string.setting_off)
        )
        binding.currentPeriod.text = context.getString(
            R.string.brightness_current_period,
            context.getString(periods.first { it.period == currentPeriod }.labelRes)
        )
        periods.forEach { item ->
            val percentage = settings.percentageFor(item.period)
            item.binding.label.setText(item.labelRes)
            item.binding.value.text = context.getString(R.string.brightness_percentage_value, percentage)
            if (item.binding.slider.value.toInt() != percentage) item.binding.slider.value = percentage.toFloat()
            renderPeriod(item, item.period == currentPeriod, appearance)
        }
        renderColors(appearance)
    }

    fun initialFocus(): View =
        if (binding.configurePermission.visibility == View.VISIBLE) binding.configurePermission else binding.enabled

    private fun renderPeriod(item: PeriodBinding, active: Boolean, appearance: LauncherAppearance) {
        val palette = appearance.palette
        item.binding.root.background = if (active) {
            GradientDrawable().apply {
                setColor((palette.focus and 0x00FFFFFF) or 0x2E000000)
            }
        } else {
            null
        }
        item.binding.label.setTextColor(if (active) palette.focus else palette.primaryText)
        item.binding.value.setTextColor(if (active) palette.focus else palette.primaryText)
    }

    private fun renderColors(appearance: LauncherAppearance) {
        val palette = appearance.palette
        val focusTint = ColorStateList.valueOf(palette.focus)
        val inactiveTint = ColorStateList.valueOf(palette.stroke)
        periods.forEach { item ->
            item.binding.slider.trackActiveTintList = focusTint
            item.binding.slider.trackInactiveTintList = inactiveTint
            item.binding.slider.thumbTintList = focusTint
        }
    }

    private data class PeriodBinding(
        val period: BrightnessPeriod,
        val binding: ItemBrightnessPeriodBinding,
        val labelRes: Int
    )
}
