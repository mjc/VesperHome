package com.sergioasenjo.ltvlauncher.applications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.R
import com.sergioasenjo.ltvlauncher.databinding.ActivityCategoryManagementBinding
import com.sergioasenjo.ltvlauncher.databinding.ItemCategoryManagementBinding
import com.sergioasenjo.ltvlauncher.launcher.LauncherCategory
import com.sergioasenjo.ltvlauncher.launcher.LauncherEvent
import com.sergioasenjo.ltvlauncher.launcher.LauncherViewModel
import kotlinx.coroutines.launch

class CategoryManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoryManagementBinding
    private val viewModel: LauncherViewModel by viewModels {
        val container = (application as LtvLauncherApplication).container
        LauncherViewModel.factory(
            container.applicationRepository,
            container.appPreferencesRepository,
            container.categoryRepository,
            container.homeRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.createCategory.setOnClickListener {
            showNameDialog(R.string.create_category) { viewModel.createCategory(it) }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> renderCategories(state.categories) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            LauncherEvent.CategoryUpdateFailed -> showMessage(R.string.category_update_failed)
                            LauncherEvent.LaunchFailed -> showMessage(R.string.launch_failed)
                            LauncherEvent.PreferenceUpdateFailed -> showMessage(R.string.preference_update_failed)
                            is LauncherEvent.OpenIntent -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun renderCategories(categories: List<LauncherCategory>) {
        val focusedCategoryId = binding.categoryContainer.findFocus()?.tag as? Long
        binding.categoryContainer.removeAllViews()
        binding.emptyMessage.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE

        categories.forEach { category ->
            val itemBinding = ItemCategoryManagementBinding.inflate(
                LayoutInflater.from(this),
                binding.categoryContainer,
                false
            )
            itemBinding.category.apply {
                tag = category.id
                text = resources.getQuantityString(
                    R.plurals.category_application_count,
                    category.apps.size,
                    category.name,
                    category.apps.size
                )
                setOnClickListener { showCategoryActions(category) }
            }
            binding.categoryContainer.addView(itemBinding.root)
        }

        val focusTarget = binding.categoryContainer.findViewWithTag<View>(focusedCategoryId)
            ?: binding.categoryContainer.getChildAt(0)
            ?: binding.createCategory
        if (currentFocus == null || focusedCategoryId != null) {
            focusTarget.post { focusTarget.requestFocus() }
        }
    }

    private fun showCategoryActions(category: LauncherCategory) {
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

    private fun confirmDelete(category: LauncherCategory) {
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
