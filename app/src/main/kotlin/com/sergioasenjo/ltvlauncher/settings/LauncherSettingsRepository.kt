package com.sergioasenjo.ltvlauncher.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.brightness.BrightnessPeriod
import com.sergioasenjo.ltvlauncher.brightness.BrightnessSettings
import com.sergioasenjo.ltvlauncher.screensaver.BackButtonAction
import com.sergioasenjo.ltvlauncher.screensaver.ScreensaverClockStyle
import com.sergioasenjo.ltvlauncher.screensaver.ScreensaverSettings
import com.sergioasenjo.ltvlauncher.status.StatusBarSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherSettingsDataStore by preferencesDataStore(name = "launcher_settings")

enum class LauncherTheme {
    DARK,
    LIGHT
}

data class LauncherAppearance(
    val theme: LauncherTheme = LauncherTheme.DARK,
    val showAppNames: Boolean = true,
    val showCategoryTitles: Boolean = true,
    val showFocusOutline: Boolean = true,
    val appCardFocusAnimations: Boolean = true,
    val selectorTransitionAnimations: Boolean = true,
    val keyClickSounds: Boolean = true
) {
    val palette: LauncherPalette
        get() = when (theme) {
            LauncherTheme.DARK -> LauncherPalette.DARK
            LauncherTheme.LIGHT -> LauncherPalette.LIGHT
        }
}

data class LauncherPalette(
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val surface: Int,
    val focusedSurface: Int,
    val focusedText: Int,
    val focus: Int,
    val stroke: Int,
    val panel: Int
) {
    companion object {
        val DARK = LauncherPalette(
            backgroundStart = 0xFF18212B.toInt(),
            backgroundEnd = 0xFF080A0E.toInt(),
            primaryText = 0xFFF7F9FC.toInt(),
            secondaryText = 0xFFAEB8C4.toInt(),
            surface = 0xFF263340.toInt(),
            focusedSurface = 0xFFF7F9FC.toInt(),
            focusedText = 0xFF111820.toInt(),
            focus = 0xFF8EC5FF.toInt(),
            stroke = 0x55FFFFFF,
            panel = 0xFF1E252D.toInt()
        )
        val LIGHT = LauncherPalette(
            backgroundStart = 0xFFF7FAFC.toInt(),
            backgroundEnd = 0xFFDCE7EF.toInt(),
            primaryText = 0xFF17212B.toInt(),
            secondaryText = 0xFF5D6975.toInt(),
            surface = 0xFFE5ECF2.toInt(),
            focusedSurface = 0xFF17212B.toInt(),
            focusedText = 0xFFF7F9FC.toInt(),
            focus = 0xFF2563A8.toInt(),
            stroke = 0x4017212B,
            panel = 0xFFF7FAFC.toInt()
        )
    }
}

data class LauncherSettings(
    val applicationSortMode: ApplicationSortMode = ApplicationSortMode.MANUAL,
    val appearance: LauncherAppearance = LauncherAppearance(),
    val statusBar: StatusBarSettings = StatusBarSettings(),
    val screensaver: ScreensaverSettings = ScreensaverSettings(),
    val brightness: BrightnessSettings = BrightnessSettings()
)

