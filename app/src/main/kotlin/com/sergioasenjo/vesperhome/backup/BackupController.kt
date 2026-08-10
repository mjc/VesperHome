package com.sergioasenjo.vesperhome.backup

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BackupController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val repository: BackupRepository,
    private val currentAppearance: () -> LauncherAppearance,
    private val requestImport: () -> Unit,
    private val onRestored: () -> Unit
) {
    private var entries: List<BackupFileEntry> = emptyList()
    private var busy = false
    private val panel = BackupPanel(
        activity,
        onCreate = ::createBackup,
        onImport = ::openImportPicker,
        onSelected = ::showActions,
        onDismissed = onRestored
    )

    fun show() {
        panel.show(entries, busy = true, currentAppearance())
        refresh()
    }

    fun importBackup(uri: Uri) {
        runOperation(R.string.backup_imported) {
            repository.importBackup(uri)
        }
    }

    fun renderAppearance() {
        if (panel.isShowing) panel.render(entries, busy, currentAppearance())
    }

    fun release() {
        panel.release()
    }

    private fun createBackup() {
        runOperation(R.string.backup_created) { repository.createBackup() }
    }

    private fun openImportPicker() {
        try {
            requestImport()
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.backup_picker_unavailable)
        } catch (_: SecurityException) {
            showMessage(R.string.backup_picker_unavailable)
        }
    }

    private fun refresh() {
        busy = true
        render()
        scope.launch {
            try {
                entries = repository.listBackups()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(R.string.backup_load_failed)
            } finally {
                busy = false
                render()
            }
        }
    }

    private fun showActions(entry: BackupFileEntry) {
        AlertDialog.Builder(activity)
            .setTitle(entry.file.name)
            .setItems(
                arrayOf(
                    activity.getString(R.string.restore_backup),
                    activity.getString(R.string.share_backup),
                    activity.getString(R.string.delete_backup)
                )
            ) { _, index ->
                when (index) {
                    0 -> confirmRestore(entry)
                    1 -> shareBackup(entry)
                    2 -> confirmDelete(entry)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRestore(entry: BackupFileEntry) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.restore_backup)
            .setMessage(R.string.restore_backup_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.restore_backup) { _, _ ->
                runOperation(R.string.backup_restored) {
                    repository.restoreBackup(entry)
                    onRestored()
                }
            }
            .show()
    }

    private fun confirmDelete(entry: BackupFileEntry) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.delete_backup)
            .setMessage(activity.getString(R.string.delete_backup_confirmation, entry.file.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_backup) { _, _ ->
                runOperation(R.string.backup_deleted) {
                    check(repository.deleteBackup(entry))
                }
            }
            .show()
    }

    private fun shareBackup(entry: BackupFileEntry) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                entry.file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = BACKUP_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(activity.contentResolver, entry.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share_backup)))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.backup_share_failed)
        } catch (_: IllegalArgumentException) {
            showMessage(R.string.backup_share_failed)
        } catch (_: SecurityException) {
            showMessage(R.string.backup_share_failed)
        }
    }

    private fun runOperation(successMessage: Int, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        render()
        scope.launch {
            try {
                operation()
                entries = repository.listBackups()
                showMessage(successMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(R.string.backup_operation_failed)
            } finally {
                busy = false
                render()
            }
        }
    }

    private fun render() {
        if (panel.isShowing) panel.render(entries, busy, currentAppearance())
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val BACKUP_MIME_TYPE = "application/zip"
    }
}
