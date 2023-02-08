package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import care.data4life.fhir.r4.model.DocumentReference
import care.data4life.fhir.r4.model.DomainResource
import care.data4life.fhir.r4.model.Provenance
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIf
import com.github.michaelbull.result.toResultOr
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.Lang
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.InstantAdapter
import de.hpi.etranslation.feature.dashboard.EtranslationService
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import de.hpi.etranslation.toLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.github.michaelbull.result.binding as syncBinding

/**
 * Gets documents from the chdp starting at the last fetch date
 * (found in the database).  As this uses the Smart4Health account
 * type, it assumes that there is only one account, and that it is
 * logged in
 */
@Singleton
class GetChdpDocumentsUseCase @Inject constructor(
    private val data4LifeClient: AsyncData4LifeClient,
    private val database: DocumentsDatabase,
    private val etranslationService: EtranslationService,
) {
    suspend operator fun invoke(): Result<Unit, Throwable> = binding {
        val configuration = etranslationService.getConfiguration()

        val startDate = withContext(Dispatchers.IO) {
            database.documentsQueries
                .getLatestChdpFetchedAt()
                .executeAsOne()
                .max
                ?.let(InstantAdapter::decode)
                ?.atZone(ZoneId.systemDefault())
        }

        val account = withContext(Dispatchers.IO) {
            database.accountsQueries
                .getByAccountType(AccountType.S4H)
                .executeAsOne()
        }

        val fetchedAt = Instant.now()

        withContext(Dispatchers.IO) {
            database.selectionsQueries.deselectAll()
        }

        val inferredOriginalLanguages = mutableMapOf<String, Lang>()
        var count = 0

        data4LifeClient.fetchAll(resourceType = DomainResource::class.java, startDate = startDate)
            .map { it.bind() }
            .collect { page ->
                count += page.size
                page.forEach { record ->
                    Log.i("HPI", "Annotations for ${record.identifier}: ${record.annotations}")

                    if (record.resource.isDeprecated()) {
                        Log.i("HPI", "Found deprecated record ${record.identifier}, skipping")
                        return@forEach
                    }

                    val etranslationExtension = if ("etranslated" in record.annotations) {
                        val etranslationExtension =
                            (record.resource as? DomainResource)
                                ?.readExtensions()
                                ?.onFailure {
                                    Log.i(
                                        "HPI",
                                        "Failed to validate etranslation extensions: ${it.message}",
                                    )
                                }
                                ?.get()
                                ?: return@forEach

                        Log.i(
                            "HPI",
                            "Found a translated record: ${record.identifier} $etranslationExtension",
                        )

                        inferredOriginalLanguages.put(
                            etranslationExtension.originalRecordId,
                            etranslationExtension.fromLang,
                        )?.run {
                            if (this != etranslationExtension.fromLang) Log.e(
                                "HPI",
                                "Contradicting translations, assuming ${etranslationExtension.fromLang} instead of $this",
                            )
                        }

                        etranslationExtension
                    } else null

                    val isResourceTypeSupported =
                        record.resource.resourceType in configuration.resourceTypes

                    val isContentTypeSupported = when (val resource = record.resource) {
                        is DocumentReference ->
                            resource.content.firstOrNull()?.attachment?.contentType == "application/pdf"
                        else -> true
                    }

                    val isSupported = isResourceTypeSupported && isContentTypeSupported

                    withContext(Dispatchers.IO) {
                        when (val resource = record.resource) {
                            is Provenance -> database.documentsQueries.insert(
                                local_id = UUID.randomUUID().toString(),
                                record_id = record.identifier,
                                original_record_id = etranslationExtension?.originalRecordId,
                                updated_at = Instant.now(),
                                chdp_fetched_at = fetchedAt,
                                lang = etranslationExtension?.toLang,
                                resource_type = resource.resourceType,
                                resource_date = resource.occurredDateTime?.toDate()?.toInstant()
                                    ?: Instant.now(),
                                title = "Provenance for ${resource.target.firstOrNull()?.identifier?.value}",
                                is_supported = isSupported,
                                account_id = account.local_id,
                            )
                            is DocumentReference -> database.documentsQueries.insert(
                                local_id = UUID.randomUUID().toString(),
                                record_id = record.identifier,
                                original_record_id = etranslationExtension?.originalRecordId,
                                updated_at = Instant.now(),
                                chdp_fetched_at = fetchedAt,
                                lang = etranslationExtension?.toLang,
                                resource_type = resource.resourceType,
                                resource_date = resource.date?.toDate()?.toInstant()
                                    ?: Instant.now(),
                                title = resource.description ?: "Unknown title",
                                is_supported = isSupported,
                                account_id = account.local_id,
                            )
                            else -> Log.w(
                                this@GetChdpDocumentsUseCase::class.simpleName,
                                "Unknown resourceType ${resource.resourceType}",
                            )
                        }
                    }
                }
            }

        Log.i("HPI", "Found $count starting at $startDate")

        withContext(Dispatchers.IO) {
            inferredOriginalLanguages.forEach { (originalRecordId, lang) ->
                database.transaction {
                    database.documentsQueries.setDocumentLangByRecordId(lang, originalRecordId)
                }
            }
        }
    }

    data class EtranslationExtensions(
        val originalRecordId: String,
        val fromLang: Lang,
        val toLang: Lang,
    )

    private fun DomainResource.readExtensions(): Result<EtranslationExtensions, ValidationError> =
        syncBinding {
            val (fromLang, toLang) = extension
                ?.find {
                    it.url == "https://etranslation.smart4health.eu/fhir-extension/lang-pair"
                }
                .toResultOr { ValidationError("No lang-pair extension found") }
                .bind()
                .extension
                .toResultOr { ValidationError("No lang-pair sub extensions") }
                .bind()
                .let { subExtensions ->
                    syncBinding<Pair<Lang, Lang>, ValidationError> {
                        val fromLang = subExtensions
                            .find { it.url == "from-lang" }
                            .toResultOr { ValidationError("no from-lang sub extension found") }
                            .bind()
                            .valueString
                            ?.let(String::toLang)
                            .toResultOr { ValidationError("failed to parse from-lang valueString") }
                            .bind()

                        val toLang = subExtensions
                            .find { it.url == "to-lang" }
                            .toResultOr { ValidationError("no to-lang sub extension found") }
                            .bind()
                            .valueString
                            ?.let(String::toLang)
                            .toResultOr { ValidationError("failed to parse to-lang valueString") }
                            .bind()

                        fromLang to toLang
                    }
                }
                .bind()

            val originalRecordId = extension
                ?.find { it.url == "https://etranslation.smart4health.eu/fhir-extension/original-record-id" }
                .toResultOr { ValidationError("No original-record-id extension found") }
                .bind()
                .valueString
                .toResultOr { ValidationError("No original-record-id valueString found") }
                .toErrorIf(
                    predicate = { it == "null" },
                    transform = { ValidationError("original-record-id is 'null'") },
                )
                .bind()

            EtranslationExtensions(
                originalRecordId = originalRecordId,
                fromLang = fromLang,
                toLang = toLang,
            )
        }

    private fun DomainResource.isDeprecated(): Boolean =
        (extension ?: emptyList())
            .any { it.url == "https://etranslation.smart4health.eu/fhir-extension/deprecated" }

    private data class ValidationError(val message: String)
}
