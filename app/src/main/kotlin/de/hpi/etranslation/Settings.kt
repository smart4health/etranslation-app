package de.hpi.etranslation

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class Settings(
    val langOverride: String?,

    val theme: Theme,

    val isConsented: Boolean,
)

@Serializable
enum class Theme {
    DAY,
    NIGHT,
    SYSTEM,
}

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue = Settings(
        langOverride = null,
        theme = Theme.SYSTEM,
        isConsented = false,
    )

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): Settings = try {
        Json.decodeFromStream(input)
    } catch (se: SerializationException) {
        throw CorruptionException("Unable to read Settings", se)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: Settings, output: OutputStream) {
        Json.encodeToStream(t, output)
    }
}
