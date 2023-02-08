package de.hpi.etranslation.feature.dashboard.controller

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
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
import de.hpi.etranslation.databinding.ControllerDashboardStartTranslationBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.StartTranslationViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.launch

class StartTranslationController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.startTranslationViewModelFactory.create(lifecycleScope)
    }

    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null

    private var enableNotificationsButton: Button? = null

    private lateinit var permissionsLauncher: ActivityResultLauncher<String>

    override fun onContextAvailable(context: Context) {
        super.onContextAvailable(context)

        permissionsLauncher = (activity as ComponentActivity)
            .activityResultRegistry
            .register(
                "permissions_launcher",
                this@StartTranslationController,
                ActivityResultContracts.RequestPermission(),
            ) {
                // this space intentionally left blank
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerDashboardStartTranslationBinding.inflate(inflater, container, false).apply {
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

        requireViewLifecycleOwner.lifecycleScope.launch {
            requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState.collect { viewState ->
                    fromFlags.text = viewState.originalLangs.joinToString(
                        separator = "  ",
                        transform = Lang::asFlag,
                    )

                    warningTextView.text = listOfNotNull(
                        if (viewState.stats.skipSymmetricTranslationCount > 0) {
                            val t = context.resources.getQuantityString(
                                R.plurals.controller_dashboard_start_translation_warning_skip_symmetric_translation,
                                viewState.stats.skipSymmetricTranslationCount,
                                viewState.stats.skipSymmetricTranslationCount,
                                viewState.selectedLang?.asFlag(),
                            )
                            val bullet = context.getString(
                                R.string.controller_dashboard_bullet_start_translation_warning_skip_symmetric_translation,
                            )
                            "$bullet $t"
                        } else null,
                        if (viewState.stats.willBeRetranslatedCount > 0) {
                            val t = context.resources.getQuantityString(
                                R.plurals.controller_dashboard_start_translation_warning_retranslation,
                                viewState.stats.willBeRetranslatedCount,
                                viewState.stats.willBeRetranslatedCount,
                            )
                            val bullet = context.getString(
                                R.string.controller_dashboard_bullet_start_translation_warning_retranslation,
                            )
                            "$bullet $t"
                        } else null,
                        if (viewState.stats.skipInProgressCount > 0) {
                            val t = context.resources.getQuantityString(
                                R.plurals.controller_dashboard_start_translation_warning_skip_in_progress,
                                viewState.stats.skipInProgressCount,
                                viewState.stats.skipInProgressCount,
                                viewState.selectedLang?.asFlag(),
                            )
                            val bullet = context.getString(
                                R.string.controller_dashboard_bullet_start_translation_warning_skip_in_progress,
                            )
                            "$bullet $t"
                        } else null,
                    ).joinToString(separator = "\n")

                    translateButton.isEnabled = viewState.selectedLang != null

                    viewState.selectedLang?.let {
                        toChipGroup.findViewWithTag<Chip>(it)?.isChecked = true
                    }
                }
            }
        }

        toChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            (group.findViewById<Chip>(checkedIds.first()).tag as? Lang)
                .let(viewModel::setSelectedLang)
        }

        cancelButton.setOnClickListener { close() }

        translateButton.setOnClickListener {
            translateButton.isEnabled = false
            requireViewLifecycleOwner.lifecycleScope.launch {
                viewModel.startTranslation()
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        this@StartTranslationController.enableNotificationsButton = enableNotificationsButton
        enableNotificationsButton.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                root.context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED

        enableNotificationsButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.root

    override fun onAttach(view: View) {
        super.onAttach(view)

        view.post {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)

        enableNotificationsButton?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setSelectedLang(null)
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
        val startTranslationViewModelFactory: StartTranslationViewModel.Factory
    }
}
