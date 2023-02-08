package de.hpi.etranslation.feature.onboard

import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.text.toSpannable
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.asTransaction
import com.bluelinelabs.conductor.viewpager2.RouterStateAdapter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.ALL_LANGS
import de.hpi.etranslation.ClickableMovementMethod
import de.hpi.etranslation.Lang
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.asFlag
import de.hpi.etranslation.databinding.ControllerOnboardIntroBinding
import de.hpi.etranslation.databinding.ControllerOnboardIntroPage1Binding
import de.hpi.etranslation.databinding.ControllerOnboardIntroPage2Binding
import de.hpi.etranslation.databinding.ControllerOnboardIntroPage3Binding
import de.hpi.etranslation.databinding.ControllerOnboardIntroPage4Binding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.context
import de.hpi.etranslation.feature.onboard.di.OnboardEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

class IntroController : ViewLifecycleController() {

    private val entryPoint by entryPoint<OnboardEntryPoint>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedInstanceState: Bundle?,
    ): View {
        return ControllerOnboardIntroBinding.inflate(inflater, container, false).apply {
            root.applyInsetter {
                type(navigationBars = true, statusBars = true) {
                    padding()
                }
            }

            introViewPager.adapter = object : RouterStateAdapter(this@IntroController) {
                private val pages = listOf(
                    Page1Controller(),
                    Page2Controller(),
                    Page3Controller(),
                    Page4Controller(),
                )

                override fun configureRouter(router: Router, position: Int) {
                    if (!router.hasRootController()) {
                        pages[position]
                            .asTransaction()
                            .let(router::setRoot)
                    }
                }

                override fun getItemCount() = pages.size
            }
            introViewPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        backButton.isEnabled = position > 0
                    }
                },
            )

            dotsIndicator.attachTo(introViewPager)

            nextButton.setOnClickListener {
                if (introViewPager.currentItem == introViewPager.adapter?.itemCount?.minus(1)) {
                    requireViewLifecycleOwner.lifecycleScope.launch {
                        entryPoint.onboardEventSender.send(OnboardEvent.INTRO_DONE)
                    }
                } else
                    introViewPager.currentItem = introViewPager.currentItem + 1
            }

            backButton.setOnClickListener {
                introViewPager.currentItem = introViewPager.currentItem - 1
            }
        }.root
    }
}

class Page1Controller : ViewLifecycleController() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerOnboardIntroPage1Binding.inflate(inflater, container, false).apply {
        flagRow.text = ALL_LANGS.joinToString(
            separator = "",
            transform = Lang::asFlag,
        )

        description.text = context
            .getText(R.string.controller_onboard_intro_page_1_description_1)
            .toSpannable()
            .map<URLSpan> { urlSpan ->
                object : ClickableSpan() {
                    override fun onClick(widget: View) = CustomTabsIntent.Builder()
                        .build()
                        .intent
                        .setData(urlSpan.url.let(Uri::parse))
                        .let(this@Page1Controller::startActivity)
                }
            }

        description.movementMethod = ClickableMovementMethod()
    }.root
}

class Page2Controller : ViewLifecycleController() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerOnboardIntroPage2Binding.inflate(inflater, container, false).root
}

class Page3Controller : ViewLifecycleController() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerOnboardIntroPage3Binding.inflate(inflater, container, false).root
}

class Page4Controller : ViewLifecycleController() {

    private val entryPoint by entryPoint<MyEntryPoint>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerOnboardIntroPage4Binding.inflate(inflater, container, false).apply {
        description1.text = context
            .getText(R.string.controller_onboard_intro_page_4_description_1)
            .toSpannable()
            .map<URLSpan> {
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        requireViewLifecycleOwner.lifecycleScope.launch {
                            entryPoint.onboardEventSender.send(OnboardEvent.VIEW_PRIVACY)
                        }
                    }
                }
            }

        description1.movementMethod = ClickableMovementMethod()
    }.root

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val onboardEventSender: SendChannel<OnboardEvent>
    }
}

/**
 * Replace spans of type t in a spannable with mutation
 */
private inline fun <reified T> Spannable.map(mapper: (T) -> Any): Spannable {
    getSpans(0, length, T::class.java).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)

        removeSpan(span)

        setSpan(mapper(span), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    return this
}
