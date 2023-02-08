package de.hpi.etranslation.feature.dashboard.usecase

import de.hpi.etranslation.data.DocumentsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CancelTranslationRequestsUseCase @Inject constructor(
    private val database: DocumentsDatabase,
) {

    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        database.transaction {
            database.requestsQueries
                .getUnsentRequests()
                .executeAsList()
                .forEach { request ->
                    database.requestsQueries.deleteByLocalId(request.local_id)
                }
        }
    }
}
