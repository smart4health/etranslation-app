package de.hpi.etranslation.feature.dashboard.usecase

import care.data4life.fhir.r4.model.Patient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.toResultOr
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assumes a logged in d4l account is present
 */
@Singleton
class FindS4HDisplayNameUseCase @Inject constructor(
    private val d4lClient: AsyncData4LifeClient,
) {
    suspend operator fun invoke(): Result<String, Throwable> = binding {
        d4lClient.fetchAll(resourceType = Patient::class.java)
            .map { x -> x.bind() }
            .firstOrNull { page ->
                page.firstOrNull { it.resource.resourceType == "Patient" } != null
            }
            .toResultOr { Error("No patient found") }
            .bind()
            .mapNotNull { record ->
                val name = (record.resource as? Patient)
                    ?.name
                    ?.first()

                if (name?.text.isNullOrBlank() && name != null)
                    (name.given ?: listOf())
                        .plus(name.family)
                        .filterNotNull()
                        .joinToString(separator = " ")
                else
                    null
            }
            .firstOrNull()
            .toResultOr { Error("Not found") }
            .bind()
    }
}
