package de.hpi.etranslation.feature.dashboard

import android.content.Context
import care.data4life.fhir.r4.FhirR4Parser
import care.data4life.fhir.r4.model.DomainResource
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceRepository @Inject constructor(
    private val d4lClient: AsyncData4LifeClient,
    private val fhirR4Parser: FhirR4Parser,
    @ApplicationContext
    private val context: Context,
    private val database: DocumentsDatabase,
) {

    suspend fun getByLocalId(
        localId: String,
    ): Result<String, Error> = withContext(Dispatchers.IO) {
        binding {
            val localFile = resourceDir().div(localId.fhir)

            if (!localFile.exists()) {
                val recordId = database.documentsQueries
                    .getByLocalId(localId)
                    .executeAsOne()
                    .record_id
                    .toResultOr { Error.NotFound }
                    .bind()

                val resource = d4lClient.downloadRecord<DomainResource>(recordId)
                    .mapError(Error::Other)
                    .bind()
                    .resource
                    .let(fhirR4Parser::fromFhir)

                localFile.writeText(resource)
            }

            localFile.readText()
        }
    }

    /**
     * Ensures a file is deleted, doesn't care if it doesn't exist
     */
    suspend fun deleteByLocalId(
        localId: String,
    ) = withContext(Dispatchers.IO) {
        val localFile = resourceDir().div(localId.fhir)
        if (localFile.exists())
            localFile.delete()
    }

    suspend fun writeRawResourceByLocalId(
        localId: String,
        content: String,
    ) = withContext(Dispatchers.IO) {
        resourceDir().div(localId.fhir).writeText(content)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        resourceDir().deleteRecursively()
    }

    private fun resourceDir(): File = File(context.filesDir, "resources").also { it.mkdirs() }

    private operator fun File.div(name: String) = File(this, name)

    private val String.fhir: String
        get() = "$this.fhir.json"

    sealed class Error {
        object NotFound : Error()
        data class Other(val t: Throwable) : Error()
    }
}
