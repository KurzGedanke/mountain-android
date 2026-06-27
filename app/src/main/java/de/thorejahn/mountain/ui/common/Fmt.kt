package de.thorejahn.mountain.ui.common

import android.text.format.DateUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware date/time formatting (§8.1). */
object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val locale: Locale get() = Locale.getDefault()

    private val timeFmt get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val dayFmt get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    private val weekdayShortFmt get() = DateTimeFormatter.ofPattern("EEE", locale)

    private fun zdt(epochSeconds: Long): ZonedDateTime =
        Instant.ofEpochSecond(epochSeconds).atZone(zone)

    /** e.g. "20:00" */
    fun time(epochSeconds: Long): String = timeFmt.format(zdt(epochSeconds))

    /** e.g. "20:00 – 21:30", or just the start when end is null. */
    fun range(startSeconds: Long, endSeconds: Long?): String =
        if (endSeconds == null) time(startSeconds)
        else "${time(startSeconds)} – ${time(endSeconds)}"

    /** e.g. "Saturday, 18 July" */
    fun day(epochSeconds: Long): String = dayFmt.format(zdt(epochSeconds))

    /** e.g. "Sat 22:00" */
    fun dayTime(epochSeconds: Long): String =
        "${weekdayShortFmt.format(zdt(epochSeconds))} ${time(epochSeconds)}"

    /** Start-of-day epoch seconds, for grouping (§7.2). */
    fun startOfDay(epochSeconds: Long): Long =
        zdt(epochSeconds).toLocalDate().atStartOfDay(zone).toEpochSecond()

    /** e.g. "2 hours ago" */
    fun relative(epochSeconds: Long): String =
        DateUtils.getRelativeTimeSpanString(
            epochSeconds * 1000L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
}
