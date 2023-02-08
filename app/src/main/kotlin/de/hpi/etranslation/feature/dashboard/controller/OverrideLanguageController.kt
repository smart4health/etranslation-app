package de.hpi.etranslation.feature.dashboard.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.ALL_LANGS
import de.hpi.etranslation.Lang
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.asFlag
import de.hpi.etranslation.databinding.ChipFlagBinding
import de.hpi.etranslation.databinding.ControllerDashboardOverrideLanguageBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguageUseCase
import de.hpi.etranslation.feature.dashboard.viewmodel.OverrideLanguageViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.launch

class OverrideLanguageController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.overrideLanguageViewModelFactory.create(lifecycleScope)
    }

    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerDashboardOverrideLanguageBinding.inflate(inflater, container, false).apply {
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

        toChipGroup.removeAllViews()
        ALL_LANGS.forEach { lang ->
            ChipFlagBinding.inflate(inflater, toChipGroup, false).apply {
                root.text = lang.asFlag()
                root.tag = lang
            }.root.let(toChipGroup::addView)
        }

        toChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val lang = toChipGroup.findViewById<Chip>(checkedIds.single()).tag as Lang
                viewModel.onSelected(lang)
            }
        }

        requireViewLifecycleOwner.lifecycleScope.launch {
            requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState.collect { viewState ->
                    val asterisk = when (viewState.originalSource) {
                        InferDocumentLanguageUseCase.Source.LOCAL_OVERRIDE -> ""
                        InferDocumentLanguageUseCase.Source.GLOBAL_OVERRIDE -> ""
                        InferDocumentLanguageUseCase.Source.LOCALE -> context.getString(R.string.lang_source_asterisk_device)
                        InferDocumentLanguageUseCase.Source.FALLBACK -> context.getString(R.string.lang_source_asterisk_fallback)
                    }
                    val warning = when (viewState.originalSource) {
                        InferDocumentLanguageUseCase.Source.LOCAL_OVERRIDE -> null
                        InferDocumentLanguageUseCase.Source.GLOBAL_OVERRIDE -> context.getString(R.string.lang_source_global)
                        InferDocumentLanguageUseCase.Source.LOCALE -> context.getString(
                            R.string.lang_source,
                            context.getString(R.string.lang_source_asterisk_device),
                            context.getString(R.string.lang_source_device),
                        )
                        InferDocumentLanguageUseCase.Source.FALLBACK -> context.getString(
                            R.string.lang_source,
                            context.getString(R.string.lang_source_asterisk_fallback),
                            context.getString(R.string.lang_source_fallback),
                        )
                    }

                    fromFlags.text = context.getString(
                        R.string.controller_dashboard_override_language_body_from,
                        viewState.originalLang.asFlag(),
                        asterisk,
                    )

                    sourceTextView.visibility = if (warning == null) View.GONE else View.VISIBLE
                    warning?.let(sourceTextView::setText)

                    if (viewState.numExistingTranslations > 0) {
                        val bullet = context.getString(R.string.controller_dashboard_bullet_override_language_text_warning)
                        val t = context.resources.getQuantityString(
                            R.plurals.controller_dashboard_override_language_text_warning,
                            viewState.numExistingTranslations,
                            viewState.numExistingTranslations,
                        )
                        warningTextView.text = "$bullet $t"
                        warningTextView.visibility = View.VISIBLE
                    } else
                        warningTextView.visibility = View.INVISIBLE

                    viewState.targetLang?.let { l ->
                        toChipGroup.findViewWithTag<Chip>(l)?.let {
                            it.isChecked = true
                        }
                    }
                    overrideButton.isEnabled = viewState.targetLang != null
                }
            }
        }

        overrideButton.setOnClickListener {
            overrideButton.isEnabled = false

            // wait for the override to finish before closing
            requireViewLifecycleOwner.lifecycleScope.launch {
                viewModel.onOverride()
                close()
            }
        }

        cancelButton.setOnClickListener { close() }
    }.root

    override fun onAttach(view: View) {
        super.onAttach(view)

        view.post {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun handleBack(): Boolean {
        close()
        return true
    }

    private fun close() {
        viewModel.onClose()
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        router?.popController(this@OverrideLanguageController)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val overrideLanguageViewModelFactory: OverrideLanguageViewModel.Factory
    }
}
