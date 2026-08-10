package com.sergioasenjo.vesperhome.backup

import com.sergioasenjo.vesperhome.applications.ApplicationSortMode
import com.sergioasenjo.vesperhome.brightness.BrightnessSettings
import com.sergioasenjo.vesperhome.data.AppPreferenceEntity
import com.sergioasenjo.vesperhome.data.CategoryAppEntity
import com.sergioasenjo.vesperhome.data.CategoryEntity
import com.sergioasenjo.vesperhome.data.SpacerEntity
import com.sergioasenjo.vesperhome.screensaver.BackButtonAction
import com.sergioasenjo.vesperhome.screensaver.ScreensaverClockStyle
import com.sergioasenjo.vesperhome.screensaver.ScreensaverSettings
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.settings.LauncherTheme
import com.sergioasenjo.vesperhome.status.StatusBarSettings
import com.sergioasenjo.vesperhome.wallpaper.BuiltInWallpaper
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSelection
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.serialization.Serializable

data class BackupFileEntry(val file: File, val createdAt: Long, val size: Long)

@Serializable
internal data class BackupDocument(
    val version: Int,
    val createdAt: Long,
    val settings: SettingsBackup,
    val wallpaper: WallpaperBackup,
    val appPreferences: List<AppPreferenceBackup>,
    val categories: List<CategoryBackup>,
    val memberships: List<MembershipBackup>,
    val spacers: List<SpacerBackup>
)

@Serializable
internal data class SettingsBackup(
    val applicationSortMode: String,
    val theme: String,
    val showAppNames: Boolean,
    val showCategoryTitles: Boolean,
    val showFocusOutline: Boolean,
    val appCardFocusAnimations: Boolean,
    val selectorTransitionAnimations: Boolean,
    val keyClickSounds: Boolean,
    val statusAutoHide: Boolean,
    val statusShowDate: Boolean,
    val statusShowTime: Boolean,
    val statusShowNetwork: Boolean,
    val statusShowInputs: Boolean,
    val statusShowNotifications: Boolean,
    val statusAutoHideNotificationBell: Boolean,
    val systemNotificationPopups: Boolean,
    val dateFormat: String,
    val timeFormat: String,
    val screensaverClockStyle: String,
    val backButtonAction: String,
    val brightnessEnabled: Boolean,
    val brightnessMorning: Int,
    val brightnessDay: Int,
    val brightnessAfternoon: Int,
    val brightnessEvening: Int,
    val brightnessNight: Int
)

@Serializable
internal data class WallpaperBackup(val timeBasedEnabled: Boolean, val main: String, val day: String, val night: String)

@Serializable
internal data class AppPreferenceBackup(
    val componentName: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val manualOrder: Long?,
    val lastUsedAt: Long?,
    val hasCustomBanner: Boolean
)

@Serializable
internal data class CategoryBackup(
    val id: Long,
    val name: String,
    val position: Long,
    val sortMode: String,
    val layoutType: String,
    val gridColumns: Int,
    val rowHeight: Int
)

@Serializable
internal data class MembershipBackup(val categoryId: Long, val componentName: String, val position: Long)

@Serializable
internal data class SpacerBackup(val id: Long, val position: Long, val height: Int)

internal data class BackupSnapshot(
    val settings: LauncherSettings,
    val wallpaper: WallpaperSettings,
    val appPreferences: List<AppPreferenceEntity>,
    val categories: List<CategoryEntity>,
    val memberships: List<CategoryAppEntity>,
    val spacers: List<SpacerEntity>
)

internal fun BackupSnapshot.toDocument(createdAt: Long): BackupDocument = BackupDocument(
    version = BACKUP_FORMAT_VERSION,
    createdAt = createdAt,
    settings = settings.toBackup(),
    wallpaper = wallpaper.toBackup(),
    appPreferences = appPreferences.map { preference ->
        AppPreferenceBackup(
            preference.componentName,
            preference.isFavorite,
            preference.isHidden,
            preference.manualOrder,
            preference.lastUsedAt,
            preference.customBannerRevision != null
        )
    },
    categories = categories.map { category ->
        CategoryBackup(
            category.categoryId,
            category.name,
            category.position,
            category.sortMode,
            category.layoutType,
            category.gridColumns,
            category.rowHeight
        )
    },
    memberships = memberships.map { MembershipBackup(it.categoryId, it.componentName, it.position) },
    spacers = spacers.map { SpacerBackup(it.spacerId, it.position, it.height) }
)

internal fun BackupDocument.toSnapshot(): BackupSnapshot {
    require(version == BACKUP_FORMAT_VERSION) { "Unsupported backup version" }
    require(appPreferences.size <= MAX_APP_PREFERENCES)
    require(categories.size <= MAX_CATEGORIES)
    require(memberships.size <= MAX_MEMBERSHIPS)
    require(spacers.size <= MAX_SPACERS)
    require(categories.map(CategoryBackup::id).distinct().size == categories.size)
    require(categories.map { it.name.lowercase() }.distinct().size == categories.size)
    val categoryIds = categories.map(CategoryBackup::id).toSet()
    require(memberships.all { it.categoryId in categoryIds })
    val restoredRevision = System.currentTimeMillis()
    return BackupSnapshot(
        settings = settings.toDomain(),
        wallpaper = wallpaper.toDomain(),
        appPreferences = appPreferences.map { preference ->
            requireValidComponent(preference.componentName)
            AppPreferenceEntity(
                componentName = preference.componentName,
                isFavorite = preference.isFavorite,
                isHidden = preference.isHidden,
                manualOrder = preference.manualOrder,
                lastUsedAt = preference.lastUsedAt,
                customBannerRevision = restoredRevision.takeIf { preference.hasCustomBanner }
            )
        },
        categories = categories.map { category ->
            require(category.id > 0 && category.name.isNotBlank() && category.name.length <= MAX_CATEGORY_NAME_LENGTH)
            require(category.gridColumns in 5..10 && category.rowHeight in 80..150)
            require(category.sortMode in setOf("MANUAL", "ALPHABETICAL", "LAST_USED"))
            require(category.layoutType in setOf("ROW", "GRID"))
            CategoryEntity(
                category.id,
                category.name,
                category.position,
                category.sortMode,
                category.layoutType,
                category.gridColumns,
                category.rowHeight
            )
        },
        memberships = memberships.map { membership ->
            requireValidComponent(membership.componentName)
            CategoryAppEntity(membership.categoryId, membership.componentName, membership.position)
        },
        spacers = spacers.map { spacer ->
            require(spacer.id > 0 && spacer.height in VALID_SPACER_HEIGHTS)
            SpacerEntity(spacer.id, spacer.position, spacer.height)
        }
    )
}

