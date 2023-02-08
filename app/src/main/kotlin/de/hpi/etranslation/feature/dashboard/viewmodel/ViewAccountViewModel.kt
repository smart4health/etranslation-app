package de.hpi.etranslation.feature.dashboard.viewmodel

import android.content.Context
import android.content.Intent
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.unwrap
import com.squareup.sqldelight.runtime.coroutines.asFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.DeleteDeprecatedResourcesUseCase
import de.hpi.etranslation.feature.dashboard.usecase.FindS4HDisplayNameUseCase
import de.hpi.etranslation.feature.dashboard.usecase.LogOutS4hUseCase
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Assumes usage of the s4h account
 */
class ViewAccountViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    @ApplicationCoroutineScope
    private val applicationScope: CoroutineScope,
    val d4lClient: AsyncData4LifeClient,
    val findS4HDisplayNameUseCase: FindS4HDisplayNameUseCase,
    val logOutS4hUseCase: LogOutS4hUseCase,
    val documentsDatabase: DocumentsDatabase,
    private val deleteDeprecatedResourcesUseCase: DeleteDeprecatedResourcesUseCase,
) {

    private val _events = Channel<Event>()
    val events: ReceiveChannel<Event> = _events

    private val _isLoading = MutableStateFlow(false)
    val viewState = documentsDatabase.accountsQueries
        .getByAccountType(AccountType.S4H)
        .asFlow()
        .map { query ->
            withContext(Dispatchers.IO) {
                query.executeAsOne()
            }
        }
        .combine(_isLoading) { account, isLoading ->
            ViewState(
                displayName = account.display_name,
                isError = account.has_error,
                isLoading = isLoading,
            )
        }

    init {
        viewModelScope.launch {
            val account = withContext(Dispatchers.IO) {
                // weirdly redundant with viewstate
                documentsDatabase.accountsQueries
                    .getByAccountType(AccountType.S4H)
                    .executeAsOne()
            }

            val newDisplayName = if (account.display_name == null) {
                findS4HDisplayNameUseCase().get()
            } else null

            val clientId = d4lClient.getClientId()

            withContext(Dispatchers.IO) {
                documentsDatabase.transaction {
                    if (newDisplayName != null)
                        documentsDatabase.accountsQueries
                            .updateDisplayNameByAccountType(newDisplayName, AccountType.S4H)

                    clientId.onFailure {
                        documentsDatabase.accountsQueries
                            .updateErrorByAccountType(true, AccountType.S4H)
                    }
                }
            }
        }
    }

    fun onFixAccount(context: Context) = viewModelScope.launch {
        _isLoading.value = true
        d4lClient.loginIntent()
            .let(Event::LoginIntent)
            .let { _events.send(it) }
    }

    suspend fun onRemoveAccount() = applicationScope.launch {
        logOutS4hUseCase()
    }.join()

    suspend fun deleteDeprecatedResources() = withContext(applicationScope.coroutineContext) {
        deleteDeprecatedResourcesUseCase()
    }

    fun onLauncherDone(loginResult: Result<Intent, Intent?>) = viewModelScope.launch {
        loginResult.onSuccess { authData ->
            d4lClient.finishLogin(authData)
            val account = withContext(Dispatchers.IO) {
                documentsDatabase.accountsQueries
                    .getByAccountType(AccountType.S4H)
                    .executeAsOne()
            }

            // SAFETY: this is the handler for a successful login, so a client id
            //         should be available
            val newId = d4lClient.getClientId().unwrap()

            if (account.remote_id != newId) {
                d4lClient.logout()
                _events.send(Event.WrongAccount)
            } else {
                withContext(Dispatchers.IO) {
                    documentsDatabase.accountsQueries
                        .updateErrorByAccountType(false, AccountType.S4H)
                }
            }
        }.onFailure { _ ->
            _events.send(Event.DidNotLogin)
        }

        _isLoading.value = false
    }

    data class ViewState(
        val displayName: String?,
        val isError: Boolean,
        val isLoading: Boolean,
    )

    sealed class Event {
        data class LoginIntent(val intent: Intent) : Event()

        object DidNotLogin : Event()

        object WrongAccount : Event()
    }

    @AssistedFactory
    interface Factory : ScopeFactory<ViewAccountViewModel>
}
