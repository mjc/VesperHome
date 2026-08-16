package com.sergioasenjo.vesperhome.media

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ActivityMediaSearchBinding
import com.sergioasenjo.vesperhome.music.JellyfinItemLauncher
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import com.sergioasenjo.vesperhome.settings.ManagementScreenAppearanceRenderer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MediaSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaSearchBinding
    private lateinit var addForm: MediaAddFormController
    private lateinit var adapter: MediaSearchAdapter
    private lateinit var jellyfinItemLauncher: JellyfinItemLauncher
    private var appearance = LauncherAppearance()
    private var detailsVisible = false
    private var resultKey = ""
    private val viewModel: MediaSearchViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        MediaSearchViewModel.factory(container.mediaSearchRepository, container.trackedMediaRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val container = (application as VesperHomeApplication).container
        val appearanceRenderer = ManagementScreenAppearanceRenderer(
            binding.root,
            binding.wallpaper,
            binding.workspace,
            listOf(binding.workspace)
        )
        jellyfinItemLauncher = JellyfinItemLauncher(this)
        adapter = MediaSearchAdapter(::selectItem)
        binding.results.apply {
            layoutManager = GridLayoutManager(this@MediaSearchActivity, GRID_COLUMNS)
            adapter = this@MediaSearchActivity.adapter
            itemAnimator = null
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        updateScrollHint()
                    }
                }
            )
        }
        addForm = MediaAddFormController(this, binding.addPage, viewModel)
        bindActions()
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.uiState.value.selected != null) viewModel.closeDetails() else finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        container.launcherSettingsRepository.appearance,
                        container.wallpaperRepository.state
                    ) { appearance, wallpaper -> appearance to wallpaper }.collect { (appearance, wallpaper) ->
                        this@MediaSearchActivity.appearance = appearanceRenderer.render(appearance, wallpaper)
                        adapter.setAppearance(this@MediaSearchActivity.appearance)
                        renderFilters(viewModel.uiState.value)
                    }
                }
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.messages.collect { message ->
                        Toast.makeText(this@MediaSearchActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.query.post { binding.query.requestFocus() }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setForeground(true)
    }

    override fun onStop() {
        viewModel.setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        addForm.recycle()
        super.onDestroy()
    }

    private fun bindActions() {
        binding.close.setOnClickListener { finish() }
        binding.filterAll.setOnClickListener { viewModel.setFilter(MediaSearchFilter.ALL) }
        binding.filterSeries.setOnClickListener { viewModel.setFilter(MediaSearchFilter.SERIES) }
        binding.filterMovies.setOnClickListener { viewModel.setFilter(MediaSearchFilter.MOVIES) }
        binding.filterTracked.setOnClickListener { viewModel.setFilter(MediaSearchFilter.TRACKED) }
        binding.retry.setOnClickListener { viewModel.retry() }
        binding.query.doAfterTextChanged { text -> viewModel.setQuery(text?.toString().orEmpty()) }
        binding.query.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            binding.query.clearFocus()
            binding.results.post {
                binding.results.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
            true
        }
    }

    private fun selectItem(item: MediaCardItem) {
        if (item !is TrackedMedia) {
            viewModel.select(item)
            return
        }
        val container = (application as VesperHomeApplication).container
        jellyfinItemLauncher.open {
            val credentials = container.jellyfinPreferencesRepository.credentials.first() ?: return@open null
            container.jellyfinApiRepository.itemIdByProvider(
                credentials,
                if (item.provider == MediaProvider.SONARR) "tvdb" else "tmdb",
                item.externalId,
                if (item.provider == MediaProvider.SONARR) "Series" else "Movie"
            )
        }
    }

    private fun render(state: MediaSearchUiState) {
        val showingDetails = state.selected != null
        binding.searchHeader.isVisible = !showingDetails
        binding.searchControls.isVisible = !showingDetails
        binding.resultsPage.isVisible = !showingDetails
        addForm.render(state)
        if (showingDetails) {
            if (!detailsVisible) binding.addPage.back.post { binding.addPage.back.requestFocus() }
            detailsVisible = true
            return
        }
        detailsVisible = false
        renderFilters(state)
        binding.resultsProgress.isVisible = state.loading
        val newResultKey = "${state.filter}:${state.query}"
        val resetScroll = resultKey != newResultKey
        resultKey = newResultKey
        adapter.submitList(state.items) {
            if (resetScroll) binding.results.scrollToPosition(0)
            binding.results.post(::updateScrollHint)
        }
        val message = when {
            state.loading -> null

            state.errorRes != null -> getString(state.errorRes)

            state.filter == MediaSearchFilter.TRACKED && state.items.isEmpty() -> getString(R.string.media_no_tracked)

            state.filter != MediaSearchFilter.TRACKED && state.query.trim().length < MIN_QUERY_LENGTH ->
                getString(R.string.media_query_prompt)

            state.items.isEmpty() -> getString(R.string.media_no_results)

            else -> null
        }
        binding.messageContainer.isVisible = message != null
        binding.message.text = message.orEmpty()
        binding.retry.isVisible = state.errorRes != null
    }

    private fun updateScrollHint() {
        val show = binding.resultsPage.isVisible &&
            adapter.itemCount > GRID_COLUMNS &&
            binding.results.canScrollVertically(1)
        binding.scrollHint.isVisible = show
        if (show) binding.scrollHint.text = getString(R.string.media_more_results_below, adapter.itemCount)
    }

    private fun renderFilters(state: MediaSearchUiState) {
        binding.filterSeries.isVisible = MediaProvider.SONARR in state.providers
        binding.filterMovies.isVisible = MediaProvider.RADARR in state.providers
        val buttons = mapOf(
            MediaSearchFilter.ALL to binding.filterAll,
            MediaSearchFilter.SERIES to binding.filterSeries,
            MediaSearchFilter.MOVIES to binding.filterMovies,
            MediaSearchFilter.TRACKED to binding.filterTracked
        )
        buttons.forEach { (filter, button) -> styleFilter(button, filter == state.filter) }
    }

    private fun styleFilter(button: MaterialButton, selected: Boolean) {
        val palette = appearance.palette
        button.strokeWidth = if (selected) dp(2) else 0
        button.strokeColor = ColorStateList.valueOf(palette.focus)
        button.isSelected = selected
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val GRID_COLUMNS = 5
        const val MIN_QUERY_LENGTH = 2
    }
}
