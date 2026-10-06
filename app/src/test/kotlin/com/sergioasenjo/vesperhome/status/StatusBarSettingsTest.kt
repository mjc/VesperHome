package com.sergioasenjo.vesperhome.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarSettingsTest {
    @Test
    fun hiddenClockFieldsDoNotScheduleUpdates() {
        assertNull(StatusBarSettings(showDate = false, showTime = false).clockUpdateIntervalMillis())
        assertEquals(60_000L, StatusBarSettings(showDate = false, dateFormat = "ss").clockUpdateIntervalMillis())
        assertEquals(60_000L, StatusBarSettings(showTime = false, timeFormat = "ss").clockUpdateIntervalMillis())
    }

    @Test
    fun onlyVisibleUnquotedSecondsRequireSecondUpdates() {
        assertEquals(1_000L, StatusBarSettings(timeFormat = "HH:mm:ss").clockUpdateIntervalMillis())
        assertEquals(1_000L, StatusBarSettings(showDate = true, dateFormat = "ss").clockUpdateIntervalMillis())
        assertEquals(60_000L, StatusBarSettings(timeFormat = "HH:mm 'seconds'").clockUpdateIntervalMillis())
        assertEquals(60_000L, StatusBarSettings(timeFormat = "HH:mm 'it''s'").clockUpdateIntervalMillis())
    }
}