class LauncherSettingsRepository(private val context: Context) {
    val settings: Flow<LauncherSettings> = context.launcherSettingsDataStore.data.map { preferences ->
        LauncherSettings(
            applicationSortMode = preferences.enumValue(APPLICATION_SORT_MODE, ApplicationSortMode.MANUAL),
            appearance = LauncherAppearance(
                theme = preferences.enumValue(THEME, LauncherTheme.DARK),
                showAppNames = preferences[SHOW_APP_NAMES] ?: true,
                showCategoryTitles = preferences[SHOW_CATEGORY_TITLES] ?: true,
                showFocusOutline = preferences[SHOW_FOCUS_OUTLINE] ?: true,
                appCardFocusAnimations = preferences[APP_CARD_FOCUS_ANIMATIONS] ?: true,
                selectorTransitionAnimations = preferences[SELECTOR_TRANSITION_ANIMATIONS] ?: true,
                keyClickSounds = preferences[KEY_CLICK_SOUNDS] ?: true
            ),
            statusBar = StatusBarSettings(
                autoHide = preferences[STATUS_AUTO_HIDE] ?: false,
                showDate = preferences[STATUS_SHOW_DATE] ?: true,
                showTime = preferences[STATUS_SHOW_TIME] ?: true,
                showNetwork = preferences[STATUS_SHOW_NETWORK] ?: true,
                showInputs = preferences[STATUS_SHOW_INPUTS] ?: true,
                showNotifications = preferences[STATUS_SHOW_NOTIFICATIONS] ?: true,
                autoHideNotificationBell = preferences[STATUS_AUTO_HIDE_NOTIFICATION_BELL] ?: true,
                systemNotificationPopups = preferences[SYSTEM_NOTIFICATION_POPUPS] ?: false,
                dateFormat = preferences[STATUS_DATE_FORMAT] ?: DEFAULT_DATE_FORMAT,
                timeFormat = preferences[STATUS_TIME_FORMAT] ?: defaultTimeFormat()
            ),
            screensaver = ScreensaverSettings(
                clockStyle = preferences.enumValue(SCREENSAVER_CLOCK_STYLE, ScreensaverClockStyle.MINIMAL),
                backButtonAction = preferences.enumValue(BACK_BUTTON_ACTION, BackButtonAction.NOTHING)
            ),
            brightness = BrightnessSettings(
                enabled = preferences[BRIGHTNESS_ENABLED] ?: false,
                morningPercentage = preferences[BRIGHTNESS_MORNING] ?: BrightnessPeriod.MORNING.defaultPercentage,
                dayPercentage = preferences[BRIGHTNESS_DAY] ?: BrightnessPeriod.DAY.defaultPercentage,
                afternoonPercentage =
                    preferences[BRIGHTNESS_AFTERNOON] ?: BrightnessPeriod.AFTERNOON.defaultPercentage,
                eveningPercentage = preferences[BRIGHTNESS_EVENING] ?: BrightnessPeriod.EVENING.defaultPercentage,
                nightPercentage = preferences[BRIGHTNESS_NIGHT] ?: BrightnessPeriod.NIGHT.defaultPercentage
            )
        )
    }
    val applicationSortMode: Flow<ApplicationSortMode> = settings.map { it.applicationSortMode }
    val appearance: Flow<LauncherAppearance> = settings.map { it.appearance }

    suspend fun setApplicationSortMode(sortMode: ApplicationSortMode) {
        setEnum(APPLICATION_SORT_MODE, sortMode)
    }

    suspend fun setTheme(theme: LauncherTheme) {
        setEnum(THEME, theme)
    }

    suspend fun setShowAppNames(show: Boolean) {
        setBoolean(SHOW_APP_NAMES, show)
    }

    suspend fun setShowCategoryTitles(show: Boolean) {
        setBoolean(SHOW_CATEGORY_TITLES, show)
    }

    suspend fun setShowFocusOutline(show: Boolean) {
        setBoolean(SHOW_FOCUS_OUTLINE, show)
    }

    suspend fun setAppCardFocusAnimations(enabled: Boolean) {
        setBoolean(APP_CARD_FOCUS_ANIMATIONS, enabled)
    }

    suspend fun setSelectorTransitionAnimations(enabled: Boolean) {
        setBoolean(SELECTOR_TRANSITION_ANIMATIONS, enabled)
    }

    suspend fun setKeyClickSounds(enabled: Boolean) {
        setBoolean(KEY_CLICK_SOUNDS, enabled)
    }

    suspend fun setStatusBarAutoHide(enabled: Boolean) {
        setBoolean(STATUS_AUTO_HIDE, enabled)
    }

    suspend fun setStatusBarShowDate(show: Boolean) {
        setBoolean(STATUS_SHOW_DATE, show)
    }

    suspend fun setStatusBarShowTime(show: Boolean) {
        setBoolean(STATUS_SHOW_TIME, show)
    }

