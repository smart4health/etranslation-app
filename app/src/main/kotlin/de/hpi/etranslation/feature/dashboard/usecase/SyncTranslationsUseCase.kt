package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.GetSentRequestsByAccountId
import de.hpi.etranslation.feature.dashboard.EtranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the etranslation service for ready translations and
 * then pushes to the CHDP with [GetTranslationUseCase]
 *
 * This was built with the assumption of 1 S4H account only
 * and thus does not handle other account types
 */
@Singleton
class SyncTranslationsUseCase @Inject constructor(
    private val database: DocumentsDatabase,
    private val etranslationService: EtranslationService,
    private val getTranslationUseCase: GetTranslationUseCase,
) {
    suspend operator fun invoke(): List<Status> {
        val requests = withContext(Dispatchers.IO) {
            val account = database.accountsQueries
                .getByAccountType(AccountType.S4H)
                .executeAsOne()

            database.requestsQueries
                .getSentRequestsByAccountId(account.local_id)
                .executeAsList()
        }

        Log.i("HPI", "Found ${requests.size} requests to check")
        val statuses =
            etranslationService.checkStatuses(requests.map(GetSentRequestsByAccountId::request_id))
        Log.i("HPI", "statuses: $statuses")

        return requests.map { request ->
            val syncStatus = statuses[request.request_id]?.let { status ->
                when (status.status) {
                    EtranslationService.Status.UNTRANSLATED, EtranslationService.Status.SENT ->
                        Status.NOT_READY
                    EtranslationService.Status.TRANSLATION_ERROR, EtranslationService.Status.SEND_ERROR -> {
                        withContext(Dispatchers.IO) {
                            database.requestsQueries.deleteByLocalId(request.local_id)
                        }

                        Status.ERROR
                    }
                    EtranslationService.Status.TRANSLATED ->
                        if (getTranslationUseCase(request))
                            Status.READY
                        else
                            Status.NOT_READY
                }
            } ?: run {
                Log.i(
                    this@SyncTranslationsUseCase::class.simpleName,
                    "Status not found for request_id ${request.request_id}",
                )
                Status.NO_STATUS
            }

            delay(500)

            syncStatus
        }
    }

    enum class Status {
        NOT_READY,
        NO_STATUS,
        ERROR,
        READY,
    }
}
