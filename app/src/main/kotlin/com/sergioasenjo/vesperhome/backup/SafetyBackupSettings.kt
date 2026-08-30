package com.sergioasenjo.vesperhome.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sergioasenjo.vesperhome.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.safetyBackupDataStore by preferencesDataStore(name = "safety_backup_settings")

enum class SafetyBackupFrequency(val intervalDays: Long) {
    OFF(0),
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30)
}

internal val SafetyBackupFrequency.labelRes: Int
    get() = when (this) {
        SafetyBackupFrequency.OFF -> R.string.safety_backup_off
        SafetyBackupFrequency.DAILY -> R.string.safety_backup_daily
        SafetyBackupFrequency.WEEKLY -> R.string.safety_backup_weekly
        SafetyBackupFrequency.MONTHLY -> R.string.safety_backup_monthly
    }

class SafetyBackupSettingsRepository(private val context: Context) {
    val frequency: Flow<SafetyBackupFrequency> = context.safetyBackupDataStore.data.map { preferences ->
        preferences[FREQUENCY]
            ?.let { stored -> SafetyBackupFrequency.entries.firstOrNull { it.name == stored } }
            ?: SafetyBackupFrequency.OFF
    }

    suspend fun setFrequency(frequency: SafetyBackupFrequency) {
        context.safetyBackupDataStore.edit { preferences -> preferences[FREQUENCY] = frequency.name }
        SafetyBackupScheduler(context).schedule(frequency)
    }

    private companion object {
        val FREQUENCY = stringPreferencesKey("frequency")
    }
}

class SafetyBackupScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    fun schedule(frequency: SafetyBackupFrequency) {
        if (frequency == SafetyBackupFrequency.OFF) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SafetyBackupWorker>(frequency.intervalDays, TimeUnit.DAYS)
            .setInitialDelay(frequency.intervalDays, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "vesper-safety-backup"
    }
}
