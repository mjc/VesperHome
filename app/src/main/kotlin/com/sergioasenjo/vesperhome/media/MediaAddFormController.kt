package com.sergioasenjo.vesperhome.media

import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ViewMediaAddBinding

class MediaAddFormController(
    private val activity: AppCompatActivity,
    private val binding: ViewMediaAddBinding,
    private val viewModel: MediaSearchViewModel
) {
    private var state = MediaSearchUiState()
    private var rendering = false

    init {
        binding.back.setOnClickListener { viewModel.closeDetails() }
        binding.rootFolder.setOnClickListener { showRootFolders() }
        binding.monitor.setOnClickListener { showMonitorModes() }
        binding.qualityProfile.setOnClickListener { showQualityProfiles() }
        binding.seriesType.setOnClickListener { showSeriesTypes() }
        binding.minimumAvailability.setOnClickListener { showMinimumAvailability() }
        binding.tags.setOnClickListener { showTags() }
        binding.seasonFolder.setOnCheckedChangeListener { _, checked ->
            if (!rendering) viewModel.updateSelection { it.copy(seasonFolder = checked) }
        }
        binding.searchMissing.setOnCheckedChangeListener { _, checked ->
            if (!rendering) viewModel.updateSelection { it.copy(searchMissing = checked) }
        }
        binding.searchCutoff.setOnCheckedChangeListener { _, checked ->
            if (!rendering) viewModel.updateSelection { it.copy(searchCutoffUnmet = checked) }
        }
        binding.add.setOnClickListener { viewModel.addSelected() }
    }

    fun render(state: MediaSearchUiState) {
        this.state = state
        val selected = state.selected
        binding.root.isVisible = selected != null
        if (selected == null) return
        val fallback = if (selected.provider == MediaProvider.SONARR) R.drawable.ic_tv_series else R.drawable.ic_movie
        binding.poster.load(selected.posterUrl) {
            placeholder(fallback)
            error(fallback)
        }
        binding.title.text = activity.getString(R.string.media_add_title, selected.title, selected.year)
        binding.provider.text = activity.getString(
            if (selected.provider == MediaProvider.SONARR) R.string.sonarr else R.string.radarr
        )
        binding.overview.text = selected.overview
        binding.addProgress.isVisible = state.loadingOptions || state.adding
        binding.form.isVisible = state.choices != null
        binding.add.isVisible = state.choices != null
        binding.add.isEnabled = !state.adding
        val choices = state.choices ?: return
        val selection = state.selection ?: return
        rendering = true
        binding.seriesOptions.isVisible = selected.provider == MediaProvider.SONARR
        binding.movieOptions.isVisible = selected.provider == MediaProvider.RADARR
        binding.searchCutoff.isVisible = selected.provider == MediaProvider.SONARR
        binding.searchMissing.text = activity.getString(
            if (selected.provider == MediaProvider.SONARR) {
                R.string.media_search_missing_episodes
            } else {
                R.string.media_search_missing_movie
            }
        )
        binding.rootFolder.text = choices.rootFolders.firstOrNull { it.id == selection.rootFolderId }?.let(::rootLabel)
        binding.qualityProfile.text = choices.qualityProfiles.firstOrNull {
            it.id == selection.qualityProfileId
        }?.name.orEmpty()
        binding.monitor.text = monitorLabel(selected.provider, selection.monitor)
        binding.seriesType.text = activity.getString(selection.seriesType.labelRes)
        binding.minimumAvailability.text = activity.getString(selection.minimumAvailability.labelRes)
        binding.tags.text = choices.tags.filter { it.id in selection.tagIds }
            .joinToString { it.label }
            .ifBlank { activity.getString(R.string.media_none) }
        binding.tags.isEnabled = choices.tags.isNotEmpty()
        binding.seasonFolder.isChecked = selection.seasonFolder
        binding.searchMissing.isChecked = selection.searchMissing
        binding.searchCutoff.isChecked = selection.searchCutoffUnmet
        rendering = false
    }

    fun recycle() {
        binding.poster.load(null)
    }

    private fun showRootFolders() {
        val choices = state.choices ?: return
        val selection = state.selection ?: return
        showChoice(
            R.string.media_root_folder,
            choices.rootFolders.map(::rootLabel),
            choices.rootFolders.indexOfFirst { it.id == selection.rootFolderId }
        ) { index ->
            viewModel.updateSelection { it.copy(rootFolderId = choices.rootFolders[index].id) }
        }
    }

    private fun showQualityProfiles() {
        val choices = state.choices ?: return
        val selection = state.selection ?: return
        showChoice(
            R.string.media_quality_profile,
            choices.qualityProfiles.map { it.name },
            choices.qualityProfiles.indexOfFirst { it.id == selection.qualityProfileId }
        ) { index ->
            viewModel.updateSelection { it.copy(qualityProfileId = choices.qualityProfiles[index].id) }
        }
    }

    private fun showMonitorModes() {
        val selection = state.selection ?: return
        val provider = state.selected?.provider ?: return
        if (provider == MediaProvider.SONARR) {
            val values = SonarrMonitor.entries
            showChoice(
                R.string.media_monitor,
                values.map { activity.getString(it.labelRes) },
                values.indexOfFirst { it.apiValue == selection.monitor }
            ) { index -> viewModel.updateSelection { it.copy(monitor = values[index].apiValue) } }
        } else {
            val values = RadarrMonitor.entries
            showChoice(
                R.string.media_monitor,
                values.map { activity.getString(it.labelRes) },
                values.indexOfFirst { it.apiValue == selection.monitor }
            ) { index -> viewModel.updateSelection { it.copy(monitor = values[index].apiValue) } }
        }
    }

    private fun showSeriesTypes() {
        val selection = state.selection ?: return
        val values = SeriesType.entries
        showChoice(
            R.string.media_series_type,
            values.map { activity.getString(it.labelRes) },
            values.indexOf(selection.seriesType)
        ) { index -> viewModel.updateSelection { it.copy(seriesType = values[index]) } }
    }

    private fun showMinimumAvailability() {
        val selection = state.selection ?: return
        val values = MinimumAvailability.entries
        showChoice(
            R.string.media_minimum_availability,
            values.map { activity.getString(it.labelRes) },
            values.indexOf(selection.minimumAvailability)
        ) { index -> viewModel.updateSelection { it.copy(minimumAvailability = values[index]) } }
    }

    private fun showTags() {
        val choices = state.choices ?: return
        val selected = state.selection?.tagIds?.toMutableSet() ?: return
        val checked = choices.tags.map { it.id in selected }.toBooleanArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.media_tags)
            .setMultiChoiceItems(choices.tags.map { it.label }.toTypedArray(), checked) { _, index, enabled ->
                if (enabled) selected += choices.tags[index].id else selected -= choices.tags[index].id
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.updateSelection { it.copy(tagIds = selected) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChoice(title: Int, labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, index ->
                onSelected(index)
                dialog.dismiss()
            }
            .show()
    }

    private fun rootLabel(folder: MediaRootFolder): String {
        val free =
            folder.freeSpace?.let { Formatter.formatFileSize(activity, it) } ?: activity.getString(R.string.media_none)
        return activity.getString(R.string.media_root_folder_value, folder.path, free)
    }

    private fun monitorLabel(provider: MediaProvider, value: String): String = if (provider == MediaProvider.SONARR) {
        activity.getString(SonarrMonitor.entries.firstOrNull { it.apiValue == value }?.labelRes ?: R.string.media_none)
    } else {
        activity.getString(RadarrMonitor.entries.firstOrNull { it.apiValue == value }?.labelRes ?: R.string.media_none)
    }
}

private val SonarrMonitor.labelRes: Int
    get() = when (this) {
        SonarrMonitor.ALL -> R.string.media_monitor_all
        SonarrMonitor.FUTURE -> R.string.media_monitor_future
        SonarrMonitor.MISSING -> R.string.media_monitor_missing
        SonarrMonitor.EXISTING -> R.string.media_monitor_existing
        SonarrMonitor.FIRST_SEASON -> R.string.media_monitor_first_season
        SonarrMonitor.LAST_SEASON -> R.string.media_monitor_last_season
        SonarrMonitor.PILOT -> R.string.media_monitor_pilot
        SonarrMonitor.RECENT -> R.string.media_monitor_recent
        SonarrMonitor.MONITOR_SPECIALS -> R.string.media_monitor_specials
        SonarrMonitor.UNMONITOR_SPECIALS -> R.string.media_unmonitor_specials
        SonarrMonitor.NONE -> R.string.media_none
        SonarrMonitor.SKIP -> R.string.media_monitor_skip
    }

private val RadarrMonitor.labelRes: Int
    get() = when (this) {
        RadarrMonitor.MOVIE_ONLY -> R.string.media_monitor_movie
        RadarrMonitor.MOVIE_AND_COLLECTION -> R.string.media_monitor_collection
        RadarrMonitor.NONE -> R.string.media_none
    }

private val SeriesType.labelRes: Int
    get() = when (this) {
        SeriesType.STANDARD -> R.string.media_series_standard
        SeriesType.DAILY -> R.string.media_series_daily
        SeriesType.ANIME -> R.string.media_series_anime
    }

private val MinimumAvailability.labelRes: Int
    get() = when (this) {
        MinimumAvailability.TBA -> R.string.media_availability_tba
        MinimumAvailability.ANNOUNCED -> R.string.media_availability_announced
        MinimumAvailability.IN_CINEMAS -> R.string.media_availability_cinemas
        MinimumAvailability.RELEASED -> R.string.media_availability_released
    }
