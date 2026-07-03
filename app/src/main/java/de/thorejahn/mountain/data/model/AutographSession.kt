package de.thorejahn.mountain.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One autograph signing session: a band signs at a signing point during a time window (§3).
 *
 * Persistence format mirrors [TimeSlot]: [start]/[end] are stored as unix epoch SECONDS (Long),
 * not the verbose PHP DateTime object. [end] and [location] are nullable and omitted from JSON
 * when null (see [de.thorejahn.mountain.data.remote.AppJson]).
 */
@Serializable
data class AutographSession(
    val bandId: Int,
    val band: String,          // denormalized band name
    val bandSlug: String,
    val signingPoint: String,  // denormalized signing point name
    val location: String? = null,
    val start: Long,           // epoch seconds
    val end: Long? = null,     // epoch seconds, nullable
) {
    /** Stable across reloads (a band signs a given point-start at most once) — matches iOS. */
    val id: String get() = "$bandId-$start"

    val startInstant: Instant get() = Instant.ofEpochSecond(start)

    /** Effective end: real end, or start + 1 hour when missing (same rule as sets, §7). */
    val effectiveEnd: Instant get() = Instant.ofEpochSecond(end ?: (start + 3600L))
}
