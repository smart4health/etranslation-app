package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import care.data4life.fhir.r4.model.DocumentReference
import care.data4life.fhir.r4.model.DomainResource
import care.data4life.fhir.r4.model.Extension
import com.github.michaelbull.result.coroutines.binding.binding
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeprecateRecordUseCase @Inject constructor(
    private val d4lClient: AsyncData4LifeClient,
) {

    suspend operator fun invoke(recordId: String, reason: String) = binding<Unit, Throwable> {
        val record = d4lClient.downloadRecord<DomainResource>(recordId).bind()

        record.resource.addExtension(
            Extension("https://etranslation.smart4health.eu/fhir-extension/deprecated").apply {
                valueString = reason
            },
        )

        when (val r = record.resource) {
            is DocumentReference -> {
                r.description = (r.description ?: "").plus(" (deprecated)")
            }
        }

        d4lClient.update(
            recordId = recordId,
            resource = record.resource,
            annotations = record.annotations + "etranslated-deprecated",
        ).bind()

        Log.i("HPI", "Deprecated $recordId")
    }
}
