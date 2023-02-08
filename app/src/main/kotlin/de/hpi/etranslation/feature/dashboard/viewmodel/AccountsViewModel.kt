package de.hpi.etranslation.feature.dashboard.viewmodel

import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.feature.dashboard.AccountsAdapter
import de.hpi.etranslation.feature.dashboard.DashboardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountsViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    private val dashboardEventSender: @JvmSuppressWildcards SendChannel<DashboardEvent>,
    private val database: DocumentsDatabase,
) {

    val viewState: SharedFlow<List<AccountUiModel>> = database
        .accountsQueries
        .getAll()
        .asFlow()
        .map { query ->
            withContext(Dispatchers.IO) {
                query.executeAsList()
            }.map { account ->
                AccountUiModel(
                    id = account.local_id,
                    displayName = account.display_name,
                    accountType = account.type,
                    hasError = account.has_error,
                )
            }
        }
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    fun onItemSelected(
        id: String,
        itemAction: AccountsAdapter.ItemAction,
    ) = when (itemAction) {
        AccountsAdapter.ItemAction.CLICK -> viewModelScope.launch {
            dashboardEventSender.send(DashboardEvent.ViewAccount)
        }
    }

    fun onAddAccount() {
        viewModelScope.launch {
            dashboardEventSender.send(DashboardEvent.AddAccount)
        }
    }

    data class AccountUiModel(
        val id: String,
        val displayName: String?,
        val accountType: AccountType,
        val hasError: Boolean,
    )

    @AssistedFactory
    interface Factory : ScopeFactory<AccountsViewModel>
}
