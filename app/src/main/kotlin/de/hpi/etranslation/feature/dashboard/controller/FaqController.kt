package de.hpi.etranslation.feature.dashboard.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardFaqBinding
import dev.chrisbanes.insetter.applyInsetter

class FaqController : ViewLifecycleController() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ) = ControllerDashboardFaqBinding.inflate(inflater, container, false).apply {
        topAppBar.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        topAppBar.setNavigationOnClickListener {
            router.popController(this@FaqController)
        }
    }.root
}
