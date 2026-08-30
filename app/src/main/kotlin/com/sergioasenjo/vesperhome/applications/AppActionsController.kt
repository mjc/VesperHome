package com.sergioasenjo.vesperhome.applications

import android.content.ActivityNotFoundException
import android.text.InputFilter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.categories.LauncherCategory

class AppActionsController(
    private val activity: AppCompatActivity,
    private val categories: () -> List<LauncherCategory>,
    private val isReorderable: (AppAdapter) -> Boolean,
    private val onFavoriteChanged: (LauncherApp) -> Unit,
    private val onHidden: (LauncherApp) -> Unit,
    private val onNameChanged: (LauncherApp, String?) -> Unit,
    private val onApplicationDetails: (LauncherApp) -> Unit,
    private val onUninstall: (LauncherApp) -> Unit,
    private val onCategoryMembershipChanged: (LauncherCategory, LauncherApp, Boolean) -> Unit,
    private val onCustomBannerSelected: (LauncherApp, android.net.Uri) -> Unit,
    private val onCustomBannerRemoved: (LauncherApp) -> Unit,
    private val isPinLocked: (LauncherApp) -> Boolean,
    private val onPinLockChanged: (LauncherApp) -> Unit,
    private val onReorder: (AppAdapter, LauncherApp) -> Unit,
    private val onPickerUnavailable: () -> Unit
) {
    private var pendingBannerApp: LauncherApp? = null
    private val bannerPicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val app = pendingBannerApp
        pendingBannerApp = null
        if (app != null && uri != null) onCustomBannerSelected(app, uri)
    }

    fun show(app: LauncherApp, adapter: AppAdapter) {
        val favoriteAction = if (app.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
        val actions = mutableListOf(activity.getString(favoriteAction) to { onFavoriteChanged(app) })
        actions += activity.getString(R.string.rename_application) to { showRenameDialog(app) }
        if (app.customName != null) {
            actions += activity.getString(R.string.reset_application_name) to { onNameChanged(app, null) }
        }
        actions += activity.getString(R.string.hide_app) to { onHidden(app) }
        actions += activity.getString(R.string.choose_custom_banner) to { openBannerPicker(app) }
        if (app.customBannerFile != null) {
            actions += activity.getString(R.string.remove_custom_banner) to { onCustomBannerRemoved(app) }
        }
        actions += activity.getString(R.string.application_info) to { onApplicationDetails(app) }
        actions += activity.getString(R.string.uninstall_application) to { onUninstall(app) }
        actions += activity.getString(
            if (isPinLocked(app)) R.string.unlock_application else R.string.lock_application
        ) to { onPinLockChanged(app) }
        if (isReorderable(adapter)) {
            actions += activity.getString(R.string.reorder_application) to { onReorder(adapter, app) }
        }
        categories().forEach { category ->
            val included = category.apps.any { it.packageName == app.packageName }
            val label = if (included) {
                activity.getString(R.string.remove_from_category, category.name)
            } else {
                activity.getString(R.string.add_to_category, category.name)
            }
            actions += label to { onCategoryMembershipChanged(category, app, !included) }
        }

        AlertDialog.Builder(activity)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, action -> actions[action].second() }
            .show()
    }

    private fun showRenameDialog(app: LauncherApp) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.application_name)
            filters = arrayOf(InputFilter.LengthFilter(MAX_APP_NAME_LENGTH))
            setSingleLine()
            setText(app.label)
            selectAll()
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.rename_application)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(activity, R.string.application_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    onNameChanged(app, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openBannerPicker(app: LauncherApp) {
        pendingBannerApp = app
        try {
            bannerPicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            pendingBannerApp = null
            onPickerUnavailable()
        }
    }

    private companion object {
        const val MAX_APP_NAME_LENGTH = 100
    }
}
