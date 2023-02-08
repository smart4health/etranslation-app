package de.hpi.etranslation.feature.dashboard.viewmodel

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hpi.etranslation.Lang
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.combine3
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.GetSelectedOriginalsAndTranslations
import de.hpi.etranslation.data.asListAdapter
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguagePartial
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguageUseCase
import de.hpi.etranslation.feature.dashboard.worker.SendRequestsWorker
import de.hpi.etranslation.feature.dashboard.worker.SyncTranslationsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.SortedSet
import java.util.UUID

class StartTranslationViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    @ApplicationCoroutineScope
    private val applicationScope: CoroutineScope,
    private val database: DocumentsDatabase,
    private val langAdapter: EnumColumnAdapter<Lang>,
    private val inferDocumentLanguagePartial: InferDocumentLanguagePartial,
    @ApplicationContext
    private val applicationContext: Context,
) {

    private val selectedLangFlow: MutableStateFlow<Lang?> =
        MutableStateFlow(null)

    val viewState: SharedFlow<ViewState> = database.documentsQueries
        .getSelectedOriginalsAndTranslations()
        .asFlow()
        .map { query ->
            withContext(Dispatchers.IO) {
                query.executeAsList()
            }
        }
        .combine3(
            inferDocumentLanguagePartial.flow,
            selectedLangFlow,
        ) { documents, inferrer, selected ->
            val documentsWithOriginalLang = documents.map { doc ->
                val (lang, source) = inferrer(doc.lang)

                Triple(doc, lang, source)
            }

            val stats = documentsWithOriginalLang.map { (doc, originalLang, _) ->
                doc to originalLang
            }.calculateStats(selected)

            val originalLangs = documentsWithOriginalLang
                .filter { it.first.original_record_id == null }
                .map(Triple<GetSelectedOriginalsAndTranslations, Lang, InferDocumentLanguageUseCase.Source>::second)
                .toSortedSet()

            ViewState(
                originalLangs = originalLangs,
                selectedLang = selected,
                stats = stats,
            )
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    suspend fun startTranslation() = applicationScope.launch {
        val targetLang = selectedLangFlow.value ?: return@launch

        withContext(Dispatchers.IO) {
            val inferrer = inferDocumentLanguagePartial.flow.first()

            database.transaction {
                database.documentsQueries
                    .getSelectedOriginalsAndTranslations()
                    .executeAsList()
                    .filter { doc -> doc.original_record_id == null }
                    .forEach { original ->

                        val (lang, _) = inferrer(original.lang)

                        database.documentsQueries
                            .setDocumentLangByLocalId(lang, original.local_id)

                        val inProgressLangs = original.in_progress_langs
                            .let(langAdapter.asListAdapter()::decode)

                        if (lang != targetLang && lang !in inProgressLangs) database.requestsQueries.insert(
                            local_id = UUID.randomUUID().toString(),
                            request_id = null,
                            original_local_id = original.local_id,
                            target_lang = targetLang,
                            updated_at = Instant.now(),
                        )
                    }
            }

            database.selectionsQueries.deselectAll()
        }

        val upload = OneTimeWorkRequestBuilder<SendRequestsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val getTranslations = OneTimeWorkRequestBuilder<SyncTranslationsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext)
            .beginWith(upload)
            .then(getTranslations)
            .enqueue()
    }.join()

    fun setSelectedLang(lang: Lang?) {
        selectedLangFlow.value = lang
    }

    private fun List<Pair<GetSelectedOriginalsAndTranslations, Lang>>.calculateStats(selectedLang: Lang?): Stats {
        val symmetricTranslations = hashSetOf<String>()
        val inProgressTranslations = hashSetOf<String>()
        val retranslations = hashSetOf<String>()

        forEach { (doc, originalLang) ->
            when (doc.original_record_id) {
                null -> {
                    // is original
                    if (originalLang == selectedLang) {
                        symmetricTranslations.add(doc.local_id)
                        retranslations.remove(doc.local_id)
                    }

                    if (selectedLang in doc.in_progress_langs.let(langAdapter.asListAdapter()::decode)) {
                        // note: mutually exclusive with symmetric translation
                        inProgressTranslations.add(doc.local_id)
                        retranslations.remove(doc.local_id)
                    }
                }
                else -> {
                    if (
                        doc.original_record_id !in symmetricTranslations &&
                        doc.original_record_id !in inProgressTranslations &&
                        originalLang == selectedLang
                    ) {
                        retranslations.add(doc.original_record_id)
                    }
                }
            }
        }

        return Stats(
            skipSymmetricTranslationCount = symmetricTranslations.size,
            skipInProgressCount = inProgressTranslations.size,
            willBeRetranslatedCount = retranslations.size,
        )
    }

    data class Stats(
        val skipSymmetricTranslationCount: Int,
        val skipInProgressCount: Int,
        val willBeRetranslatedCount: Int,
    )

    data class ViewState(
        val originalLangs: SortedSet<Lang>,
        val selectedLang: Lang?,
        val stats: Stats,
    )

    @AssistedFactory
    interface Factory : ScopeFactory<StartTranslationViewModel>
}
