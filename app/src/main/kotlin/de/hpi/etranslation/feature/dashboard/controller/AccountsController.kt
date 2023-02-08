package de.hpi.etranslation.feature.dashboard.controller

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.GridSpacingItemDecoration
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardAccountsBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.AccountsAdapter
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.AccountsViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.launch

class AccountsController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.accountsViewModelFactory.create(lifecycleScope)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View {
        return ControllerDashboardAccountsBinding.inflate(inflater, container, false).apply {
            topAppBar.applyInsetter {
                type(statusBars = true) {
                    margin()
                }
            }

            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                32f,
                resources!!.displayMetrics,
            ).toInt()
                .let(::GridSpacingItemDecoration)
                .let(recyclerView::addItemDecoration)
            val spanCount = context.resources.getInteger(R.integer.accounts_grid_span_count)
            recyclerView.layoutManager = GridLayoutManager(context, spanCount)
            recyclerView.adapter = AccountsAdapter(viewModel::onItemSelected)

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.viewState.collect { viewState ->
                        (recyclerView.adapter as? AccountsAdapter)
                            ?.submitList(viewState)

                        if (viewState.isEmpty()) {
                            recyclerView.visibility = View.INVISIBLE
                            noAccountsView.visibility = View.VISIBLE
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            noAccountsView.visibility = View.GONE
                        }
                    }
                }
            }

            addAccountFab.setOnClickListener {
                viewModel.onAddAccount()
            }
        }.root
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val accountsViewModelFactory: AccountsViewModel.Factory
    }
}
