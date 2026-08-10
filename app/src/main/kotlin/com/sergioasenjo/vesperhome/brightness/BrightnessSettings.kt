package com.sergioasenjo.vesperhome.brightness

import java.util.Calendar

enum class BrightnessPeriod(val defaultPercentage: Int) {
    MORNING(60),
    DAY(100),
    AFTERNOON(80),
    EVENING(50),
    NIGHT(20);

    companion object {
        fun current(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): BrightnessPeriod = when (hour) {
            in 6..<9 -> MORNING
            in 9..<15 -> DAY
            in 15..<18 -> AFTERNOON
            in 18..<22 -> EVENING
            else -> NIGHT
        }
    }
}

data class BrightnessSettings(
    val enabled: Boolean = false,
    val morningPercentage: Int = BrightnessPeriod.MORNING.defaultPercentage,
    val dayPercentage: Int = BrightnessPeriod.DAY.defaultPercentage,
    val afternoonPercentage: Int = BrightnessPeriod.AFTERNOON.defaultPercentage,
    val eveningPercentage: Int = BrightnessPeriod.EVENING.defaultPercentage,
    val nightPercentage: Int = BrightnessPeriod.NIGHT.defaultPercentage
) {
    fun percentageFor(period: BrightnessPeriod): Int = when (period) {
        BrightnessPeriod.MORNING -> morningPercentage
        BrightnessPeriod.DAY -> dayPercentage
        BrightnessPeriod.AFTERNOON -> afternoonPercentage
        BrightnessPeriod.EVENING -> eveningPercentage
        BrightnessPeriod.NIGHT -> nightPercentage
    }
}
