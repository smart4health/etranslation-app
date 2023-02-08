package de.hpi.etranslation.data

import com.squareup.sqldelight.ColumnAdapter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object InstantAdapter : ColumnAdapter<Instant, String> {
    override fun encode(value: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .withZone(ZoneId.systemDefault())
        .format(value)

    override fun decode(databaseValue: String): Instant = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .parse(databaseValue)
        .let(Instant::from)
}
