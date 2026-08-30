package com.sergioasenjo.vesperhome.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sergioasenjo.vesperhome.VesperHomeApplication
import kotlinx.coroutines.CancellationException

class SafetyBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val application = applicationContext as VesperHomeApplication
        application.container.backupRepository.createSafetyBackup(SafetyBackupReason.SCHEDULED)
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        Result.retry()
    }
}