private fun LauncherSettings.toBackup(): SettingsBackup = SettingsBackup(
    applicationSortMode.name,
    appearance.theme.name,
    appearance.showAppNames,
    appearance.showCategoryTitles,
    appearance.showFocusOutline,
    appearance.appCardFocusAnimations,
    appearance.selectorTransitionAnimations,
    appearance.keyClickSounds,
    statusBar.autoHide,
    statusBar.showDate,
    statusBar.showTime,
    statusBar.showNetwork,
    statusBar.showInputs,
    statusBar.showNotifications,
    statusBar.autoHideNotificationBell,
    statusBar.systemNotificationPopups,
    statusBar.dateFormat,
    statusBar.timeFormat,
    screensaver.clockStyle.name,
    screensaver.backButtonAction.name,
    brightness.enabled,
    brightness.morningPercentage,
    brightness.dayPercentage,
    brightness.afternoonPercentage,
    brightness.eveningPercentage,
    brightness.nightPercentage
)

private fun SettingsBackup.toDomain(): LauncherSettings {
    requireValidDateTimePattern(dateFormat)
    requireValidDateTimePattern(timeFormat)
    val brightnessValues = listOf(
        brightnessMorning,
        brightnessDay,
        brightnessAfternoon,
        brightnessEvening,
        brightnessNight
    )
    require(brightnessValues.all { it in 5..100 })
    return LauncherSettings(
        applicationSortMode = enumValueOf<ApplicationSortMode>(applicationSortMode),
        appearance = LauncherAppearance(
            enumValueOf<LauncherTheme>(theme),
            showAppNames,
            showCategoryTitles,
            showFocusOutline,
            appCardFocusAnimations,
            selectorTransitionAnimations,
            keyClickSounds
        ),
        statusBar = StatusBarSettings(
            statusAutoHide,
            statusShowDate,
            statusShowTime,
            statusShowNetwork,
            statusShowInputs,
            statusShowNotifications,
            statusAutoHideNotificationBell,
            systemNotificationPopups,
            dateFormat,
            timeFormat
        ),
        screensaver = ScreensaverSettings(
            enumValueOf<ScreensaverClockStyle>(screensaverClockStyle),
            enumValueOf<BackButtonAction>(backButtonAction)
        ),
        brightness = BrightnessSettings(
            brightnessEnabled,
            brightnessMorning,
            brightnessDay,
            brightnessAfternoon,
            brightnessEvening,
            brightnessNight
        )
    )
}

private fun WallpaperSettings.toBackup(): WallpaperBackup = WallpaperBackup(
    timeBasedEnabled,
    main.backupValue,
    day.backupValue,
    night.backupValue
)

private fun WallpaperBackup.toDomain(): WallpaperSettings = WallpaperSettings(
    timeBasedEnabled,
    main.toWallpaperSelection(),
    day.toWallpaperSelection(),
    night.toWallpaperSelection()
)

private val WallpaperSelection.backupValue: String
    get() = when (this) {
        is WallpaperSelection.BuiltIn -> wallpaper.name
        WallpaperSelection.Custom -> CUSTOM_WALLPAPER
    }

private fun String.toWallpaperSelection(): WallpaperSelection =
    if (this == CUSTOM_WALLPAPER) WallpaperSelection.Custom else WallpaperSelection.BuiltIn(enumValueOf(this))

private fun requireValidComponent(componentName: String) {
    require(componentName.isNotBlank() && componentName.length <= MAX_COMPONENT_NAME_LENGTH)
    require(componentName.substringBefore('/').matches(PACKAGE_NAME_PATTERN))
}

private fun requireValidDateTimePattern(pattern: String) {
    require(pattern.isNotBlank() && pattern.length <= MAX_DATE_TIME_PATTERN_LENGTH)
    SimpleDateFormat(pattern, Locale.getDefault())
}

internal const val BACKUP_FORMAT_VERSION = 1
internal const val CUSTOM_WALLPAPER = "CUSTOM"
private const val MAX_APP_PREFERENCES = 10_000
private const val MAX_CATEGORIES = 500
private const val MAX_MEMBERSHIPS = 50_000
private const val MAX_SPACERS = 500
private const val MAX_CATEGORY_NAME_LENGTH = 100
private const val MAX_COMPONENT_NAME_LENGTH = 500
private const val MAX_DATE_TIME_PATTERN_LENGTH = 100
private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]+")
private val VALID_SPACER_HEIGHTS = setOf(10, 20, 30, 40, 50, 75, 100, 150)
