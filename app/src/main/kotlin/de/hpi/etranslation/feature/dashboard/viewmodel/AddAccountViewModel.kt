package de.hpi.etranslation.feature.dashboard.viewmodel

import android.content.Intent
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.FindS4HDisplayNameUseCase
import de.hpi.etranslation.feature.dashboard.usecase.RefreshUseCase
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AddAccountViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    private val d4lClient: AsyncData4LifeClient,
    private val database: DocumentsDatabase,
    @ApplicationCoroutineScope
    private val applicationScope: CoroutineScope,
    private val refreshUseCase: RefreshUseCase,
    private val findS4HDisplayNameUseCase: FindS4HDisplayNameUseCase,
) {

    private val _events = Channel<Event>()
    val events: ReceiveChannel<Event> = _events

    private val _d4lLoggedIn = MutableStateFlow(false)
    private val _s4hLoading = MutableStateFlow(false)

    val viewState: SharedFlow<ViewState> = _d4lLoggedIn
        .combine(_s4hLoading) { d4lLoggedIn, s4hLoading ->
            ViewState(
                s4hMaxed = d4lLoggedIn,
                s4hLoading = s4hLoading,
            )
        }
        .shareIn(viewModelScope, started = SharingStarted.WhileSubscribed())

    init {
        viewModelScope.launch {
            _d4lLoggedIn.value = d4lClient.isLoggedIn().getOr(false)
        }
    }

    fun onAddS4hAccount() {
        viewModelScope.launch {
            _s4hLoading.value = true
            _events.send(Event.LAUNCH_S4H)
        }
    }

    fun onLaunched() {
    }

    fun onLauncherDone(loginResult: Result<Intent, Intent?>) {
        viewModelScope.launch {
            _s4hLoading.value = false
        }

        loginResult.onSuccess { authData ->
            _d4lLoggedIn.value = true
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    d4lClient.finishLogin(authData)
                    database.accountsQueries.insert(
                        local_id = UUID.randomUUID().toString(),
                        type = AccountType.S4H,
                        display_name = findS4HDisplayNameUseCase().get(),
                        // SAFETY: this is the handler for a successful login, so a client id
                        //         should be available
                        remote_id = d4lClient.getClientId().getOrThrow(),
                        has_error = false,
                    )
                }

                applicationScope.launch {
                    refreshUseCase()
                }

                _events.send(Event.LOGIN_SUCCESS)
            }
        }.onFailure { _ ->
            viewModelScope.launch {
                _events.send(Event.LOGIN_FAILED)
            }
        }
    }

    data class ViewState(
        val s4hMaxed: Boolean,
        // not super convinced about putting loading state here tbh
        val s4hLoading: Boolean,
    )

    enum class Event {
        LAUNCH_S4H,
        LOGIN_FAILED,
        LOGIN_SUCCESS,
    }

    @AssistedFactory
    interface Factory : ScopeFactory<AddAccountViewModel>
}
