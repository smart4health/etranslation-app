package de.hpi.etranslation.feature.viewer.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardViewTranslationBinding

class ViewTranslationController(
    bundle: Bundle,
) : ViewLifecycleController(bundle), HasScrollable {
    private val scrollableId: Int = View.generateViewId()

    @Suppress("unused")
    private val localId by lazy {
        args.getString("LOCAL_ID")!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerDashboardViewTranslationBinding.inflate(inflater, container, false).apply {
        root.id = scrollableId
    }.root

    companion object {
        fun withLocalId(localId: String): ViewLifecycleController =
            ViewTranslationController(bundleOf("LOCAL_ID" to localId))
    }

    override fun getId(): Int = scrollableId
}

interface HasScrollable {
    fun getId(): Int
}
