package com.sergioasenjo.vesperhome.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sergioasenjo.vesperhome.data.LauncherDatabase
import com.sergioasenjo.vesperhome.settings.LauncherSettingsRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperRepository
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSelection
import com.sergioasenjo.vesperhome.wallpaper.WallpaperTarget
import java.io.File
import java.util.Locale
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
    private val safetyBackupDirectory = File(applicationContext.filesDir, SAFETY_BACKUP_DIRECTORY)
    private val profileDirectory = File(applicationContext.filesDir, PROFILE_DIRECTORY)
    private val bannerDirectory = File(applicationContext.filesDir, BANNER_DIRECTORY)
    private val wallpaperDirectory = File(applicationContext.filesDir, WALLPAPER_DIRECTORY)

    suspend fun listBackups(): List<BackupFileEntry> = withContext(Dispatchers.IO) {
        val manual = backupDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(BACKUP_EXTENSION) }
            .map { file -> BackupFileEntry(file, file.lastModified(), file.length()) }
        val safety = safetyBackupDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(SAFETY_BACKUP_PREFIX) && it.name.endsWith(BACKUP_EXTENSION) }
            .map { file ->
                BackupFileEntry(
                    file,
                    file.lastModified(),
                    file.length(),
                    BackupKind.SAFETY,
                    safetyBackupReason(file)
                )
            }
        (manual + safety)
            .sortedByDescending(BackupFileEntry::createdAt)
    }

    suspend fun createBackup(): BackupFileEntry = withContext(Dispatchers.IO) {
        createArchive(backupDirectory, BACKUP_PREFIX, BackupKind.MANUAL)
    }

    suspend fun createSafetyBackup(reason: SafetyBackupReason): BackupFileEntry = withContext(Dispatchers.IO) {
        val entry = createArchive(
            safetyBackupDirectory,
            "$SAFETY_BACKUP_PREFIX${reason.name.lowercase(Locale.US)}_",
            BackupKind.SAFETY,
            reason
        )
        trimFiles(safetyBackupDirectory, BACKUP_EXTENSION, MAX_SAFETY_BACKUPS)
        entry
    }

    private suspend fun createArchive(
        directory: File,
        prefix: String,
        kind: BackupKind,
        reason: SafetyBackupReason? = null
    ): BackupFileEntry {
        val createdAt = System.currentTimeMillis()
        directory.mkdirs()
        return writeArchive(
            uniqueDatedBackupFile(directory, prefix, BACKUP_EXTENSION, createdAt),
            createdAt,
            kind,
            reason
        )
    }

    internal suspend fun saveProfileState(destination: File) = withContext(Dispatchers.IO) {
        require(isProfileStateFile(destination))
        destination.parentFile?.mkdirs()
        writeArchive(destination, System.currentTimeMillis(), BackupKind.MANUAL)
    }

    internal suspend fun restoreProfileState(source: File) = withContext(Dispatchers.IO) {
        require(isProfileStateFile(source))
        restoreArchive(source)
    }

    private suspend fun writeArchive(
        destination: File,
        createdAt: Long,
        kind: BackupKind,
        reason: SafetyBackupReason? = null
    ): BackupFileEntry {
        val snapshot = captureSnapshot()
        val profileFiles = collectProfileFiles(destination)
        val document = snapshot.toDocument(createdAt, profileFiles.map { it.first })
        val assets = collectCurrentAssets(snapshot) + profileFiles
        require(assets.sumOf { it.second.length() } <= MAX_ARCHIVE_BYTES) { "Backup assets are too large" }
        val directory = requireNotNull(destination.parentFile)
        val temporaryFile = File.createTempFile("backup-", ".tmp", directory)
        try {
            ZipOutputStream(temporaryFile.outputStream().buffered()).use { output ->
                val metadata = json.encodeToString(document).toByteArray(Charsets.UTF_8)
                require(metadata.size <= MAX_METADATA_BYTES)
                output.writeEntry(METADATA_ENTRY, metadata.inputStream(), createdAt)
                assets.forEach { (entryName, file) ->
                    val maximum = if (entryName.startsWith(PROFILE_ENTRY_PREFIX)) {
                        MAX_ARCHIVE_BYTES
                    } else {
                        MAX_ASSET_BYTES
                    }
                    require(file.isFile && file.length() <= maximum) {
                        "A backup asset is missing or too large"
                    }
                    file.inputStream().buffered().use { input -> output.writeEntry(entryName, input, createdAt) }
                }
            }
            replaceFile(temporaryFile, destination)
            destination.setLastModified(createdAt)
            return BackupFileEntry(destination, createdAt, destination.length(), kind, reason)
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
            val destination = uniqueDatedBackupFile(
                backupDirectory,
                BACKUP_PREFIX,
                BACKUP_EXTENSION,
                prepared.document.createdAt
            )
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
        require(isManagedBackup(entry.file))
        createSafetyBackup(SafetyBackupReason.BEFORE_RESTORE)
        restoreArchive(entry.file)
    }

    private suspend fun restoreArchive(source: File) {
        val prepared = readArchive(source)
        val previousSnapshot = captureSnapshot()
        val previousAssets = File.createTempFile("asset-rollback-", ".tmp", applicationContext.cacheDir).also {
            it.delete()
            it.mkdirs()
        }
        copyDirectoryIfPresent(bannerDirectory, File(previousAssets, BANNER_DIRECTORY))
        copyDirectoryIfPresent(wallpaperDirectory, File(previousAssets, WALLPAPER_DIRECTORY))
        copyDirectoryIfPresent(profileDirectory, File(previousAssets, PROFILE_DIRECTORY))
        try {
            installAssets(prepared.stageDirectory, prepared.snapshot)
            installProfiles(prepared.stageDirectory, prepared.document)
            replaceDatabase(prepared.snapshot)
            launcherSettingsRepository.restore(prepared.snapshot.settings)
            wallpaperRepository.restore(prepared.snapshot.wallpaper)
        } catch (error: Exception) {
            runCatching {
                installAssetDirectories(
                    File(previousAssets, BANNER_DIRECTORY),
                    File(previousAssets, WALLPAPER_DIRECTORY)
                )
                installProfileDirectory(File(previousAssets, PROFILE_DIRECTORY))
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
        isManagedBackup(entry.file) && entry.file.delete()
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

    private fun collectProfileFiles(destination: File): List<Pair<String, File>> {
        if (destination.parentFile?.canonicalFile == profileDirectory.canonicalFile) return emptyList()
        return profileDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && (file.name == PROFILE_REGISTRY_FILE || file.name.matches(PROFILE_ARCHIVE_PATTERN))
            }
            .sortedBy(File::getName)
            .map { file -> "$PROFILE_ENTRY_PREFIX/${file.name}" to file }
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

                        entry.name.startsWith(ASSET_ENTRY_PREFIX) || entry.name.startsWith(PROFILE_ENTRY_PREFIX) -> {
                            require(isSafeStoredEntry(entry.name)) { "Unsafe backup entry" }
                            val destination = File(stageDirectory, entry.name)
                            require(destination.canonicalPath.startsWith(stageDirectory.canonicalPath + File.separator))
                            destination.parentFile?.mkdirs()
                            destination.outputStream().buffered().use { output ->
                                val maximum = if (entry.name.startsWith(PROFILE_ENTRY_PREFIX)) {
                                    MAX_ARCHIVE_BYTES
                                } else {
                                    MAX_ASSET_BYTES
                                }
                                copyLimited(input, output, maximum, budget)
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
            val expectedEntries = expectedArchiveEntries(document)
            require(extractedEntries - METADATA_ENTRY == expectedEntries) { "Backup assets do not match metadata" }
            expectedEntries.filter { it.startsWith(ASSET_ENTRY_PREFIX) }
                .forEach { entryName -> validateBackupImage(File(stageDirectory, entryName)) }
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

    private fun installProfiles(stageDirectory: File, document: BackupDocument) {
        if (document.profileEntries.isEmpty()) return
        installProfileDirectory(File(stageDirectory, PROFILE_ENTRY_PREFIX))
    }

    private fun installProfileDirectory(source: File) {
        profileDirectory.deleteRecursively()
        copyDirectoryIfPresent(source, profileDirectory)
    }

    private fun expectedArchiveEntries(document: BackupDocument): Set<String> = buildSet {
        if (document.wallpaper.main == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.MAIN))
        if (document.wallpaper.day == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.DAY))
        if (document.wallpaper.night == CUSTOM_WALLPAPER) add(wallpaperEntry(WallpaperTarget.NIGHT))
        document.appPreferences
            .filter(AppPreferenceBackup::hasCustomBanner)
            .map { it.componentName.substringBefore('/') }
            .distinct()
            .forEach { add(bannerEntry(it)) }
        addAll(document.profileEntries)
    }

    private fun isManagedBackup(file: File): Boolean {
        val parent = file.parentFile?.canonicalFile
        return parent == backupDirectory.canonicalFile || parent == safetyBackupDirectory.canonicalFile
    }

    private fun isProfileStateFile(file: File): Boolean =
        file.parentFile?.canonicalFile == profileDirectory.canonicalFile &&
            file.name.matches(Regex("[a-f0-9-]+\\.vesperprofile"))

    private fun bannerFile(packageName: String): File = File(bannerDirectory, "$packageName.image")

    private fun bannerEntry(packageName: String): String = "$ASSET_ENTRY_PREFIX/banners/$packageName.image"

    private fun wallpaperEntry(target: WallpaperTarget): String =
        "$ASSET_ENTRY_PREFIX/wallpapers/${target.name.lowercase(Locale.US)}.image"

    private fun isSafeStoredEntry(entryName: String): Boolean =
        entryName.matches(Regex("assets/(banners/[A-Za-z0-9_.]+|wallpapers/(main|day|night))\\.image")) ||
            entryName == "$PROFILE_ENTRY_PREFIX/$PROFILE_REGISTRY_FILE" ||
            entryName.matches(Regex("profiles/[a-f0-9-]+\\.vesperprofile"))

    private data class PreparedBackup(
        val document: BackupDocument,
        val snapshot: BackupSnapshot,
        val stageDirectory: File
    )
}
