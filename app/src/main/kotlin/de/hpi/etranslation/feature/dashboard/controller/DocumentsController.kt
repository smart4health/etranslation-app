package de.hpi.etranslation.feature.dashboard.controller

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.MarginItemDecoration
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardDocumentsBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.DocumentAdapter
import de.hpi.etranslation.feature.dashboard.HasAnchorView
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.DocumentsViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.launch

class DocumentsController : ViewLifecycleController(), HasAnchorView {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.documentsViewModelFactory.create(lifecycleScope)
    }

    private var fab: ExtendedFloatingActionButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View = ControllerDashboardDocumentsBinding.inflate(inflater, container, false).apply {
        appBarContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            resources!!.displayMetrics,
        ).toInt()
            .let(::MarginItemDecoration)
            .let(recyclerView::addItemDecoration)
        recyclerView.layoutManager = LinearLayoutManager(root.context)
        recyclerView.adapter = DocumentAdapter(viewModel::onDocumentAction)

        requireViewLifecycleOwner.lifecycleScope.launch {
            requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState.collect { viewState ->
                    (recyclerView.adapter as? DocumentAdapter)
                        ?.submitList(viewState.documents)

                    noAccountsView.isVisible = viewState.countAccounts == 0
                    noDocumentsView.isVisible =
                        viewState.countAccounts > 0 && viewState.documents.isEmpty()

                    if (viewState.selectedCount > 0) {
                        contextAppBar.title = context.getString(
                            R.string.controller_dashboard_documents_title_selected,
                            viewState.selectedCount,
                        )

                        appBarLayout.isLiftOnScroll = false
                        appBarLayout.isLifted = true

                        contextAppBar.visibility = View.VISIBLE
                        topAppBar.visibility = View.GONE

                        swipeRefreshLayout.isEnabled = false

                        translateFab.show()
                    } else {
                        appBarLayout.isLiftOnScroll = true

                        topAppBar.visibility = View.VISIBLE
                        contextAppBar.visibility = View.GONE

                        swipeRefreshLayout.isEnabled = true
                        translateFab.hide()
                    }

                    swipeRefreshLayout.isRefreshing = viewState.isRefreshing
                }
            }
        }

        contextAppBar.setNavigationOnClickListener {
            viewModel.deselectAll()
        }

        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_faq -> {
                    viewModel.onFaq()
                    true
                }
                else -> {
                    Log.e("HPI", "Unknown menu item id")
                    false
                }
            }
        }

        translateFab.setOnClickListener {
            viewModel.onTranslate()
        }

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.onRefresh()
        }

        fab = this.translateFab
    }.root

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        fab = null
    }

    override fun anchorView(): View? =
        if (fab?.visibility == View.VISIBLE)
            fab
        else
            null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val documentsViewModelFactory: DocumentsViewModel.Factory
    }
}
