package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import care.data4life.fhir.r4.FhirR4Parser
import care.data4life.fhir.r4.model.DocumentReference
import care.data4life.fhir.r4.model.DomainResource
import care.data4life.fhir.r4.model.Element
import care.data4life.fhir.r4.model.Extension
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.GetSentRequestsByAccountId
import de.hpi.etranslation.feature.dashboard.EtranslationService
import de.hpi.etranslation.feature.dashboard.ResourceRepository
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetTranslationUseCase @Inject constructor(
    private val database: DocumentsDatabase,
    private val resourceRepository: ResourceRepository,
    private val etranslationService: EtranslationService,
    private val d4lClient: AsyncData4LifeClient,
    private val fhirR4Parser: FhirR4Parser,
    private val deprecateRecordUseCase: DeprecateRecordUseCase,
) {
    /**
     * Download one (1) translation if it's there and store it,
     * deleting the request locally and remotely as well,
     * upload to the chdp
     *
     * (Operates under the assumption of 1 logged in CHDP account)
     *
     * @return true if success, false if document not ready
     */
    suspend operator fun invoke(request: GetSentRequestsByAccountId): Boolean {
        val newId = UUID.randomUUID().toString()

        val bodyText = (etranslationService.getTranslation(request.request_id) ?: return false)
            .invoke()

        resourceRepository.writeRawResourceByLocalId(newId, bodyText)

        val domainResource = fhirR4Parser.toFhir(DomainResource::class.java, bodyText)

        val original = withContext(Dispatchers.IO) {
            database.documentsQueries
                .getByLocalId(request.original_local_id)
                .executeAsOne()
        }

        val resourceDate = when (domainResource) {
            is DocumentReference -> {
                domainResource.id = null
                domainResource.identifier = mutableListOf()
                domainResource.description += " (${request.target_lang})"

                domainResource.date?.toDate()?.toInstant() ?: Instant.now()
            }
            else -> error("Unknown resource type ${domainResource.resourceType}")
        }

        domainResource.addExtension(
            Extension("https://etranslation.smart4health.eu/fhir-extension/lang-pair").apply {
                addExtension(
                    Extension("from-lang").apply {
                        valueString = request.lang?.toString()
                    },
                )
                addExtension(
                    Extension("to-lang").apply {
                        valueString = request.target_lang.toString()
                    },
                )
            },
        )
        domainResource.addExtension(
            Extension("https://etranslation.smart4health.eu/fhir-extension/original-record-id").apply {
                valueString = original.record_id
            },
        )

        Log.i("HPI", "Uploading with ${domainResource.id}")
        val newRecord = when (val r = d4lClient.create(domainResource, listOf("etranslated"))) {
            is Ok -> r.value
            is Err -> {
                Log.e("HPI", "Failed to upload", r.error)

                return false
            }
        }

        withContext(Dispatchers.IO) {
            database.documentsQueries
                .getUploadedTranslationsByOriginalRecordIdAndLang(
                    original_record_id = original.record_id,
                    lang = request.target_lang,
                )
                .executeAsList()
                .forEach { doc ->
                    deprecateRecordUseCase(
                        recordId = doc.record_id,
                        reason = "new translation available",
                    )
                }

            database.transaction {
                database.documentsQueries.deleteTranslationsByOriginalRecordIdAndLang(
                    original_record_id = original.record_id,
                    lang = request.target_lang,
                )
                database.documentsQueries.insert(
                    local_id = newId,
                    record_id = newRecord.identifier,
                    original_record_id = original.record_id,
                    updated_at = Instant.now(),
                    chdp_fetched_at = null,
                    lang = request.target_lang,
                    resource_type = original.resource_type,
                    resource_date = resourceDate,
                    title = original.title + " (${request.target_lang})",
                    is_supported = true,
                    account_id = request.account_id,
                )
                database.requestsQueries.deleteByLocalId(request.local_id)
            }
        }

        etranslationService.deleteTranslation(request.request_id)

        return true
    }

    private fun Element.addExtension(newExtension: Extension) {
        if (extension == null) {
            extension = mutableListOf()
        }

        // NULL SAFETY: extension should be set if null above
        extension!!.add(newExtension)
    }
}

fun DomainResource.addExtension(newExtension: Extension) {
    if (extension == null) {
        extension = mutableListOf()
    }

    // NULL SAFETY: extension should be set if null above
    extension!!.add(newExtension)
}
