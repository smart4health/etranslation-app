package de.hpi.etranslation.feature.dashboard.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardAccountsViewBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.ViewAccountViewModel
import de.hpi.etranslation.lib.chdp.LoginContract
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ViewAccountController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.viewAccountViewModelFactory.create(lifecycleScope)
    }

    private lateinit var loginLauncher: ActivityResultLauncher<Intent>

    override fun onContextAvailable(context: Context) {
        super.onContextAvailable(context)
        loginLauncher = (activity as ComponentActivity).activityResultRegistry
            .register(
                "d4l_login_launcher",
                this@ViewAccountController,
                LoginContract(context.colorSurface),
                viewModel::onLauncherDone,
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View {
        return ControllerDashboardAccountsViewBinding.inflate(inflater, container, false).apply {
            topAppBar.applyInsetter {
                type(statusBars = true) {
                    margin()
                }
            }

            topAppBar.setNavigationOnClickListener {
                router.popController(this@ViewAccountController)
            }

            topAppBar.title =
                context.getString(R.string.controller_dashboard_accounts_view_s4h_title)

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.viewState.collect { viewState ->
                        divider1.visibility = viewState.isError.orInvisible()
                        fixAccountButton.visibility = viewState.isError.orInvisible()
                        fixAccountButton.isEnabled = !viewState.isLoading
                        fixAccountWarning.visibility = viewState.isError.orInvisible()
                    }
                }
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.events.receiveAsFlow().collect { event ->
                        when (event) {
                            is ViewAccountViewModel.Event.LoginIntent ->
                                loginLauncher.launch(event.intent)
                            ViewAccountViewModel.Event.DidNotLogin -> Toast.makeText(
                                context,
                                R.string.controller_dashboard_accounts_view_toast_login_cancelled,
                                Toast.LENGTH_SHORT,
                            ).show()
                            ViewAccountViewModel.Event.WrongAccount -> Toast.makeText(
                                context,
                                R.string.controller_dashboard_accounts_view_toast_login_mismatch,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }

            fixAccountButton.setOnClickListener { viewModel.onFixAccount(context) }

            logOutButton.setOnClickListener {
                logOutButton.isEnabled = false
                requireViewLifecycleOwner.lifecycleScope.launch {
                    val shouldLogOut = suspendCancellableCoroutine { cont ->
                        MaterialAlertDialogBuilder(root.context).apply {
                            setTitle(R.string.controller_dashboard_accounts_view_dialog_title)
                            setMessage(R.string.controller_dashboard_accounts_view_dialog_message)
                            setNeutralButton(R.string.controller_dashboard_accounts_view_dialog_neutral) { _, _ ->
                                cont.resume(false)
                            }

                            setPositiveButton(R.string.controller_dashboard_accounts_view_dialog_positive) { _, _ ->
                                cont.resume(true)
                            }
                            setOnCancelListener {
                                cont.resume(false)
                            }
                        }.show()
                    }

                    if (shouldLogOut) {
                        viewModel.onRemoveAccount()
                        router.popController(this@ViewAccountController)
                    }

                    logOutButton.isEnabled = true
                }
            }

            deleteDeprecatedResourcesButton.setOnClickListener {
                deleteDeprecatedResourcesButton.isEnabled = false
                requireViewLifecycleOwner.lifecycleScope.launch {
                    val shouldDelete = suspendCancellableCoroutine { cont ->
                        MaterialAlertDialogBuilder(root.context).apply {
                            setTitle(R.string.controller_dashboard_accounts_view_dialog_delete_deprecated_title)
                            setMessage(R.string.controller_dashboard_accounts_view_dialog_delete_deprecated_message)
                            setNeutralButton(R.string.controller_dashboard_accounts_view_dialog_delete_deprecated_neutral) { _, _ ->
                                cont.resume(false)
                            }

                            setPositiveButton(R.string.controller_dashboard_accounts_view_dialog_delete_deprecated_positive) { _, _ ->
                                cont.resume(true)
                            }
                            setOnCancelListener {
                                cont.resume(false)
                            }
                        }.show()
                    }

                    if (shouldDelete) {
                        viewModel.deleteDeprecatedResources()
                            .onSuccess { n ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.controller_dashboard_accounts_view_toast_deleted_n_records,
                                        n,
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }.onFailure { ex ->
                                Toast.makeText(
                                    context,
                                    R.string.controller_dashboard_accounts_view_toast_delete_deprecated_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                                Log.e("HPI", "Failed to delete", ex)
                            }
                    }

                    deleteDeprecatedResourcesButton.isEnabled = false
                }
            }
        }.root
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val viewAccountViewModelFactory: ViewAccountViewModel.Factory
    }
}

private fun Boolean.orInvisible() = if (this) View.VISIBLE else View.INVISIBLE
