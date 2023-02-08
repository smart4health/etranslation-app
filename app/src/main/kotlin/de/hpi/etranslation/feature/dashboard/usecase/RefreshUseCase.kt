package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import com.github.michaelbull.result.onFailure
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.data.DocumentsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshUseCase @Inject constructor(
    private val refreshingMutableFlow: MutableStateFlow<Boolean>,
    private val getChdpDocumentsUseCase: GetChdpDocumentsUseCase,
    private val syncTranslationsUseCase: SyncTranslationsUseCase,
    private val documentsDatabase: DocumentsDatabase,
) {
    suspend operator fun invoke() {
        refreshingMutableFlow.value = true

        withContext(Dispatchers.IO) {
            documentsDatabase.accountsQueries
                .getAll()
                .executeAsList()
        }.forEach { account ->
            when (account.type) {
                AccountType.S4H -> {
                    getChdpDocumentsUseCase().onFailure { t ->
                        Log.e("HPI", "Failed to fetch from chdp", t)
                        withContext(Dispatchers.IO) {
                            documentsDatabase.accountsQueries.updateErrorByAccountType(true, AccountType.S4H)
                        }
                    }
                    syncTranslationsUseCase()
                }
            }
        }

        refreshingMutableFlow.value = false
    }
}
