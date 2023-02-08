package de.hpi.etranslation.feature.dashboard.controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.ColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardAccountsAddBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.AddAccountViewModel
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import de.hpi.etranslation.lib.chdp.LoginContract
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AddAccountController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.addAccountViewModelFactory.create(lifecycleScope)
    }

    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null

    private lateinit var loginLauncher: ActivityResultLauncher<Intent>

    override fun onContextAvailable(context: Context) {
        super.onContextAvailable(context)
        loginLauncher = (activity as ComponentActivity).activityResultRegistry
            .register(
                "d4l_login_launcher",
                this@AddAccountController,
                LoginContract(context.colorSurface),
                viewModel::onLauncherDone,
            )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View {
        return ControllerDashboardAccountsAddBinding.inflate(inflater, container, false).apply {
            root.applyInsetter {
                type(navigationBars = true) {
                    margin()
                }
            }

            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
                state = BottomSheetBehavior.STATE_HIDDEN

                addBottomSheetCallback(
                    object : BottomSheetBehavior.BottomSheetCallback() {
                        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit

                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            if (newState == BottomSheetBehavior.STATE_HIDDEN)
                                router.popCurrentController()
                        }
                    },
                )
            }

            scrim.setOnClickListener { close() }
            cancelButton.setOnClickListener { close() }

            optionOtherLogo.isEnabled = false

            optionS4h.setOnClickListener {
                viewModel.onAddS4hAccount()
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.viewState.collect { viewState ->
                        isOptionS4hAccountEnabled = !(viewState.s4hLoading || viewState.s4hMaxed)
                    }
                }
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.events.receiveAsFlow().collect { event ->
                        when (event) {
                            AddAccountViewModel.Event.LAUNCH_S4H -> {
                                entryPoint.d4lClient
                                    .loginIntent()
                                    .let(loginLauncher::launch)

                                viewModel.onLaunched()
                            }
                            AddAccountViewModel.Event.LOGIN_FAILED -> Toast.makeText(
                                context,
                                R.string.controller_dashboard_accounts_add_toast_login_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                            AddAccountViewModel.Event.LOGIN_SUCCESS -> {
                                close()
                            }
                        }
                    }
                }
            }
        }.root
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        view.post {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private var ControllerDashboardAccountsAddBinding.isOptionS4hAccountEnabled: Boolean
        get() = optionS4h.isEnabled
        set(value) {
            optionS4h.isEnabled = value
            optionS4hLogo.alpha = if (value) 1f else .38f
            optionS4hTitle.isEnabled = value
            optionS4hSubtitle.isEnabled = value
        }

    override fun handleBack(): Boolean {
        close()
        return true
    }

    private fun close() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        router?.popController(this)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val addAccountViewModelFactory: AddAccountViewModel.Factory

        val d4lClient: AsyncData4LifeClient
    }
}

@get:ColorInt
internal val Context.colorSurface: Int
    get() = TypedValue().let { typedValue ->
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        typedValue.data
    }