    suspend fun setStatusBarShowNetwork(show: Boolean) {
        setBoolean(STATUS_SHOW_NETWORK, show)
    }

    suspend fun setStatusBarShowInputs(show: Boolean) {
        setBoolean(STATUS_SHOW_INPUTS, show)
    }

    suspend fun setStatusBarShowNotifications(show: Boolean) {
        setBoolean(STATUS_SHOW_NOTIFICATIONS, show)
    }

    suspend fun setStatusBarAutoHideNotificationBell(enabled: Boolean) {
        setBoolean(STATUS_AUTO_HIDE_NOTIFICATION_BELL, enabled)
    }

    suspend fun setSystemNotificationPopups(enabled: Boolean) {
        setBoolean(SYSTEM_NOTIFICATION_POPUPS, enabled)
    }

    suspend fun setStatusBarDateFormat(format: String) {
        setString(STATUS_DATE_FORMAT, format)
    }

    suspend fun setStatusBarTimeFormat(format: String) {
        setString(STATUS_TIME_FORMAT, format)
    }

    suspend fun setScreensaverClockStyle(style: ScreensaverClockStyle) {
        setEnum(SCREENSAVER_CLOCK_STYLE, style)
    }

    suspend fun setBackButtonAction(action: BackButtonAction) {
        setEnum(BACK_BUTTON_ACTION, action)
    }

    suspend fun setBrightnessEnabled(enabled: Boolean) {
        setBoolean(BRIGHTNESS_ENABLED, enabled)
    }

    suspend fun setBrightness(period: BrightnessPeriod, percentage: Int) {
        val key = when (period) {
            BrightnessPeriod.MORNING -> BRIGHTNESS_MORNING
            BrightnessPeriod.DAY -> BRIGHTNESS_DAY
            BrightnessPeriod.AFTERNOON -> BRIGHTNESS_AFTERNOON
            BrightnessPeriod.EVENING -> BRIGHTNESS_EVENING
            BrightnessPeriod.NIGHT -> BRIGHTNESS_NIGHT
        }
        setInteger(key, percentage.coerceIn(MIN_BRIGHTNESS_PERCENTAGE, MAX_BRIGHTNESS_PERCENTAGE))
    }

    suspend fun restore(restored: LauncherSettings) {
        context.launcherSettingsDataStore.edit { preferences ->
            preferences[APPLICATION_SORT_MODE] = restored.applicationSortMode.name
            preferences[THEME] = restored.appearance.theme.name
            preferences[SHOW_APP_NAMES] = restored.appearance.showAppNames
            preferences[SHOW_CATEGORY_TITLES] = restored.appearance.showCategoryTitles
            preferences[SHOW_FOCUS_OUTLINE] = restored.appearance.showFocusOutline
            preferences[APP_CARD_FOCUS_ANIMATIONS] = restored.appearance.appCardFocusAnimations
            preferences[SELECTOR_TRANSITION_ANIMATIONS] = restored.appearance.selectorTransitionAnimations
            preferences[KEY_CLICK_SOUNDS] = restored.appearance.keyClickSounds
            preferences[STATUS_AUTO_HIDE] = restored.statusBar.autoHide
            preferences[STATUS_SHOW_DATE] = restored.statusBar.showDate
            preferences[STATUS_SHOW_TIME] = restored.statusBar.showTime
            preferences[STATUS_SHOW_NETWORK] = restored.statusBar.showNetwork
            preferences[STATUS_SHOW_INPUTS] = restored.statusBar.showInputs
            preferences[STATUS_SHOW_NOTIFICATIONS] = restored.statusBar.showNotifications
            preferences[STATUS_AUTO_HIDE_NOTIFICATION_BELL] = restored.statusBar.autoHideNotificationBell
            preferences[SYSTEM_NOTIFICATION_POPUPS] = restored.statusBar.systemNotificationPopups
            preferences[STATUS_DATE_FORMAT] = restored.statusBar.dateFormat
            preferences[STATUS_TIME_FORMAT] = restored.statusBar.timeFormat
            preferences[SCREENSAVER_CLOCK_STYLE] = restored.screensaver.clockStyle.name
            preferences[BACK_BUTTON_ACTION] = restored.screensaver.backButtonAction.name
            preferences[BRIGHTNESS_ENABLED] = restored.brightness.enabled
            preferences[BRIGHTNESS_MORNING] = restored.brightness.morningPercentage
            preferences[BRIGHTNESS_DAY] = restored.brightness.dayPercentage
            preferences[BRIGHTNESS_AFTERNOON] = restored.brightness.afternoonPercentage
            preferences[BRIGHTNESS_EVENING] = restored.brightness.eveningPercentage
            preferences[BRIGHTNESS_NIGHT] = restored.brightness.nightPercentage
        }
    }

