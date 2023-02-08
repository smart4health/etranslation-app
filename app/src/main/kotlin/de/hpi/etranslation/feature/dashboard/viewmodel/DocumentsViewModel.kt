package de.hpi.etranslation.feature.dashboard.viewmodel

import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.Lang
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.combine3
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.asListAdapter
import de.hpi.etranslation.feature.dashboard.DashboardEvent
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguagePartial
import de.hpi.etranslation.feature.dashboard.usecase.InferDocumentLanguageUseCase
import de.hpi.etranslation.feature.dashboard.usecase.RefreshUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class DocumentsViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    private val database: DocumentsDatabase,
    private val langAdapter: EnumColumnAdapter<Lang>,
    inferDocumentLanguagePartial: InferDocumentLanguagePartial,
    private val dashboardEventSender: @JvmSuppressWildcards SendChannel<DashboardEvent>,
    private val refreshUseCase: RefreshUseCase,
    refreshingFlow: StateFlow<Boolean>,
) {

    val viewState: SharedFlow<ViewState> = database
        .documentsQueries
        .getDocumentGroups()
        .asFlow()
        .map { query ->
            withContext(Dispatchers.IO) {
                query.executeAsList() to database.selectionsQueries.count().executeAsOne()
            }
        }
        .combine3(
            inferDocumentLanguagePartial.flow,
            refreshingFlow,
        ) { (groupedViews, count), inferrer, isRefreshing ->

            val countAccounts = groupedViews
                .distinctBy { it.account_id }
                .count()

            val documents = groupedViews.map { groupedView ->
                val translations = groupedView.translated_langs
                    .let(langAdapter.asListAdapter()::decode)

                val inProgress = groupedView.uploaded_langs
                    .let(langAdapter.asListAdapter()::decode)

                val (originalLang, source) = inferrer(groupedView.original_lang)

                Document(
                    originalRecordId = groupedView.original_record_id,
                    title = groupedView.original_title,
                    resourceType = groupedView.resource_type,
                    originalLang = originalLang,
                    originalLangSource = source,
                    translations = translations,
                    inProgress = inProgress,
                    date = groupedView.resource_date,
                    selectionState = when {
                        groupedView.processing_langs.isNotEmpty() -> SelectionState.PROCESSING
                        groupedView.is_selected > 0 && count == 1L -> SelectionState.EXPANDED
                        groupedView.is_selected > 0 && count > 1 -> SelectionState.SELECTED
                        else -> SelectionState.ENABLED
                    },
                    accountType = groupedView.account_type,
                )
            }

            ViewState(
                documents = documents,
                selectedCount = count.toInt(),
                isRefreshing = isRefreshing,
                countAccounts = countAccounts,
            )
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    fun onDocumentAction(originalRecordId: String, action: Action) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (action) {
                    Action.CLICKED -> database.transaction {
                        if (database.selectionsQueries.getSelectedByRecordId(originalRecordId)
                                .executeAsOneOrNull() == null
                        )
                            database.selectionsQueries.selectByRecordId(originalRecordId)
                        else
                            database.selectionsQueries.deselectByRecordId(originalRecordId)
                    }
                    Action.VIEW -> dashboardEventSender.send(DashboardEvent.View(originalRecordId))
                    Action.OVERRIDE_LANGUAGE -> dashboardEventSender.send(DashboardEvent.OverrideLanguage)
                    Action.LONG_CLICKED -> database.transaction {
                        database.selectionsQueries.deselectAll()
                        database.selectionsQueries.selectByRecordId(originalRecordId)
                    }
                }
            }
        }
    }

    fun onFaq() {
        viewModelScope.launch {
            dashboardEventSender.send(DashboardEvent.Faq)
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.selectionsQueries.deselectAll()
            }
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            refreshUseCase()
        }
    }

    fun onTranslate() {
        viewModelScope.launch {
            dashboardEventSender.send(DashboardEvent.Translate)
        }
    }

    enum class Action {
        CLICKED,
        VIEW,
        OVERRIDE_LANGUAGE,
        LONG_CLICKED,
    }

    data class ViewState(
        val documents: List<Document>,
        val selectedCount: Int,
        val isRefreshing: Boolean,
        val countAccounts: Int,
    )

    data class Document(
        val originalRecordId: String,
        val title: String,
        val resourceType: String,
        val originalLang: Lang,
        val originalLangSource: InferDocumentLanguageUseCase.Source,
        val translations: List<Lang>,
        val inProgress: List<Lang>,
        val date: Instant,
        val selectionState: SelectionState,
        val accountType: AccountType,
    )

    enum class SelectionState {
        ENABLED,
        SELECTED,
        EXPANDED,
        PROCESSING,
    }

    @AssistedFactory
    interface Factory : ScopeFactory<DocumentsViewModel>
}
