package com.sergioasenjo.vesperhome.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sergioasenjo.vesperhome.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaSearchUiState(
    val providers: Set<MediaProvider> = emptySet(),
    val filter: MediaSearchFilter = MediaSearchFilter.ALL,
    val query: String = "",
    val items: List<MediaCardItem> = emptyList(),
    val trackedItems: List<TrackedMedia> = emptyList(),
    val loading: Boolean = false,
    val selected: MediaSearchResult? = null,
    val choices: MediaAddChoices? = null,
    val selection: MediaAddSelection? = null,
    val loadingOptions: Boolean = false,
    val adding: Boolean = false,
    val errorRes: Int? = null
)

class MediaSearchViewModel(
    private val searchRepository: MediaSearchRepository,
    private val trackedRepository: TrackedMediaRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MediaSearchUiState())
    val uiState = mutableUiState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<Int>()
    val messages = mutableMessages.asSharedFlow()
    private var searchJob: Job? = null
    private var trackingJob: Job? = null
    private var foreground = false

    init {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(providers = searchRepository.connectedProviders())
        }
        viewModelScope.launch {
            trackedRepository.items.collect { tracked ->
                val state = mutableUiState.value
                mutableUiState.value = state.copy(
                    trackedItems = tracked,
                    items = if (state.filter == MediaSearchFilter.TRACKED) tracked else state.items
                )
            }
        }
    }

    fun setQuery(query: String) {
        mutableUiState.value = mutableUiState.value.copy(query = query, errorRes = null)
        if (mutableUiState.value.filter != MediaSearchFilter.TRACKED) scheduleSearch()
    }

    fun setFilter(filter: MediaSearchFilter) {
        val state = mutableUiState.value
        if (state.filter == filter) return
        searchJob?.cancel()
        mutableUiState.value = state.copy(
            filter = filter,
            items = if (filter == MediaSearchFilter.TRACKED) state.trackedItems else emptyList(),
            loading = false,
            errorRes = null
        )
        updateTracking()
        if (filter != MediaSearchFilter.TRACKED) scheduleSearch(immediate = true)
    }

    fun select(item: MediaCardItem) {
        val result = item as? MediaSearchResult ?: return
        if (result.alreadyAdded) {
            viewModelScope.launch { mutableMessages.emit(R.string.media_already_added) }
            return
        }
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                selected = result,
                choices = null,
                selection = null,
                loadingOptions = true,
                errorRes = null
            )
            try {
                val choices = searchRepository.loadAddChoices(result.provider)
                mutableUiState.value = mutableUiState.value.copy(
                    choices = choices,
                    selection = choices.selection,
                    loadingOptions = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    loadingOptions = false,
                    selected = null
                )
                mutableMessages.emit(R.string.media_options_failed)
            }
        }
    }

    fun updateSelection(update: (MediaAddSelection) -> MediaAddSelection) {
        mutableUiState.value.selection?.let { selection ->
            mutableUiState.value = mutableUiState.value.copy(selection = update(selection))
        }
    }

    fun addSelected() {
        val state = mutableUiState.value
        val selected = state.selected ?: return
        val choices = state.choices ?: return
        val selection = state.selection ?: return
        if (state.adding) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(adding = true, errorRes = null)
            try {
                searchRepository.add(selected, choices, selection)
                val tracked = mutableUiState.value.trackedItems
                mutableUiState.value = mutableUiState.value.copy(
                    filter = MediaSearchFilter.TRACKED,
                    items = tracked,
                    selected = null,
                    choices = null,
                    selection = null,
                    adding = false
                )
                mutableMessages.emit(R.string.media_added)
                updateTracking()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(adding = false)
                mutableMessages.emit(R.string.media_add_failed)
            }
        }
    }

    fun closeDetails() {
        mutableUiState.value = mutableUiState.value.copy(
            selected = null,
            choices = null,
            selection = null,
            loadingOptions = false,
            adding = false,
            errorRes = null
        )
    }

    fun retry() {
        if (mutableUiState.value.selected != null) {
            mutableUiState.value.selected?.let(::select)
        } else {
            scheduleSearch(immediate = true)
        }
    }

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        updateTracking()
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val state = mutableUiState.value
        if (state.query.trim().length < MIN_QUERY_LENGTH) {
            mutableUiState.value = state.copy(items = emptyList(), loading = false, errorRes = null)
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(SEARCH_DEBOUNCE_MS)
            mutableUiState.value = mutableUiState.value.copy(loading = true, errorRes = null)
            try {
                val current = mutableUiState.value
                val results = searchRepository.search(current.query.trim(), current.filter)
                mutableUiState.value = mutableUiState.value.copy(items = results, loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    items = emptyList(),
                    loading = false,
                    errorRes = R.string.media_search_failed
                )
            }
        }
    }

    private fun updateTracking() {
        trackingJob?.cancel()
        if (!foreground || mutableUiState.value.filter != MediaSearchFilter.TRACKED) return
        trackingJob = viewModelScope.launch {
            while (true) {
                trackedRepository.refresh()
                delay(TRACKING_REFRESH_MS)
            }
        }
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_DEBOUNCE_MS = 450L
        private const val TRACKING_REFRESH_MS = 10_000L

        fun factory(
            searchRepository: MediaSearchRepository,
            trackedRepository: TrackedMediaRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { MediaSearchViewModel(searchRepository, trackedRepository) }
        }
    }
}