    private suspend fun <T : Enum<T>> setEnum(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: T
    ) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value.name }
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value }
    }

    private suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value }
    }

    private suspend fun setInteger(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.launcherSettingsDataStore.edit { preferences -> preferences[key] = value }
    }

    private fun defaultTimeFormat(): String = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    private companion object {
        val APPLICATION_SORT_MODE = stringPreferencesKey("application_sort_mode")
        val THEME = stringPreferencesKey("theme")
        val SHOW_APP_NAMES = booleanPreferencesKey("show_app_names")
        val SHOW_CATEGORY_TITLES = booleanPreferencesKey("show_category_titles")
        val SHOW_FOCUS_OUTLINE = booleanPreferencesKey("show_focus_outline")
        val APP_CARD_FOCUS_ANIMATIONS = booleanPreferencesKey("app_card_focus_animations")
        val SELECTOR_TRANSITION_ANIMATIONS = booleanPreferencesKey("selector_transition_animations")
        val KEY_CLICK_SOUNDS = booleanPreferencesKey("key_click_sounds")
        val STATUS_AUTO_HIDE = booleanPreferencesKey("status_auto_hide")
        val STATUS_SHOW_DATE = booleanPreferencesKey("status_show_date")
        val STATUS_SHOW_TIME = booleanPreferencesKey("status_show_time")
        val STATUS_SHOW_NETWORK = booleanPreferencesKey("status_show_network")
        val STATUS_SHOW_INPUTS = booleanPreferencesKey("status_show_inputs")
        val STATUS_SHOW_NOTIFICATIONS = booleanPreferencesKey("status_show_notifications")
        val STATUS_AUTO_HIDE_NOTIFICATION_BELL = booleanPreferencesKey("status_auto_hide_notification_bell")
        val SYSTEM_NOTIFICATION_POPUPS = booleanPreferencesKey("system_notification_popups")
        val STATUS_DATE_FORMAT = stringPreferencesKey("status_date_format")
        val STATUS_TIME_FORMAT = stringPreferencesKey("status_time_format")
        val SCREENSAVER_CLOCK_STYLE = stringPreferencesKey("screensaver_clock_style")
        val BACK_BUTTON_ACTION = stringPreferencesKey("back_button_action")
        val BRIGHTNESS_ENABLED = booleanPreferencesKey("brightness_scheduler_enabled")
        val BRIGHTNESS_MORNING = intPreferencesKey("brightness_morning")
        val BRIGHTNESS_DAY = intPreferencesKey("brightness_day")
        val BRIGHTNESS_AFTERNOON = intPreferencesKey("brightness_afternoon")
        val BRIGHTNESS_EVENING = intPreferencesKey("brightness_evening")
        val BRIGHTNESS_NIGHT = intPreferencesKey("brightness_night")
        const val DEFAULT_DATE_FORMAT = "EEE, MMM d"
        const val MIN_BRIGHTNESS_PERCENTAGE = 5
        const val MAX_BRIGHTNESS_PERCENTAGE = 100
    }
}

private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enumValue(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    defaultValue: T
): T = this[key]?.let { storedValue -> enumValues<T>().firstOrNull { it.name == storedValue } } ?: defaultValue
