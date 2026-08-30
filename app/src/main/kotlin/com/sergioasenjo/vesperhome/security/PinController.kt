package com.sergioasenjo.vesperhome.security

import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.LauncherApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PinController(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val repository: PinRepository
) {
    private var failedAttempts = 0
    private var blockedUntil = 0L

    fun showSettings() {
        scope.launch {
            val state = repository.current()
            if (state.hasPin) {
                requestPin(R.string.enter_pin) { showConfiguredSettings() }
            } else {
                requestNewPin { showConfiguredSettings() }
            }
        }
    }

    fun authorize(area: PinProtectedArea, onAuthorized: () -> Unit) {
        scope.launch {
            if (repository.current().protects(area)) requestPin(R.string.enter_pin, onAuthorized) else onAuthorized()
        }
    }

    fun authorizeApp(app: LauncherApp, onAuthorized: () -> Unit) {
        scope.launch {
            val state = repository.current()
            if (state.hasPin && app.packageName in state.lockedPackages) {
                requestPin(R.string.enter_pin, onAuthorized)
            } else {
                onAuthorized()
            }
        }
    }

    fun authorizeAppManagement(onAuthorized: () -> Unit) {
        authorize(PinProtectedArea.APP_MANAGEMENT, onAuthorized)
    }

    fun isAppLocked(app: LauncherApp): Boolean = app.packageName in repository.state.value.lockedPackages

    fun toggleAppLock(app: LauncherApp) {
        scope.launch {
            val state = repository.current()
            if (!state.hasPin) {
                requestNewPin { toggleAppLockAfterAuthorization(app) }
            } else {
                requestPin(R.string.enter_pin) { toggleAppLockAfterAuthorization(app) }
            }
        }
    }

    private fun toggleAppLockAfterAuthorization(app: LauncherApp) {
        scope.launch {
            repository.toggleAppLock(app.packageName)
            showMessage(
                if (app.packageName in repository.current().lockedPackages) {
                    R.string.application_locked
                } else {
                    R.string.application_unlocked
                }
            )
        }
    }

    private suspend fun showConfiguredSettings() {
        val state = repository.current()
        val actions = listOf(
            activity.getString(R.string.change_pin),
            protectionLabel(R.string.protect_media_integrations, state.protectMediaIntegrations),
            protectionLabel(R.string.protect_app_management, state.protectAppManagement),
            protectionLabel(R.string.protect_backups_profiles, state.protectBackupsAndProfiles),
            activity.getString(R.string.remove_pin)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.pin_and_security)
            .setItems(actions.toTypedArray()) { _, index ->
                when (index) {
                    0 -> requestNewPin { showMessage(R.string.pin_changed) }
                    1 -> toggleProtection(PinProtectedArea.MEDIA_INTEGRATIONS, !state.protectMediaIntegrations)
                    2 -> toggleProtection(PinProtectedArea.APP_MANAGEMENT, !state.protectAppManagement)
                    3 -> toggleProtection(PinProtectedArea.BACKUPS_AND_PROFILES, !state.protectBackupsAndProfiles)
                    4 -> confirmRemovePin()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleProtection(area: PinProtectedArea, enabled: Boolean) {
        scope.launch {
            repository.setProtection(area, enabled)
            showConfiguredSettings()
        }
    }

    private fun confirmRemovePin() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.remove_pin)
            .setMessage(R.string.remove_pin_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove_pin) { _, _ ->
                scope.launch {
                    repository.clearPin()
                    showMessage(R.string.pin_removed)
                }
            }
            .show()
    }

    private fun requestNewPin(onCreated: suspend () -> Unit) {
        requestPinInput(R.string.set_pin) { firstPin ->
            requestPinInput(R.string.confirm_pin) { confirmation ->
                if (!firstPin.contentEquals(confirmation)) {
                    firstPin.fill('\u0000')
                    confirmation.fill('\u0000')
                    showMessage(R.string.pin_mismatch)
                    return@requestPinInput
                }
                scope.launch {
                    try {
                        repository.setPin(firstPin)
                        confirmation.fill('\u0000')
                        onCreated()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        showMessage(R.string.pin_operation_failed)
                    }
                }
            }
        }
    }

    private fun requestPin(titleRes: Int, onVerified: suspend () -> Unit) {
        val remaining = blockedUntil - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            Toast.makeText(
                activity,
                activity.getString(R.string.pin_try_again_later, (remaining / 1_000L) + 1L),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        requestPinInput(titleRes) { pin ->
            scope.launch {
                if (repository.verify(pin)) {
                    failedAttempts = 0
                    onVerified()
                } else {
                    failedAttempts += 1
                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                        failedAttempts = 0
                        blockedUntil = SystemClock.elapsedRealtime() + BLOCK_DURATION_MS
                        showMessage(R.string.pin_temporarily_blocked)
                    } else {
                        showMessage(R.string.incorrect_pin)
                    }
                }
            }
        }
    }

    private fun requestPinInput(titleRes: Int, onAccepted: (CharArray) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(MAX_PIN_LENGTH))
            setSingleLine()
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pin = input.text.toString().toCharArray()
                input.text?.clear()
                if (pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
                    onAccepted(pin)
                } else {
                    pin.fill('\u0000')
                    showMessage(R.string.pin_length_error)
                }
            }
            .show()
    }

    private fun protectionLabel(labelRes: Int, enabled: Boolean): String = activity.getString(
        R.string.pin_protection_value,
        activity.getString(labelRes),
        activity.getString(if (enabled) R.string.setting_on else R.string.setting_off)
    )

    private fun showMessage(messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MAX_FAILED_ATTEMPTS = 5
        const val BLOCK_DURATION_MS = 30_000L
    }
}
