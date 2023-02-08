package de.hpi.etranslation.feature.dashboard.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.ALL_LANGS
import de.hpi.etranslation.BuildConfig
import de.hpi.etranslation.Lang
import de.hpi.etranslation.R
import de.hpi.etranslation.Theme
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.asFlag
import de.hpi.etranslation.databinding.ChipFlagBinding
import de.hpi.etranslation.databinding.ControllerDashboardSettingsBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.dashboard.viewmodel.SettingsViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SettingsController : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val viewModel by lazy {
        entryPoint.viewModelFactory.create(lifecycleScope)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View {
        return ControllerDashboardSettingsBinding.inflate(inflater, container, false).apply {
            topAppBar.applyInsetter {
                type(statusBars = true) {
                    margin()
                }
            }

            ALL_LANGS.forEach { lang ->
                ChipFlagBinding.inflate(inflater, chipGroup, false).apply {
                    root.text = lang.asFlag()
                    root.tag = lang
                }.root.let(chipGroup::addView)
            }

            requireViewLifecycleOwner.lifecycleScope.launch {
                requireViewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.viewState.collect { viewState ->

                        debugDivider.isVisible = BuildConfig.DEBUG
                        debugTitle.isVisible = BuildConfig.DEBUG
                        breakAccountButton.isVisible = BuildConfig.DEBUG
                        d4lId.isVisible = BuildConfig.DEBUG

                        deviceLocaleTextView.text = viewState.deviceLang?.asFlag()?.let { flag ->
                            context.getString(
                                R.string.controller_dashboard_settings_body_device_language,
                                flag,
                            )
                        } ?: context.getString(
                            R.string.controller_dashboard_settings_body_device_language_unknown,
                        )

                        if (viewState.langOverride != null)
                            chipGroup.findViewWithTag<Chip>(viewState.langOverride)
                                ?.isChecked = true
                        else
                            chipGroup.clearCheck()

                        when (viewState.theme) {
                            Theme.DAY -> R.id.chip_day_theme to R.string.controller_dashboard_settings_body_theme_day
                            Theme.NIGHT -> R.id.chip_night_theme to R.string.controller_dashboard_settings_body_theme_night
                            Theme.SYSTEM -> R.id.chip_system_theme to R.string.controller_dashboard_settings_body_theme_follow_system
                        }.let { (chipId, resId) ->
                            nightModeChipGroup.check(chipId)
                            themeTextView.text = root.context.getText(resId)
                        }

                        d4lId.text = viewState.d4lId ?: "🙁 SDK broke"
                    }
                }
            }

            chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                val lang = checkedIds.firstOrNull()
                    ?.let<Int, Chip>(chipGroup::findViewById)
                    ?.tag as? Lang

                viewModel.setLangOverride(lang)
            }

            clearDefaultButton.setOnClickListener {
                viewModel.setLangOverride(null)
            }

            nightModeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                val newTheme = when (checkedIds.firstOrNull()) {
                    R.id.chip_day_theme -> Theme.DAY
                    R.id.chip_night_theme -> Theme.NIGHT
                    R.id.chip_system_theme -> Theme.SYSTEM
                    else -> error("forgot a chip id")
                }

                viewModel.setTheme(newTheme)
            }

            logoutButton.setOnClickListener {
                requireViewLifecycleOwner.lifecycleScope.launch {
                    val shouldLogOut = suspendCancellableCoroutine { cont ->
                        MaterialAlertDialogBuilder(root.context).apply {
                            setTitle(R.string.controller_dashboard_settings_revoke_dialog_title)
                            setMessage(R.string.controller_dashboard_settings_revoke_dialog_message)
                            setNeutralButton(R.string.controller_dashboard_settings_revoke_dialog_button_neutral) { _, _ ->
                                cont.resume(false)
                            }

                            setPositiveButton(R.string.controller_dashboard_settings_revoke_dialog_button_positive) { _, _ ->
                                cont.resume(true)
                            }
                            setOnCancelListener {
                                cont.resume(false)
                            }
                        }.show()
                    }

                    if (shouldLogOut)
                        viewModel.revokeConsent()
                }
            }

            breakAccountButton.setOnClickListener {
                viewModel.breakAccount()
            }
        }.root
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val viewModelFactory: SettingsViewModel.Factory
    }
}
