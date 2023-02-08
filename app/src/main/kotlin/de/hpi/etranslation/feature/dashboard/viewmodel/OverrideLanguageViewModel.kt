package de.hpi.etranslation.feature.dashboard.viewmodel

import android.util.Log
import com.github.michaelbull.result.onFailure
import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.Lang
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.combine3
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.DeprecateRecordUseCase
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguagePartial
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverrideLanguageViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    @ApplicationCoroutineScope
    private val applicationScope: CoroutineScope,
    private val documentsDatabase: DocumentsDatabase,
    inferDocumentLanguagePartial: InferDocumentLanguagePartial,
    private val deprecateRecordUseCase: DeprecateRecordUseCase,
) {

    private val targetLangFlow: MutableStateFlow<Lang?> = MutableStateFlow(null)

    val viewState: SharedFlow<ViewState> = documentsDatabase
        .documentsQueries
        .getSelectedOriginalsAndTranslations()
        .asFlow()
        .map { query ->
            withContext(Dispatchers.IO) {
                query.executeAsList()
            }
        }
        .combine3(
            targetLangFlow,
            inferDocumentLanguagePartial.flow,
        ) { docs, targetLang, inferrer ->

            val (originalDocs, translations) = docs.partition { doc ->
                doc.original_record_id == null
            }

            val originalDoc = originalDocs.first()

            val (originalLang, originalSource) = inferrer(originalDoc.lang)

            ViewState(
                originalLang = originalLang,
                originalSource = originalSource,
                targetLang = targetLang,
                numExistingTranslations = translations.size,
            )
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    /**
     * Runs on application scope because records must be
     * deprecated in the CHDP
     */
    suspend fun onOverride() = applicationScope.launch {
        val targetLang = targetLangFlow.value

        if (targetLang == null) {
            Log.e("HPI", "Tried to create translation request without target lang")
            return@launch
        }

        withContext(Dispatchers.IO) {
            val (originals, translations) = documentsDatabase
                .documentsQueries
                .getSelectedOriginalsAndTranslations()
                .executeAsList()
                .partition { doc -> doc.original_record_id == null }

            val original = originals.single()

            translations.forEach { translation ->
                if (translation.record_id == null) {
                    Log.i(
                        "HPI",
                        "Can't deprecated local_id ${translation.local_id}, no record_id found",
                    )
                } else deprecateRecordUseCase(
                    recordId = translation.record_id,
                    reason = "source language invalidated",
                ).onFailure { t ->
                    Log.e("HPI", "Failed to deprecate ${translation.record_id}", t)
                }
            }

            documentsDatabase.transaction {
                documentsDatabase
                    .documentsQueries
                    .setDocumentLangByLocalId(targetLang, original.local_id)

                documentsDatabase
                    .documentsQueries
                    .deleteTranslationsByOriginalRecordId(original.record_id)
            }
        }
    }.join()

    fun onSelected(lang: Lang) {
        targetLangFlow.value = lang
    }

    fun onClose() {
        targetLangFlow.value = null
    }

    data class ViewState(
        val originalLang: Lang,
        val originalSource: InferDocumentLanguageUseCase.Source,
        val targetLang: Lang?,
        val numExistingTranslations: Int,
    )

    @AssistedFactory
    interface Factory : ScopeFactory<OverrideLanguageViewModel>
}
