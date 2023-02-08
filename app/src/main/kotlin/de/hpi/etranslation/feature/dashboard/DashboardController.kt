package de.hpi.etranslation.feature.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.asTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.google.android.material.snackbar.Snackbar
import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.MaterialFadeThroughChangeHandler
import de.hpi.etranslation.R
import de.hpi.etranslation.Settings
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.databinding.ControllerDashboardBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.controller.AccountsController
import de.hpi.etranslation.feature.dashboard.controller.AddAccountController
import de.hpi.etranslation.feature.dashboard.controller.DocumentsController
import de.hpi.etranslation.feature.dashboard.controller.FaqController
import de.hpi.etranslation.feature.dashboard.controller.OverrideLanguageController
import de.hpi.etranslation.feature.dashboard.controller.SettingsController
import de.hpi.etranslation.feature.dashboard.controller.StartTranslationController
import de.hpi.etranslation.feature.dashboard.controller.ViewAccountController
import de.hpi.etranslation.feature.viewer.controller.ViewDocumentController
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private var childRouter: Router? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View {
        return ControllerDashboardBinding.inflate(inflater, container, false).apply {
            childRouter = getChildRouter(childContainer)
                .setPopRootControllerMode(Router.PopRootControllerMode.NEVER)
                .also { r ->
                    if (r.backstack.isEmpty())
                        DocumentsController()
                            .asTransaction()
                            .let(r::setRoot)
                }

            root.applyInsetter {
                type(navigationBars = true) {
                    margin()
                }
            }

            bottomNavigationView.setOnApplyWindowInsetsListener(null)
            bottomNavigationView.setOnItemReselectedListener { }
            bottomNavigationView.setOnItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_documents -> {
                        DocumentsController()
                            .asTransaction()
                            .pushChangeHandler(MaterialFadeThroughChangeHandler())
                            .let { childRouter?.replaceTopController(it) }

                        true
                    }
                    R.id.action_accounts -> {
                        AccountsController()
                            .asTransaction()
                            .pushChangeHandler(MaterialFadeThroughChangeHandler())
                            .let { childRouter?.replaceTopController(it) }

                        true
                    }
                    R.id.action_settings -> {
                        SettingsController()
                            .asTransaction()
                            .pushChangeHandler(MaterialFadeThroughChangeHandler())
                            .let { childRouter?.replaceTopController(it) }

                        true
                    }
                    else -> {
                        Log.e(this::class.simpleName, "Unrecognized menu action")
                        false
                    }
                }
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    entryPoint.dashboardEventReceiver.receiveAsFlow().collect { event ->
                        when (event) {
                            DashboardEvent.Translate -> {
                                val duration = resources
                                    ?.getInteger(android.R.integer.config_shortAnimTime)
                                    ?.toLong()
                                    ?: 100

                                StartTranslationController()
                                    .asTransaction()
                                    .pushChangeHandler(FadeChangeHandler(duration, false))
                                    .popChangeHandler(FadeChangeHandler(duration))
                                    .let(router::pushController)
                            }
                            DashboardEvent.OverrideLanguage -> {
                                val duration = resources
                                    ?.getInteger(android.R.integer.config_shortAnimTime)
                                    ?.toLong()
                                    ?: 100

                                OverrideLanguageController()
                                    .asTransaction()
                                    .pushChangeHandler(FadeChangeHandler(duration, false))
                                    .popChangeHandler(FadeChangeHandler(duration))
                                    .let(router::pushController)
                            }
                            is DashboardEvent.View -> {
                                ViewDocumentController.withOriginalId(event.id)
                                    .asTransaction()
                                    .pushChangeHandler(
                                        MaterialFadeThroughChangeHandler(
                                            removesFromViewOnPush = false,
                                        ),
                                    )
                                    .popChangeHandler(MaterialFadeThroughChangeHandler())
                                    .let(router::pushController)
                            }
                            DashboardEvent.AddAccount -> {
                                val duration = resources
                                    ?.getInteger(android.R.integer.config_shortAnimTime)
                                    ?.toLong()
                                    ?: 100

                                AddAccountController()
                                    .asTransaction()
                                    .pushChangeHandler(FadeChangeHandler(duration, false))
                                    .popChangeHandler(FadeChangeHandler(duration))
                                    .let(router::pushController)
                            }
                            DashboardEvent.ViewAccount -> {
                                ViewAccountController()
                                    .asTransaction()
                                    .pushChangeHandler(
                                        MaterialFadeThroughChangeHandler(
                                            removesFromViewOnPush = false,
                                        ),
                                    )
                                    .popChangeHandler(MaterialFadeThroughChangeHandler())
                                    .let(router::pushController)
                            }
                            DashboardEvent.Faq -> {
                                FaqController()
                                    .asTransaction()
                                    .pushChangeHandler(
                                        MaterialFadeThroughChangeHandler(
                                            removesFromViewOnPush = false,
                                        ),
                                    )
                                    .popChangeHandler(MaterialFadeThroughChangeHandler())
                                    .let(router::pushController)
                            }
                        }
                    }
                }
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    entryPoint.dashboardErrorReceiver.receiveAsFlow().collect { error ->
                        val text = when (error) {
                            is DashboardError.SendRequestsFailure -> context.getString(
                                R.string.controller_dashboard_toast_send_requests_failure,
                                error.failureCount,
                            )
                            is DashboardError.TranslationFailure -> context.getString(
                                R.string.controller_dashboard_toast_translation_failure,
                            )
                            DashboardError.Unknown -> context.getString(
                                R.string.controller_dashboard_toast_unknown_failure,
                            )
                        }

                        val anchorView = topChildAsHasAnchorView()?.anchorView()
                            ?: bottomNavigationView

                        Snackbar.make(root, text, Snackbar.LENGTH_LONG)
                            .setAnchorView(anchorView)
                            .show()
                    }
                }
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    entryPoint.documentsDatabase
                        .accountsQueries
                        .countHasError()
                        .asFlow()
                        .map { query ->
                            withContext(Dispatchers.IO) {
                                query.executeAsOne()
                            }
                        }
                        .collect {
                            if (it > 0)
                                bottomNavigationView.getOrCreateBadge(R.id.action_accounts)
                            else
                                bottomNavigationView.removeBadge(R.id.action_accounts)
                        }
                }
            }
        }.root
    }

    override fun onDestroy() {
        super.onDestroy()
        (activity as? LifecycleOwner)?.lifecycleScope?.launch {
            withContext(Dispatchers.IO) {
                entryPoint.documentsDatabase.selectionsQueries.deselectAll()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val dataStore: DataStore<Settings>

        val dashboardEventReceiver: @JvmSuppressWildcards ReceiveChannel<DashboardEvent>

        val documentsDatabase: DocumentsDatabase

        val dashboardErrorReceiver: @JvmSuppressWildcards ReceiveChannel<DashboardError>
    }

    private fun topChildAsHasAnchorView(): HasAnchorView? = childRouter
        ?.backstack
        ?.lastOrNull()
        ?.controller
        as? HasAnchorView
}

sealed class DashboardEvent {
    object Translate : DashboardEvent()
    object OverrideLanguage : DashboardEvent()
    data class View(val id: String) : DashboardEvent()
    object AddAccount : DashboardEvent()
    object ViewAccount : DashboardEvent()
    object Faq : DashboardEvent()
}

sealed class DashboardError {
    data class SendRequestsFailure(
        val failureCount: Int,
    ) : DashboardError()

    data class TranslationFailure(
        val failureCount: Int,
    ) : DashboardError()

    object Unknown : DashboardError()
}
