package com.sergioasenjo.ltvlauncher.categories

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
import com.sergioasenjo.ltvlauncher.databinding.ActivityCategoryManagementBinding
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

        val categoryAdapter = CategoryManagementAdapter(::showCategoryActions)
        binding.categories.apply {
            layoutManager = LinearLayoutManager(this@CategoryManagementActivity)
            adapter = categoryAdapter
            itemAnimator = null
        }
        binding.createCategory.setOnClickListener {
            showNameDialog(R.string.create_category, save = viewModel::createCategory)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.emptyMessage.isVisible = !state.loading && state.categories.isEmpty()
                        categoryAdapter.submitList(state.categories) {
                            if (state.categories.isNotEmpty() && currentFocus == null) {
                                binding.categories.findViewHolderForAdapterPosition(0)
                                    ?.itemView
                                    ?.requestFocus()
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

    private fun showCategoryActions(category: CategorySummary) {
        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setItems(
                arrayOf(getString(R.string.rename_category), getString(R.string.delete_category))
            ) { _, action ->
                when (action) {
                    0 -> showNameDialog(R.string.rename_category, category.name) {
                        viewModel.renameCategory(category.id, it)
                    }

                    1 -> confirmDelete(category)
                }
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
                if (name.isEmpty()) {
                    showMessage(R.string.category_name_required)
                } else {
                    save(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(category: CategorySummary) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_category)
            .setMessage(getString(R.string.delete_category_confirmation, category.name))
            .setPositiveButton(R.string.delete_category) { _, _ ->
                viewModel.deleteCategory(category.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}
