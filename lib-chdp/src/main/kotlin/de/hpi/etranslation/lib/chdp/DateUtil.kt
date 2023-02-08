package de.hpi.etranslation.lib.chdp

import org.threeten.bp.DateTimeUtils
import org.threeten.bp.ZoneId
import java.time.LocalDateTime as StdLocalDateTime
import org.threeten.bp.LocalDateTime as ABPLocalDateTime

fun ABPLocalDateTime.toStd(): StdLocalDateTime {
    return ZoneId.systemDefault()
        .let(this::atZone)
        .let(DateTimeUtils::toGregorianCalendar)
        .toZonedDateTime()
        .toLocalDateTime()
}
