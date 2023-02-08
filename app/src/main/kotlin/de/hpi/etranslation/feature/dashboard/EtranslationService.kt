package de.hpi.etranslation.feature.dashboard

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import de.hpi.etranslation.Lang
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EtranslationService @Inject constructor(
    private val client: HttpClient,
) {

    private val baseUrl = Url("https://etranslation.smart4health.eu/api/v1")

    /**
     * Send a resource for translation
     *
     * @return translation request id
     */
    suspend fun translateResource(
        resource: String,
        fromLang: Lang,
        toLang: Lang,
    ): Result<String, Unit> {
        val response =
            client.post("documents") {
                parameter("from", fromLang.toString())
                parameter("to", toLang.toString())
                setBody(resource)
            }

        return if (response.status.isSuccess())
            response.bodyAsText().trim().let(::Ok)
        else
            Err(Unit)
    }

    suspend fun getTranslation(translationRequestId: String): (suspend () -> String)? {
        val response = client.get("documents/$translationRequestId")

        if (response.status == HttpStatusCode.NotFound)
            return null

        return { response.bodyAsText() }
    }

    suspend fun deleteTranslation(translationRequestId: String) {
        client.delete("documents/$translationRequestId")
    }

    suspend fun getConfiguration(): Configuration = client.get("configuration").body()

    suspend fun checkStatuses(statuses: List<String>): Map<String, TranslationRequestStatus> {
        if (statuses.isEmpty())
            return emptyMap()

        return client.get("queue") {
            parameter("ids", statuses.joinToString(separator = ","))
        }.body()
    }

    @Serializable
    data class Configuration(
        val languages: List<String>,
        val resourceTypes: List<String>,
    )

    @Serializable
    data class TranslationRequestStatus(
        val status: Status,
        @Serializable(with = InstantSerializer::class)
        val at: Instant,
    )

    enum class Status {
        UNTRANSLATED,
        SENT,
        TRANSLATED,
        TRANSLATION_ERROR,
        SEND_ERROR,
    }

    object InstantSerializer : KSerializer<Instant> {
        override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Instant) {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(value)
                .let(encoder::encodeString)
        }

        override fun deserialize(decoder: Decoder): Instant {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .parse(decoder.decodeString())
                .let(Instant::from)
        }
    }
}
