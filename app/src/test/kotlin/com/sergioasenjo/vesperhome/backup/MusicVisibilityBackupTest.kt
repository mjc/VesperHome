package com.sergioasenjo.vesperhome.backup

import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.wallpaper.WallpaperSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicVisibilityBackupTest {
    @Test
    fun neutralMusicSettingKeepsLegacyBackupKeyAndRestoresHiddenSection() {
        val snapshot = BackupSnapshot(
            LauncherSettings(showMusic = false),
            WallpaperSettings(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList()
        )
        val encoded = Json.encodeToString(snapshot.toDocument(createdAt = 1L))
        assertTrue(encoded.contains("\"showMusic\":false"))
        assertFalse(encoded.contains("\"showMusic\""))
        val restored = Json.decodeFromString<BackupDocument>(encoded).toSnapshot()
        assertFalse(restored.settings.showMusic)
    }
}
