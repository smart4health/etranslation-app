package de.hpi.etranslation.lib.chdp

import android.content.Context
import android.content.Intent
import care.data4life.fhir.r4.model.DomainResource
import care.data4life.sdk.Data4LifeClient
import care.data4life.sdk.SdkContract
import care.data4life.sdk.call.Callback
import care.data4life.sdk.call.Fhir4Record
import care.data4life.sdk.lang.D4LException
import care.data4life.sdk.listener.ResultListener
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.threeten.bp.DateTimeUtils
import java.time.ZonedDateTime
import java.util.GregorianCalendar
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.threeten.bp.ZonedDateTime as ABPZonedDateTime

private typealias Page<T> = List<Fhir4Record<T>>

class AsyncData4LifeClient(
    applicationContext: Context,
) {
    init {
        AndroidThreeTen.init(applicationContext)
        Data4LifeClient.init(applicationContext)
    }

    suspend fun loginIntent(): Intent = withContext(Dispatchers.IO) {
        Data4LifeClient.getInstance().getLoginIntent(
            setOf(
                "perm:r",
                "rec:r",
                "rec:w",
                "attachment:r",
                "attachment:w",
                "user:r",
                "user:q",
            ),
        )
    }

    suspend fun finishLogin(authData: Intent) = ioCoroutine { cont ->
        Data4LifeClient.getInstance()
            .finishLogin(
                authData,
                object : care.data4life.sdk.listener.Callback {
                    override fun onSuccess() = cont.resume(Ok(Unit))

                    override fun onError(exception: D4LException) = cont.resume(Err(exception))
                },
            )
    }

    suspend fun isLoggedIn(): Result<Boolean, D4LException> =
        ioCoroutine { cont ->
            Data4LifeClient.getInstance().isUserLoggedIn(
                object : ResultListener<Boolean> {
                    override fun onSuccess(t: Boolean) = cont.resume(Ok(t))

                    override fun onError(exception: D4LException) = cont.resume(Err(exception))
                },
            )
        }

    suspend fun getClientId(): Result<String, Throwable> = withContext(Dispatchers.IO) {
        try {
            Data4LifeClient.getInstance().userId.let(::Ok)
        } catch (t: Throwable) {
            t.let(::Err)
        }
    }

    suspend fun logout(): Result<Unit, D4LException> =
        if (isLoggedIn() == Ok(true))
            ioCoroutine { cont ->
                Data4LifeClient.getInstance().logout(
                    object : care.data4life.sdk.listener.Callback {
                        override fun onSuccess() = cont.resume(Ok(Unit))

                        override fun onError(exception: D4LException) = cont.resume(Err(exception))
                    },
                )
            }
        else Ok(Unit)

    suspend fun <T : DomainResource> downloadRecord(
        id: String,
    ): Result<Fhir4Record<T>, D4LException> = ioCoroutine { cont ->
        Data4LifeClient.getInstance().fhir4.download(id, Data4LifeCallback(cont))
    }

    private suspend fun <T : DomainResource> downloadPage(
        pageNumber: Int,
        pageSize: Int,
        startDate: ABPZonedDateTime?,
        resourceType: Class<T>,
    ): Result<Page<T>, D4LException> = binding {
        ioCoroutine { cont ->
            Data4LifeClient.getInstance().fhir4.search(
                resourceType = resourceType,
                annotations = listOf(),
                pageSize = pageSize,
                offset = pageNumber * pageSize,
                callback = Data4LifeCallback(cont),
                creationDateRange = SdkContract.CreationDateRange(
                    startDate = startDate?.toLocalDate(),
                    endDate = null,
                ),
                updateDateTimeRange = SdkContract.UpdateDateTimeRange(
                    startDateTime = startDate?.toLocalDateTime(),
                    endDateTime = null,
                ),
                includeDeletedRecords = false,
            )
        }.bind().filter { record ->
            startDate?.toLocalDateTime()?.let {
                record.meta.updatedDate.isAfter(it)
            } ?: true
        }.map { record ->
            downloadRecord<T>(record.identifier).bind()
        }
    }

    private suspend fun <T : DomainResource> fetchPage(
        pageNumber: Int,
        pageSize: Int,
        startDate: ABPZonedDateTime?,
        resourceType: Class<T>,
    ): Result<Page<T>, D4LException> = ioCoroutine { cont ->
        Data4LifeClient.getInstance().fhir4.search(
            resourceType = resourceType,
            annotations = listOf(),
            pageSize = pageSize,
            offset = pageNumber * pageSize,
            callback = Data4LifeCallback(cont),
            creationDateRange = SdkContract.CreationDateRange(
                startDate = startDate?.toLocalDate(),
                endDate = null,
            ),
            updateDateTimeRange = SdkContract.UpdateDateTimeRange(
                startDateTime = startDate?.toLocalDateTime(),
                endDateTime = null,
            ),
            includeDeletedRecords = false,
        )
    }.map { page ->
        page.filter { record ->
            startDate
                ?.toLocalDateTime()
                ?.let(record.meta.updatedDate::isAfter)
                ?: true
        }
    }

    suspend fun <T : DomainResource> fetchAll(
        startDate: ZonedDateTime? = null,
        pageSize: Int = 10,
        resourceType: Class<T>,
    ): Flow<Result<Page<T>, D4LException>> = flow {
        @Suppress("RemoveExplicitTypeArguments")
        binding<Unit, D4LException> {
            var pageNumber = 0
            while (true) {
                val page = fetchPage(
                    resourceType = resourceType,
                    pageNumber = pageNumber,
                    pageSize = pageSize,
                    startDate = startDate
                        ?.let(GregorianCalendar::from)
                        ?.let(DateTimeUtils::toZonedDateTime),
                ).bind()

                if (page.isNotEmpty())
                    emit(Ok(page))

                if (page.size < pageSize)
                    break

                pageNumber += 1
            }
        }.onFailure { ex ->
            emit(Err(ex))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun create(
        resource: DomainResource,
        annotations: List<String>,
    ): Result<Fhir4Record<DomainResource>, D4LException> = ioCoroutine { cont ->
        Data4LifeClient.getInstance().fhir4.create(
            resource = resource,
            annotations = annotations,
            callback = Data4LifeCallback(cont),
        )
    }

    suspend fun update(
        recordId: String,
        resource: DomainResource,
        annotations: List<String>,
    ): Result<Fhir4Record<DomainResource>, D4LException> = ioCoroutine { cont ->
        Data4LifeClient.getInstance().fhir4.update(
            recordId = recordId,
            resource = resource,
            annotations = annotations,
            callback = Data4LifeCallback(cont),
        )
    }

    suspend fun delete(
        recordId: String,
    ): Result<Boolean, D4LException> = ioCoroutine { cont ->
        Data4LifeClient.getInstance().fhir4.delete(
            recordId = recordId,
            callback = Data4LifeCallback(cont),
        )
    }
}

private suspend fun <T> ioCoroutine(lambda: (Continuation<T>) -> Unit): T =
    withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            lambda(cont)
        }
    }

private class Data4LifeCallback<T>(
    private val continuation: Continuation<Result<T, D4LException>>,
) : Callback<T> {
    override fun onSuccess(result: T) {
        continuation.resume(Ok(result))
    }

    override fun onError(exception: D4LException) {
        continuation.resume(Err(exception))
    }
}
