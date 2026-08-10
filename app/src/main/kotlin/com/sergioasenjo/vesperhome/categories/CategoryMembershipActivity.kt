package com.sergioasenjo.vesperhome.categories

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ActivityCategoryMembershipBinding
import com.sergioasenjo.vesperhome.settings.ManagementScreenAppearanceRenderer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CategoryMembershipActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCategoryMembershipBinding
    private val categoryId: Long by lazy { intent.getLongExtra(EXTRA_CATEGORY_ID, 0) }
    private val viewModel: CategoryMembershipViewModel by viewModels {
        val container = (application as VesperHomeApplication).container
        CategoryMembershipViewModel.factory(
            categoryId,
            container.categoryRepository,
            container.managedApplicationsRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryMembershipBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.title.text = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty()
        val container = (application as VesperHomeApplication).container
        val appearanceRenderer = ManagementScreenAppearanceRenderer(
            binding.root,
            binding.wallpaper,
            binding.content,
            listOf(binding.workspace)
        )

        val membershipAdapter = CategoryMembershipAdapter(viewModel::toggle)
        binding.apps.apply {
            layoutManager = LinearLayoutManager(this@CategoryMembershipActivity)
            adapter = membershipAdapter
            itemAnimator = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        container.launcherSettingsRepository.appearance,
                        container.wallpaperRepository.state
                    ) { appearance, wallpaper -> appearance to wallpaper }.collect { (appearance, wallpaper) ->
                        membershipAdapter.setAppearance(appearanceRenderer.render(appearance, wallpaper))
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.emptyMessage.isVisible = !state.loading && state.apps.isEmpty()
                        membershipAdapter.submitList(state.apps) {
                            if (state.apps.isNotEmpty() && currentFocus == null) {
                                binding.apps.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            }
                        }
                    }
                }
                launch {
                    viewModel.failures.collect {
                        Toast.makeText(
                            this@CategoryMembershipActivity,
                            R.string.category_update_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "category_id"
        private const val EXTRA_CATEGORY_NAME = "category_name"

        fun createIntent(context: Context, categoryId: Long, categoryName: String): Intent =
            Intent(context, CategoryMembershipActivity::class.java)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
                .putExtra(EXTRA_CATEGORY_NAME, categoryName)
    }
}
