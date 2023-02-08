package de.hpi.etranslation.feature.onboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import de.hpi.etranslation.R
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.databinding.ControllerOnboardPrivacyAndTermsBinding
import dev.chrisbanes.insetter.applyInsetter

class PrivacyAndTermsController : ViewLifecycleController() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View {
        return ControllerOnboardPrivacyAndTermsBinding.inflate(inflater, container, false).apply {
            topAppBar.applyInsetter {
                type(statusBars = true) {
                    margin()
                }
            }

            root.applyInsetter {
                type(navigationBars = true) {
                    margin()
                }
            }

            topAppBar.setNavigationOnClickListener {
                router.popController(this@PrivacyAndTermsController)
            }

            with(webView) {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        request?.url?.let { uri ->
                            CustomTabsIntent.Builder()
                                .build()
                                .intent
                                .setData(uri)
                                .let(this@PrivacyAndTermsController::startActivity)
                        }

                        return true // do not ever navigate inside the webview
                    }
                }

                settings.javaScriptEnabled = false
                loadUrl(context.getString(R.string.filename_privacy_and_terms))
            }
        }.root
    }
}
