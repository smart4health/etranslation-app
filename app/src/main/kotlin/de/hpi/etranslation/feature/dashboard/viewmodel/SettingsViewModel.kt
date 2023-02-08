package de.hpi.etranslation.feature.dashboard.viewmodel

import androidx.datastore.core.DataStore
import com.github.michaelbull.result.get
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.Lang
import de.hpi.etranslation.MainEvent
import de.hpi.etranslation.ScopeFactory
import de.hpi.etranslation.Settings
import de.hpi.etranslation.Theme
import de.hpi.etranslation.combine3
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.LogOutS4hUseCase
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import de.hpi.etranslation.toLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SettingsViewModel @AssistedInject constructor(
    @Assisted
    private val viewModelScope: CoroutineScope,
    @ApplicationCoroutineScope
    private val applicationScope: CoroutineScope,
    localeFlow: @JvmSuppressWildcards Flow<List<Locale>>,
    private val settingsDataStore: DataStore<Settings>,
    private val d4lClient: AsyncData4LifeClient,
    private val mainEventSender: @JvmSuppressWildcards SendChannel<MainEvent>,
    private val logOutS4hUseCase: LogOutS4hUseCase,
    private val database: DocumentsDatabase,
) {

    private val d4lId = MutableStateFlow<String?>(null)

    val viewState: SharedFlow<ViewState> = localeFlow
        .combine3(settingsDataStore.data, d4lId) { locale, settings, d4lId ->
            ViewState(
                deviceLang = locale.mapNotNull(Locale::toLang).firstOrNull(),
                langOverride = settings.langOverride?.toLang(),
                theme = settings.theme,
                d4lId = d4lId,
            )
        }.shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    init {
        viewModelScope.launch {
            d4lId.value = d4lClient.getClientId().get()
        }
    }

    fun setLangOverride(lang: Lang?) {
        viewModelScope.launch {
            settingsDataStore.updateData { settings ->
                settings.copy(langOverride = lang?.toString())
            }
        }
    }

    fun setTheme(theme: Theme) {
        viewModelScope.launch {
            settingsDataStore.updateData { settings ->
                settings.copy(theme = theme)
            }
        }
    }

    fun breakAccount() = viewModelScope.launch {
        d4lClient.logout()
    }

    fun revokeConsent() = applicationScope.launch {
        val s4hAccounts = withContext(Dispatchers.IO) {
            database
                .accountsQueries
                .getByAccountType(AccountType.S4H)
                .executeAsList()
        }

        if (s4hAccounts.isNotEmpty())
            logOutS4hUseCase()

        settingsDataStore.updateData {
            it.copy(isConsented = false)
        }

        mainEventSender.send(MainEvent.LOGGED_OUT)
    }

    data class ViewState(
        val deviceLang: Lang?,
        val langOverride: Lang?,
        val theme: Theme,
        val d4lId: String?,
    )

    @AssistedFactory
    interface Factory : ScopeFactory<SettingsViewModel>
}
