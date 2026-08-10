package com.sergioasenjo.vesperhome.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.sergioasenjo.vesperhome.data.LauncherDatabase
import com.sergioasenjo.vesperhome.settings.LauncherSettingsRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSelection
import com.sergioasenjo.vesperhome.wallpaper.WallpaperTarget
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(
    context: Context,
    private val database: LauncherDatabase,
    private val launcherSettingsRepository: LauncherSettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val json: Json
) {
    private val applicationContext = context.applicationContext
    private val backupDirectory = File(applicationContext.filesDir, BACKUP_DIRECTORY)
    private val bannerDirectory = File(applicationContext.filesDir, BANNER_DIRECTORY)
    private val wallpaperDirectory = File(applicationContext.filesDir, WALLPAPER_DIRECTORY)

    suspend fun listBackups(): List<BackupFileEntry> = withContext(Dispatchers.IO) {
        backupDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(BACKUP_EXTENSION) }
            .map { file -> BackupFileEntry(file, file.lastModified(), file.length()) }
            .sortedByDescending(BackupFileEntry::createdAt)
    }

    suspend fun createBackup(): BackupFileEntry = withContext(Dispatchers.IO) {
        val createdAt = System.currentTimeMillis()
        val snapshot = captureSnapshot()
        val document = snapshot.toDocument(createdAt)
        val assets = collectCurrentAssets(snapshot)
        require(assets.sumOf { it.second.length() } <= MAX_ARCHIVE_BYTES) { "Backup assets are too large" }
        backupDirectory.mkdirs()
        val destination = uniqueBackupFile(createdAt)
        val temporaryFile = File.createTempFile("backup-", ".tmp", backupDirectory)
        try {
            ZipOutputStream(temporaryFile.outputStream().buffered()).use { output ->
                val metadata = json.encodeToString(document).toByteArray(Charsets.UTF_8)
                require(metadata.size <= MAX_METADATA_BYTES)
                output.writeEntry(METADATA_ENTRY, metadata.inputStream(), createdAt)
                assets.forEach { (entryName, file) ->
                    require(file.isFile && file.length() <= MAX_ASSET_BYTES) {
                        "A backup image is missing or too large"
                    }
                    file.inputStream().buffered().use { input -> output.writeEntry(entryName, input, createdAt) }
                }
            }
            check(temporaryFile.renameTo(destination)) { "The backup file could not be finalized" }
            BackupFileEntry(destination, destination.lastModified(), destination.length())
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun importBackup(source: Uri): BackupFileEntry = withContext(Dispatchers.IO) {
        val temporaryFile = File.createTempFile("import-", BACKUP_EXTENSION, applicationContext.cacheDir)
        try {
            applicationContext.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "The selected backup could not be opened" }
                temporaryFile.outputStream().buffered().use { output ->
                    copyLimited(input, output, MAX_ARCHIVE_BYTES, CopyBudget(MAX_ARCHIVE_BYTES))
                }
            }
            val prepared = readArchive(temporaryFile)
            prepared.stageDirectory.deleteRecursively()
            backupDirectory.mkdirs()
            val destination = uniqueBackupFile(prepared.document.createdAt)
            val stagedDestination = File(backupDirectory, ".${destination.name}.tmp")
            try {
                temporaryFile.inputStream().use { input -> stagedDestination.outputStream().use(input::copyTo) }
                check(stagedDestination.renameTo(destination)) { "The imported backup could not be finalized" }
            } finally {
                stagedDestination.delete()
            }
            destination.setLastModified(prepared.document.createdAt)
            BackupFileEntry(destination, destination.lastModified(), destination.length())
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun restoreBackup(entry: BackupFileEntry) = withContext(Dispatchers.IO) {
        require(entry.file.parentFile?.canonicalFile == backupDirectory.canonicalFile)
        val prepared = readArchive(entry.file)
        val previousSnapshot = captureSnapshot()
        val previousAssets = File.createTempFile("asset-rollback-", ".tmp", applicationContext.cacheDir).also {
            it.delete()
            it.mkdirs()
        }
        copyDirectoryIfPresent(bannerDirectory, File(previousAssets, BANNER_DIRECTORY))
        copyDirectoryIfPresent(wallpaperDirectory, File(previousAssets, WALLPAPER_DIRECTORY))
        try {
            installAssets(prepared.stageDirectory, prepared.snapshot)
            replaceDatabase(prepared.snapshot)
            launcherSettingsRepository.restore(prepared.snapshot.settings)
            wallpaperRepository.restore(prepared.snapshot.wallpaper)
        } catch (error: Exception) {
            runCatching {
                installAssetDirectories(
                    File(previousAssets, BANNER_DIRECTORY),
                    File(previousAssets, WALLPAPER_DIRECTORY)
                )
                replaceDatabase(previousSnapshot)
                launcherSettingsRepository.restore(previousSnapshot.settings)
                wallpaperRepository.restore(previousSnapshot.wallpaper)
            }
            throw error
        } finally {
            prepared.stageDirectory.deleteRecursively()
            previousAssets.deleteRecursively()
        }
    }

    suspend fun deleteBackup(entry: BackupFileEntry): Boolean = withContext(Dispatchers.IO) {
        entry.file.parentFile?.canonicalFile == backupDirectory.canonicalFile && entry.file.delete()
    }

    private suspend fun captureSnapshot(): BackupSnapshot = BackupSnapshot(
        settings = launcherSettingsRepository.settings.first(),
        wallpaper = wallpaperRepository.state.first().settings,
        appPreferences = database.appPreferenceDao().getAll(),
        categories = database.categoryDao().getCategories(),
        memberships = database.categoryDao().getMemberships(),
        spacers = database.categoryDao().getSpacers()
    )

    private fun collectCurrentAssets(snapshot: BackupSnapshot): List<Pair<String, File>> {
        val assets = mutableListOf<Pair<String, File>>()
        listOf(
            WallpaperTarget.MAIN to snapshot.wallpaper.main,
            WallpaperTarget.DAY to snapshot.wallpaper.day,
            WallpaperTarget.NIGHT to snapshot.wallpaper.night
        ).forEach { (target, selection) ->
            if (selection == WallpaperSelection.Custom) {
                assets += wallpaperEntry(target) to wallpaperRepository.customFile(target)
            }
        }
        snapshot.appPreferences
            .filter { it.customBannerRevision != null }
            .map { it.componentName.substringBefore('/') }
            .distinct()
            .forEach { packageName -> assets += bannerEntry(packageName) to bannerFile(packageName) }
        return assets
    }

    private suspend fun replaceDatabase(snapshot: BackupSnapshot) {
        database.withTransaction {
            val categoryDao = database.categoryDao()
            val appPreferenceDao = database.appPreferenceDao()
            categoryDao.deleteAllMemberships()
            categoryDao.deleteAllCategories()
            categoryDao.deleteAllSpacers()
            appPreferenceDao.deleteAll()
            categoryDao.insertCategories(snapshot.categories)
            categoryDao.insertSpacers(snapshot.spacers)
            categoryDao.insertMemberships(snapshot.memberships)
            appPreferenceDao.insertAll(snapshot.appPreferences)
        }
    }

    private fun readArchive(file: File): PreparedBackup {
        require(file.isFile && file.length() in 1..MAX_ARCHIVE_BYTES) { "Invalid backup size" }
        val stageDirectory = File.createTempFile("backup-stage-", ".tmp", applicationContext.cacheDir).also {
            it.delete()
            it.mkdirs()
        }
        try {
            var metadata: ByteArray? = null
            val extractedEntries = mutableSetOf<String>()
            val budget = CopyBudget(MAX_EXTRACTED_BYTES)
            var entryCount = 0
            ZipInputStream(file.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    entryCount += 1
                    require(entryCount <= MAX_ARCHIVE_ENTRIES) { "Backup has too many entries" }
                    require(!entry.isDirectory && extractedEntries.add(entry.name)) { "Invalid backup entry" }
                    when {
                        entry.name == METADATA_ENTRY -> {
                            val output = java.io.ByteArrayOutputStream()
                            copyLimited(input, output, MAX_METADATA_BYTES.toLong(), budget)
                            metadata = output.toByteArray()
                        }

                        entry.name.startsWith(ASSET_ENTRY_PREFIX) -> {
                            require(isSafeAssetEntry(entry.name)) { "Unsafe backup entry" }
                            val destination = File(stageDirectory, entry.name)
                            require(destination.canonicalPath.startsWith(stageDirectory.canonicalPath + File.separator))
                            destination.parentFile?.mkdirs()
                            destination.outputStream().buffered().use { output ->
                                copyLimited(input, output, MAX_ASSET_BYTES, budget)
                            }
                        }

                        else -> error("Unknown backup entry")
                    }
                    input.closeEntry()
                }
            }
            val document = json.decodeFromString<BackupDocument>(
                requireNotNull(metadata) { "Backup metadata is missing" }.toString(Charsets.UTF_8)
            )
            require(document.createdAt > 0)
            val snapshot = document.toSnapshot()
            val expectedAssets = expectedAssetEntries(document)
            require(extractedEntries - METADATA_ENTRY == expectedAssets) { "Backup assets do not match metadata" }
            expectedAssets.forEach { entryName -> validateImage(File(stageDirectory, entryName)) }
            return PreparedBackup(document, snapshot, stageDirectory)
        } catch (error: Exception) {
            stageDirectory.deleteRecursively()
            throw error
        }
    }

    private fun installAssets(stageDirectory: File, snapshot: BackupSnapshot) {
        installAssetDirectories(
            File(stageDirectory, "$ASSET_ENTRY_PREFIX/banners"),
            File(stageDirectory, "$ASSET_ENTRY_PREFIX/wallpapers")
        )
        snapshot.appPreferences
            .filter { it.customBannerRevision != null }
            .forEach { require(bannerFile(it.componentName.substringBefore('/')).isFile) }
        listOf(WallpaperTarget.MAIN, WallpaperTarget.DAY, WallpaperTarget.NIGHT).forEach { target ->
            if (snapshot.wallpaper.selection(target) == WallpaperSelection.Custom) {
                require(wallpaperRepository.customFile(target).isFile)
            }
        }
    }

    private fun installAssetDirectories(sourceBanners: File, sourceWallpapers: File) {
        bannerDirectory.deleteRecursively()
        wallpaperDirectory.deleteRecursively()
        copyDirectoryIfPresent(sourceBanners, bannerDirectory)
        copyDirectoryIfPresent(sourceWallpapers, wallpaperDirectory)
    }

    private fun expectedAssetEntries(document: BackupDocument): Set<String> = buildSet {
        if (document.wallpaper.main == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.MAIN))
        if (document.wallpaper.day == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.DAY))
        if (document.wallpaper.night == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.NIGHT))
        document.appPreferences
            .filter(AppPreferenceBackup::hasCustomBanner)
            .map { it.componentName.substringBefore('/') }
            .distinct()
            .forEach { add(bannerEntry(it)) }
    }

    private fun uniqueBackupFile(createdAt: Long): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(createdAt))
        val baseName = "$BACKUP_PREFIX$timestamp"
        var candidate = File(backupDirectory, "$baseName$BACKUP_EXTENSION")
        var suffix = 1
        while (candidate.exists()) candidate = File(backupDirectory, "${baseName}_${suffix++}$BACKUP_EXTENSION")
        return candidate
    }

    private fun bannerFile(packageName: String): File = File(bannerDirectory, "$packageName.image")

    private fun bannerEntry(packageName: String): String = "$ASSET_ENTRY_PREFIX/banners/$packageName.image"

    private fun wallpaperEntry(target: WallpaperTarget): String =
        "$ASSET_ENTRY_PREFIX/wallpapers/${target.name.lowercase(Locale.US)}.image"

    private fun isSafeAssetEntry(entryName: String): Boolean =
        entryName.matches(Regex("assets/(banners/[A-Za-z0-9_.]+|wallpapers/(main|day|night))\\.image"))

    private fun validateImage(file: File) {
        require(file.isFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "A backup asset is not an image" }
    }

    private fun copyDirectoryIfPresent(source: File, destination: File) {
        if (!source.isDirectory) return
        check(source.copyRecursively(destination, overwrite = true)) { "Backup assets could not be copied" }
    }

    private data class PreparedBackup(
        val document: BackupDocument,
        val snapshot: BackupSnapshot,
        val stageDirectory: File
    )

    private companion object {
        const val BACKUP_DIRECTORY = "backups"
        const val BANNER_DIRECTORY = "custom_banners"
        const val WALLPAPER_DIRECTORY = "wallpapers"
        const val BACKUP_PREFIX = "vesper_home_backup_"
        const val BACKUP_EXTENSION = ".vesperbackup"
        const val METADATA_ENTRY = "backup.json"
        const val ASSET_ENTRY_PREFIX = "assets"
        const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        const val MAX_ARCHIVE_ENTRIES = 10_010
        const val MAX_ASSET_BYTES = 25L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
        const val MAX_EXTRACTED_BYTES = 100L * 1024 * 1024
    }
}

private class CopyBudget(private val maximum: Long) {
    private var copied = 0L

    fun add(byteCount: Int) {
        copied += byteCount
        require(copied <= maximum) { "Backup data is too large" }
    }
}

private fun ZipOutputStream.writeEntry(name: String, input: InputStream, timestamp: Long) {
    putNextEntry(ZipEntry(name).apply { time = timestamp })
    input.copyTo(this)
    closeEntry()
}

private fun copyLimited(input: InputStream, output: OutputStream, maximum: Long, budget: CopyBudget) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        copied += count
        require(copied <= maximum) { "Backup entry is too large" }
        budget.add(count)
        output.write(buffer, 0, count)
    }
}
