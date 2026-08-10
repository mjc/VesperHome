package com.sergioasenjo.vesperhome.screensaver

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import com.sergioasenjo.vesperhome.databinding.ViewClockBinding
import com.sergioasenjo.vesperhome.status.StatusBarSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class ClockViewController(private val binding: ViewClockBinding) {
    private val handler = Handler(Looper.getMainLooper())
    private var screensaverSettings = ScreensaverSettings()
    private var dateTimeSettings = StatusBarSettings()
    private val updateClock = object : Runnable {
        override fun run() {
            updateClockText()
            scheduleClockUpdate()
        }
    }
    private val moveClock = object : Runnable {
        override fun run() {
            moveToNewPosition()
            handler.postDelayed(this, CLOCK_MOVE_INTERVAL_MS)
        }
    }

    init {
        handler.postDelayed(moveClock, CLOCK_MOVE_INTERVAL_MS)
    }

    fun render(screensaver: ScreensaverSettings, dateTime: StatusBarSettings) {
        screensaverSettings = screensaver
        dateTimeSettings = dateTime
        applyStyle()
        updateClockText()
        scheduleClockUpdate()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        binding.clockContainer.animate().cancel()
    }

    private fun applyStyle() {
        when (screensaverSettings.clockStyle) {
            ScreensaverClockStyle.MINIMAL -> {
                binding.time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 120f)
                binding.time.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                binding.time.letterSpacing = 0.04f
                binding.date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                binding.date.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }

            ScreensaverClockStyle.BOLD -> {
                binding.time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 140f)
                binding.time.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                binding.time.letterSpacing = -0.02f
                binding.date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
                binding.date.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        }
    }

    private fun updateClockText() {
        val now = Date()
        binding.time.text = format(now, dateTimeSettings.timeFormat, DEFAULT_TIME_FORMAT)
        binding.date.text = format(now, dateTimeSettings.dateFormat, DEFAULT_DATE_FORMAT)
    }

    private fun moveToNewPosition() {
        val availableX = ((binding.root.width - binding.clockContainer.width) * SAFE_MOVEMENT_FRACTION / 2f)
            .coerceAtLeast(0f)
        val availableY = ((binding.root.height - binding.clockContainer.height) * SAFE_MOVEMENT_FRACTION / 2f)
            .coerceAtLeast(0f)
        val targetX = Random.nextFloat() * availableX * 2f - availableX
        val targetY = Random.nextFloat() * availableY * 2f - availableY
        binding.clockContainer.animate()
            .translationX(targetX)
            .translationY(targetY)
            .setDuration(CLOCK_MOVE_DURATION_MS)
            .start()
    }

    private fun scheduleClockUpdate() {
        handler.removeCallbacks(updateClock)
        val interval = if (dateTimeSettings.timeFormat.contains('s')) SECOND_MS else MINUTE_MS
        handler.postDelayed(updateClock, interval - System.currentTimeMillis() % interval)
    }

    private fun format(date: Date, pattern: String, fallback: String): String = try {
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    } catch (_: IllegalArgumentException) {
        SimpleDateFormat(fallback, Locale.getDefault()).format(date)
    }

    private companion object {
        const val CLOCK_MOVE_INTERVAL_MS = 30_000L
        const val CLOCK_MOVE_DURATION_MS = 2_000L
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60_000L
        const val SAFE_MOVEMENT_FRACTION = 0.7f
        const val DEFAULT_TIME_FORMAT = "HH:mm"
        const val DEFAULT_DATE_FORMAT = "EEE, MMM d"
    }
}
