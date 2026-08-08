package com.sergioasenjo.ltvlauncher.applications

import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.categories.LauncherCategory

class AppActionsController(
    private val activity: AppCompatActivity,
    private val categories: () -> List<LauncherCategory>,
    private val isReorderable: (AppAdapter) -> Boolean,
    private val onFavoriteChanged: (LauncherApp) -> Unit,
    private val onHidden: (LauncherApp) -> Unit,
    private val onApplicationDetails: (LauncherApp) -> Unit,
    private val onUninstall: (LauncherApp) -> Unit,
    private val onCategoryMembershipChanged: (LauncherCategory, LauncherApp, Boolean) -> Unit,
    private val onCustomBannerSelected: (LauncherApp, android.net.Uri) -> Unit,
    private val onCustomBannerRemoved: (LauncherApp) -> Unit,
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
        val actions = mutableListOf(
            activity.getString(favoriteAction) to { onFavoriteChanged(app) },
            activity.getString(R.string.hide_app) to { onHidden(app) },
            activity.getString(R.string.choose_custom_banner) to { openBannerPicker(app) },
            activity.getString(R.string.application_info) to { onApplicationDetails(app) },
            activity.getString(R.string.uninstall_application) to { onUninstall(app) }
        )
        if (app.customBannerFile != null) {
            actions.add(3, activity.getString(R.string.remove_custom_banner) to { onCustomBannerRemoved(app) })
        }
        if (isReorderable(adapter)) {
            actions += activity.getString(R.string.reorder_application) to { adapter.startMoving(app) }
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

    private fun openBannerPicker(app: LauncherApp) {
        pendingBannerApp = app
        try {
            bannerPicker.launch(arrayOf("image/*"))
        } catch (_: ActivityNotFoundException) {
            pendingBannerApp = null
            onPickerUnavailable()
        }
    }
}
