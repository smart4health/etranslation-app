package de.hpi.etranslation.feature.onboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.asTransaction
import com.google.android.material.transition.MaterialSharedAxis
import de.hpi.etranslation.MainEvent
import de.hpi.etranslation.MaterialSharedAxisChangeHandler
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerOnboardBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.onboard.di.OnboardEntryPoint
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class OnboardController : ViewLifecycleController() {

    private val entryPoint by entryPoint<OnboardEntryPoint>()

    private var childRouter: Router? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View {
        return ControllerOnboardBinding.inflate(inflater, container, false).apply {
            childRouter = getChildRouter(childContainer)
                .setPopRootControllerMode(Router.PopRootControllerMode.NEVER)
                .also { r ->
                    if (r.backstack.isEmpty())
                        IntroController()
                            .asTransaction()
                            .let(r::setRoot)
                }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    entryPoint.onboardEventReceiver.receiveAsFlow().collect { event ->
                        when (event) {
                            OnboardEvent.INTRO_DONE -> requireViewLifecycleOwner.lifecycleScope.launch {
                                entryPoint.dataStore.updateData { settings ->
                                    settings.copy(isConsented = true)
                                }
                                entryPoint.mainEventSender.send(MainEvent.LOGGED_IN)
                            }
                            OnboardEvent.VIEW_PRIVACY -> PrivacyAndTermsController()
                                .asTransaction()
                                .pushChangeHandler(
                                    MaterialSharedAxisChangeHandler(
                                        axis = MaterialSharedAxis.Z,
                                        removesFromViewOnPush = false,
                                    ),
                                )
                                .popChangeHandler(
                                    MaterialSharedAxisChangeHandler(
                                        axis = MaterialSharedAxis.Z,
                                    ),
                                )
                                .let { childRouter?.pushController(it) }
                        }
                    }
                }
            }
        }.root
    }
}
