package com.sergioasenjo.ltvlauncher.categories

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.applications.ApplicationSortMode
import com.sergioasenjo.ltvlauncher.databinding.ActivityCategoryManagementBinding
import com.sergioasenjo.ltvlauncher.settings.ManagementScreenAppearanceRenderer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CategoryManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoryManagementBinding
    private val viewModel: CategoryManagementViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        CategoryManagementViewModel.factory(
            container.categoryRepository,
            container.managedApplicationsRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val container = (application as LtvLauncherApplication).container
        val appearanceRenderer = ManagementScreenAppearanceRenderer(
            binding.root,
            binding.wallpaper,
            binding.content,
            listOf(binding.workspace)
        )

        val sectionAdapter = CategoryManagementAdapter(::showSectionActions)
        binding.categories.apply {
            layoutManager = LinearLayoutManager(this@CategoryManagementActivity)
            adapter = sectionAdapter
            itemAnimator = null
        }
        binding.createCategory.setOnClickListener {
            showNameDialog(R.string.create_category, save = viewModel::createCategory)
        }
        binding.createSpacer.setOnClickListener {
            showSpacerHeightDialog(R.string.create_spacer, selectedHeight = 40, save = viewModel::createSpacer)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        container.launcherSettingsRepository.appearance,
                        container.wallpaperRepository.state
                    ) { appearance, wallpaper -> appearance to wallpaper }.collect { (appearance, wallpaper) ->
                        sectionAdapter.setAppearance(appearanceRenderer.render(appearance, wallpaper))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.emptyMessage.isVisible = !state.loading && state.sections.isEmpty()
                        sectionAdapter.submitList(state.sections) {
                            if (state.sections.isNotEmpty() && currentFocus == null) {
                                binding.categories.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            }
                        }
                    }
                }
                launch {
                    viewModel.failures.collect { showMessage(R.string.category_update_failed) }
                }
            }
        }
    }

    private fun showSectionActions(section: SectionSummary) {
        when (section) {
            is CategorySummary -> showCategoryActions(section)
            is SpacerSummary -> showSpacerActions(section)
        }
    }

    private fun showCategoryActions(category: CategorySummary) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.manage_category_apps) to { openMembershipEditor(category) }
        actions += getString(R.string.rename_category) to {
            showNameDialog(R.string.rename_category, category.name) { viewModel.renameCategory(category.id, it) }
        }
        actions += getString(R.string.category_sort, getString(category.sortMode.labelRes)) to {
            showSortDialog(category)
        }
        actions += getString(R.string.category_layout, getString(category.layoutType.labelRes)) to {
            showLayoutDialog(category)
        }
        if (category.layoutType == CategoryLayoutType.GRID) {
            actions += getString(R.string.grid_columns, category.gridColumns) to { showGridColumnsDialog(category) }
        } else {
            actions += getString(R.string.row_height, category.rowHeight) to { showRowHeightDialog(category) }
        }
        actions += getString(R.string.move_section_up) to { viewModel.moveSection(category, -1) }
        actions += getString(R.string.move_section_down) to { viewModel.moveSection(category, 1) }
        actions += getString(R.string.delete_category) to { confirmDelete(category) }
        showActions(category.name, actions)
    }

    private fun showSpacerActions(spacer: SpacerSummary) {
        val actions = listOf(
            getString(R.string.spacer_height, spacer.height) to {
                showSpacerHeightDialog(R.string.spacer_height_title, spacer.height) {
                    viewModel.updateSpacer(spacer.id, it)
                }
            },
            getString(R.string.move_section_up) to { viewModel.moveSection(spacer, -1) },
            getString(R.string.move_section_down) to { viewModel.moveSection(spacer, 1) },
            getString(R.string.delete_spacer) to { viewModel.deleteSpacer(spacer.id) }
        )
        showActions(getString(R.string.spacer), actions)
    }

    private fun showActions(title: String, actions: List<Pair<String, () -> Unit>>) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, action -> actions[action].second() }
            .show()
    }

    private fun openMembershipEditor(category: CategorySummary) {
        startActivity(CategoryMembershipActivity.createIntent(this, category.id, category.name))
    }

    private fun showSortDialog(category: CategorySummary) {
        val values = ApplicationSortMode.entries
        showChoice(
            R.string.sort_applications,
            values.map { getString(it.labelRes) },
            values.indexOf(category.sortMode)
        ) { viewModel.updateCategory(category, sortMode = values[it]) }
    }

    private fun showLayoutDialog(category: CategorySummary) {
        val values = CategoryLayoutType.entries
        showChoice(
            R.string.layout,
            values.map { getString(it.labelRes) },
            values.indexOf(category.layoutType)
        ) { viewModel.updateCategory(category, layoutType = values[it]) }
    }

    private fun showGridColumnsDialog(category: CategorySummary) {
        val values = (5..10).toList()
        showChoice(
            R.string.grid_columns_title,
            values.map(Int::toString),
            values.indexOf(category.gridColumns)
        ) { viewModel.updateCategory(category, gridColumns = values[it]) }
    }

    private fun showRowHeightDialog(category: CategorySummary) {
        val values = (80..150 step 10).toList()
        showChoice(
            R.string.row_height_title,
            values.map { getString(R.string.dp_value, it) },
            values.indexOf(category.rowHeight)
        ) { viewModel.updateCategory(category, rowHeight = values[it]) }
    }

    private fun showSpacerHeightDialog(titleRes: Int, selectedHeight: Int, save: (Int) -> Unit) {
        val values = listOf(10, 20, 30, 40, 50, 75, 100, 150)
        showChoice(
            titleRes,
            values.map { getString(R.string.dp_value, it) },
            values.indexOf(selectedHeight)
        ) { save(values[it]) }
    }

    private fun showChoice(titleRes: Int, labels: List<String>, selected: Int, save: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, selection ->
                save(selection)
                dialog.dismiss()
            }
            .show()
    }

    private fun showNameDialog(titleRes: Int, currentName: String = "", save: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.category_name)
            setText(currentName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) showMessage(R.string.category_name_required) else save(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(category: CategorySummary) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_category)
            .setMessage(getString(R.string.delete_category_confirmation, category.name))
            .setPositiveButton(R.string.delete_category) { _, _ -> viewModel.deleteCategory(category.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}

private val ApplicationSortMode.labelRes: Int
    get() = when (this) {
        ApplicationSortMode.MANUAL -> R.string.sort_manual
        ApplicationSortMode.ALPHABETICAL -> R.string.sort_alphabetical
        ApplicationSortMode.LAST_USED -> R.string.sort_last_used
    }

private val CategoryLayoutType.labelRes: Int
    get() = when (this) {
        CategoryLayoutType.ROW -> R.string.layout_row
        CategoryLayoutType.GRID -> R.string.layout_grid
    }
