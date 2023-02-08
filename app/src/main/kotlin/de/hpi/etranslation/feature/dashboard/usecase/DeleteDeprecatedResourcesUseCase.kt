package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import care.data4life.fhir.r4.model.DomainResource
import care.data4life.sdk.lang.D4LException
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.onFailure
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// assumes d4l account
@Singleton
class DeleteDeprecatedResourcesUseCase @Inject constructor(
    private val d4lClient: AsyncData4LifeClient,
    private val database: DocumentsDatabase,
) {
    /**
     * @return count of deleted resources or exception
     */
    suspend operator fun invoke(): Result<Int, D4LException> = binding {
        d4lClient.fetchAll(resourceType = DomainResource::class.java)
            .map { it.bind() }
            .toList()
            .flatten()
            .filter { "etranslated-deprecated" in it.annotations }
            .map { record ->
                withContext(Dispatchers.IO) {
                    database.documentsQueries.deleteByRecordId(record.identifier)
                }
                d4lClient.delete(record.identifier).onFailure { ex ->
                    Log.e("HPI", "Failed to delete ${record.identifier}", ex)
                }
            }
            .count { it is Ok && it.value }
    }
}
