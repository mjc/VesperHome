package com.sergioasenjo.vesperhome.launcher

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.applications.ApplicationSortMode

internal fun showApplicationSortDialog(
    activity: AppCompatActivity,
    current: ApplicationSortMode,
    onSelected: (ApplicationSortMode) -> Unit
) {
    val modes = ApplicationSortMode.entries
    AlertDialog.Builder(activity)
        .setTitle(R.string.sort_applications)
        .setSingleChoiceItems(
            modes.map { activity.getString(it.labelRes) }.toTypedArray(),
            modes.indexOf(current)
        ) { dialog, selection ->
            onSelected(modes[selection])
            dialog.dismiss()
        }
        .show()
}
