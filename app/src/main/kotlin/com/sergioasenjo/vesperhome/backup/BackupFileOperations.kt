package com.sergioasenjo.vesperhome.backup

import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun uniqueDatedBackupFile(directory: File, prefix: String, extension: String, createdAt: Long): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(createdAt))
    val baseName = "$prefix$timestamp"
    var candidate = File(directory, "$baseName$extension")
    var suffix = 1
    while (candidate.exists()) candidate = File(directory, "${baseName}_${suffix++}$extension")
    return candidate
}

internal fun trimFiles(directory: File, extension: String, maximum: Int) {
    directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(extension) }
        .sortedByDescending(File::lastModified)
        .drop(maximum)
        .forEach(File::delete)
}

internal fun File.isDirectChildOf(directory: File): Boolean = parentFile?.canonicalFile == directory.canonicalFile

internal fun safetyBackupReason(file: File): SafetyBackupReason =
    if (file.name.contains(SafetyBackupReason.BEFORE_RESTORE.name.lowercase(Locale.US))) {
        SafetyBackupReason.BEFORE_RESTORE
    } else {
        SafetyBackupReason.SCHEDULED
    }

internal fun replaceFile(source: File, destination: File) {
    val previous = File(destination.parentFile, ".${destination.name}.previous")
    previous.delete()
    if (destination.exists()) check(destination.renameTo(previous)) { "The previous state could not be staged" }
    try {
        check(source.renameTo(destination)) { "The backup file could not be finalized" }
        previous.delete()
    } catch (error: Exception) {
        if (previous.exists()) previous.renameTo(destination)
        throw error
    }
}

internal fun copyDirectoryIfPresent(source: File, destination: File) {
    if (!source.isDirectory) return
    check(source.copyRecursively(destination, overwrite = true)) { "Backup assets could not be copied" }
}

internal fun validateBackupImage(file: File) {
    require(file.isFile)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "A backup asset is not an image" }
}

internal const val BACKUP_DIRECTORY = "backups"
internal const val SAFETY_BACKUP_DIRECTORY = "safety_backups"
internal const val BANNER_DIRECTORY = "custom_banners"
internal const val WALLPAPER_DIRECTORY = "wallpapers"
internal const val PROFILE_DIRECTORY = "profiles"
internal const val PROFILE_REGISTRY_FILE = "registry.json"
internal const val BACKUP_PREFIX = "vesper_home_backup_"
internal const val SAFETY_BACKUP_PREFIX = "vesper_home_safety_"
internal const val BACKUP_EXTENSION = ".vesperbackup"
internal const val METADATA_ENTRY = "backup.json"
internal const val ASSET_ENTRY_PREFIX = "assets"
internal const val PROFILE_ENTRY_PREFIX = "profiles"
internal const val MAX_METADATA_BYTES = 2 * 1024 * 1024
internal const val MAX_ARCHIVE_ENTRIES = 10_010
internal const val MAX_ASSET_BYTES = 25L * 1024 * 1024
internal const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
internal const val MAX_EXTRACTED_BYTES = 100L * 1024 * 1024
internal const val MAX_SAFETY_BACKUPS = 5
internal val PROFILE_ARCHIVE_PATTERN = Regex("[a-f0-9-]+\\.vesperprofile")
