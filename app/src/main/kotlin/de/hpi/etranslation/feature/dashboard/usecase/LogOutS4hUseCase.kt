package de.hpi.etranslation.feature.dashboard.usecase

import android.util.Log
import com.github.michaelbull.result.onFailure
import de.hpi.etranslation.AccountType
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.feature.dashboard.PdfRepository
import de.hpi.etranslation.feature.dashboard.ResourceRepository
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This use case logs out the current s4h user
 * - remove pdfs
 * - remove resources
 * - clear requests
 * - execute d4l logout
 *
 * Assumes that the d4l sdk is logged in and that we
 * have an s4h account in the accounts table
 */
@Singleton
class LogOutS4hUseCase @Inject constructor(
    private val d4lClient: AsyncData4LifeClient,
    private val resourceRepository: ResourceRepository,
    private val pdfRepository: PdfRepository,
    private val database: DocumentsDatabase,
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val account = database.accountsQueries
            .getByAccountType(AccountType.S4H)
            .executeAsOne()

        // clean up filesystem
        database.documentsQueries
            .getByAccountId(account.local_id)
            .executeAsList()
            .forEach { documents ->
                resourceRepository.deleteByLocalId(documents.local_id)
                pdfRepository.deleteByLocalId(documents.local_id)
            }

        // delete the accounts documents and cascade to requests
        database.documentsQueries.deleteByAccountId(account.local_id)

        d4lClient.logout().onFailure { ex ->
            Log.e(
                this@LogOutS4hUseCase::class.simpleName,
                "Failed to log out of d4l",
                ex,
            )
        }

        database.accountsQueries.deleteByAccountId(account.local_id)
    }
}
