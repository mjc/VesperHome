package com.sergioasenjo.vesperhome.profiles

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.widget.EditText
import android.widget.Toast
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.LauncherSettings
import com.sergioasenjo.vesperhome.settings.LauncherSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProfileController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val repository: ProfileRepository,
    private val settingsRepository: LauncherSettingsRepository,
    private val currentSettings: () -> LauncherSettings,
    private val currentAppearance: () -> LauncherAppearance,
    onDismissed: () -> Unit
) {
    private var state = ProfileState()
    private var busy = false
    private val panelDelegate = lazy {
        ProfilePanel(
            activity,
            onCreate = { showNameDialog(R.string.create_profile, ::createProfile) },
            onToggleComingNext = ::toggleComingNext,
            onToggleMusic = ::toggleMusic,
            onSelected = ::showActions,
            onDismissed = onDismissed
        )
    }
    private val panel by panelDelegate

    fun show() {
        panel.show(state, currentSettings(), busy = true, currentAppearance())
        runOperation(successMessage = null) { repository.ensureInitialized() }
    }

    fun render(settings: LauncherSettings, appearance: LauncherAppearance) {
        state = repository.state.value
        if (panelDelegate.isInitialized() && panel.isShowing) panel.render(state, settings, busy, appearance)
    }

    fun release() {
        if (panelDelegate.isInitialized()) panel.release()
    }

    private fun createProfile(name: String) {
        runOperation(R.string.profile_created) { repository.create(name) }
    }

    private fun showActions(profile: LayoutProfile) {
        val active = profile.id == state.activeProfileId
        val labels = buildList {
            if (!active) add(activity.getString(R.string.switch_profile))
            add(activity.getString(R.string.rename_profile))
            if (!active && state.profiles.size > 1) add(activity.getString(R.string.delete_profile))
        }
        AlertDialog.Builder(activity)
            .setTitle(profile.name)
            .setItems(labels.toTypedArray()) { _, index ->
                var current = 0
                if (!active) {
                    if (index == current) {
                        switchProfile(profile)
                        return@setItems
                    }
                    current += 1
                }
                if (index == current) {
                    showNameDialog(R.string.rename_profile) { name -> renameProfile(profile, name) }
                } else {
                    confirmDelete(profile)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun switchProfile(profile: LayoutProfile) {
        runOperation(R.string.profile_switched) { repository.switchTo(profile.id) }
    }

    private fun renameProfile(profile: LayoutProfile, name: String) {
        runOperation(R.string.profile_renamed) { repository.rename(profile.id, name) }
    }

    private fun confirmDelete(profile: LayoutProfile) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.delete_profile)
            .setMessage(activity.getString(R.string.delete_profile_confirmation, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_profile) { _, _ ->
                runOperation(R.string.profile_deleted) { repository.delete(profile.id) }
            }
            .show()
    }

    private fun toggleComingNext() {
        runOperation(successMessage = null) {
            settingsRepository.setShowComingNext(!currentSettings().showComingNext)
        }
    }

    private fun toggleMusic() {
        runOperation(successMessage = null) {
            settingsRepository.setShowMusic(!currentSettings().showMusic)
        }
    }

    private fun showNameDialog(titleRes: Int, onAccepted: (String) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.profile_name)
            filters = arrayOf(InputFilter.LengthFilter(MAX_PROFILE_NAME_LENGTH))
            setSingleLine()
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onAccepted(input.text.toString()) }
            .show()
    }

    private fun runOperation(successMessage: Int?, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        render(currentSettings(), currentAppearance())
        scope.launch {
            try {
                operation()
                state = repository.state.value
                successMessage?.let(::showMessage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(R.string.profile_operation_failed)
            } finally {
                busy = false
                render(currentSettings(), currentAppearance())
            }
        }
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_PROFILE_NAME_LENGTH = 40
    }
}
