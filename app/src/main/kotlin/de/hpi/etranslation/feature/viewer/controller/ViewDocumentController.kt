package de.hpi.etranslation.feature.viewer.controller

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.asTransaction
import com.bluelinelabs.conductor.viewpager2.RouterStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.ViewLifecycleController
import de.hpi.etranslation.asFlag
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.GetSelectedOriginalsAndTranslations
import de.hpi.etranslation.databinding.ControllerDashboardViewDocumentBinding
import de.hpi.etranslation.entryPoint
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguagePartial
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewDocumentController(
    bundle: Bundle,
) : ViewLifecycleController(bundle) {

    private val entryPoint by entryPoint<MyEntryPoint>()

    private val originalId by lazy {
        args.getString("ORIGINAL_ID")!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?,
    ): View = ControllerDashboardViewDocumentBinding.inflate(inflater, container, false).apply {
        // Clone the background drawable for the status bar scrim
        appBarLayout.statusBarForeground = appBarLayout.background.constantState!!.newDrawable()

        viewPager.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        topAppBar.setNavigationOnClickListener {
            router.popController(this@ViewDocumentController)
        }

        requireViewLifecycleOwner.lifecycleScope.launch {
            val documents = withContext(Dispatchers.IO) {
                entryPoint.documentsDatabase
                    .documentsQueries
                    .getSelectedOriginalsAndTranslations()
                    .executeAsList()
            }

            val (original, translations) = documents.partition { doc ->
                doc.original_record_id == null
            }

            val sortedDocuments = original + translations.sortedBy { it.lang }

            topAppBar.title = sortedDocuments.first {
                it.original_record_id == null
            }.title

            val inferrer = entryPoint.inferDocumentLanguagePartial.flow.first()

            val langs = sortedDocuments
                .map(GetSelectedOriginalsAndTranslations::lang)
                .map(inferrer::invoke)

            val adapter = object : RouterStateAdapter(this@ViewDocumentController) {
                override fun configureRouter(router: Router, position: Int) {
                    if (!router.hasRootController()) when (sortedDocuments[position].resource_type) {
                        "DocumentReference" ->
                            ViewPdfController.withId(sortedDocuments[position].local_id)
                        else ->
                            ViewTranslationController.withLocalId(sortedDocuments[position].local_id)
                    }.asTransaction().let(router::setRoot)
                }

                override fun getItemCount(): Int {
                    return sortedDocuments.size
                }
            }

            viewPager.adapter = adapter

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = langs[position].first.asFlag()
            }.attach()

            viewPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        val scrollable = adapter.getRouter(position)
                            ?.backstack
                            ?.lastOrNull()
                            ?.controller as? HasScrollable

                        if (scrollable === null)
                            Log.e(this@ViewDocumentController::class.simpleName, "Scrollable is null")

                        scrollable
                            ?.getId()
                            ?.let(appBarLayout::setLiftOnScrollTargetViewId)
                    }
                },
            )
        }
    }.root

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MyEntryPoint {
        val documentsDatabase: DocumentsDatabase

        val inferDocumentLanguagePartial: InferDocumentLanguagePartial
    }

    companion object {
        fun withOriginalId(id: String) = bundleOf(
            "ORIGINAL_ID" to id,
        ).let(::ViewDocumentController)
    }
}
