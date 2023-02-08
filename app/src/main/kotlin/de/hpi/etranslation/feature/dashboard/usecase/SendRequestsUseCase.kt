package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.unwrap
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.feature.dashboard.EtranslationService
import de.hpi.etranslation.feature.dashboard.ResourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendRequestsUseCase @Inject constructor(
    private val database: DocumentsDatabase,
    private val resourceRepository: ResourceRepository,
    private val inferDocumentLanguagePartial: InferDocumentLanguagePartial,
    private val etranslationService: EtranslationService,
) {
    suspend operator fun invoke(): Outcome {
        val requests = withContext(Dispatchers.IO) {
            database.requestsQueries
                .getUnsentRequests()
                .executeAsList()
        }

        var successCount = 0
        var failureCount = 0

        Log.i("HPI", "Found ${requests.size} requests to send")
        requests.forEach { request ->
            Log.i("HPI", "Uploading document ${request.original_local_id}")

            val original = withContext(Dispatchers.IO) {
                database.documentsQueries
                    .getByLocalId(request.original_local_id)
                    .executeAsOne()
            }

            val (fromLang, _) = inferDocumentLanguagePartial
                .flow
                .first()
                .invoke(original.lang)

            val fhir = resourceRepository.getByLocalId(request.original_local_id)
                .onFailure { err ->
                    Log.e("HPI", "Failed to read fhir resource: $err")
                    failureCount += 1
                    delay(500)
                    return@forEach
                }
                .unwrap()

            etranslationService.translateResource(
                resource = fhir,
                fromLang = fromLang,
                toLang = request.target_lang,
            ).onFailure {
                Log.i("HPI", "Failed to submit translation request ${request.local_id}")
                failureCount += 1
            }.onSuccess { requestId ->
                Log.i("HPI", "request id is $requestId")
                successCount += 1

                withContext(Dispatchers.IO) {
                    database.requestsQueries.setRequestIdByLocalId(
                        local_id = request.local_id,
                        request_id = requestId,
                    )
                }
            }

            delay(500)
        }

        return Outcome(
            successCount = successCount,
            failureCount = failureCount,
        )
    }

    data class Outcome(
        val successCount: Int,
        val failureCount: Int,
    )
}
