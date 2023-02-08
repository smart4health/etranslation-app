package de.hpi.etranslation.feature.viewer.controller

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerDashboardViewPdfBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.viewer.usecase.RenderPdfUseCase
import kotlinx.coroutines.launch

class ViewPdfController(
    bundle: Bundle,
) : ViewLifecycleController(bundle), HasScrollable {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val scrollableId: Int = View.generateViewId()

    private val id by lazy {
        args.getString("ID")!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerDashboardViewPdfBinding.inflate(inflater, container, false).apply {
        root.id = scrollableId

        imageView.doOnNextLayout { v ->
            requireViewLifecycleOwner.lifecycleScope.launch {
                entryPoint.renderPdfUseCase(id, v.width)
                    .onSuccess(imageView::setImageBitmap)
                    .onFailure { t ->
                        Log.e("HPI", "Failed to render pdf", t)
                        Toast.makeText(
                            context,
                            context.getString(R.string.controller_dashboard_view_pdf_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
            }
        }
    }.root

    override fun getId(): Int = scrollableId

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val renderPdfUseCase: RenderPdfUseCase
    }

    companion object {
        fun withId(id: String): ViewPdfController = bundleOf("ID" to id).let(::ViewPdfController)
    }
}
