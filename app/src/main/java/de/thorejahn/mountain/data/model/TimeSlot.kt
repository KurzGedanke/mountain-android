package de.thorejahn.mountain.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One band playing one stage at one time.
 *
 * Persistence format (§2.2): [start]/[end] are stored as unix epoch SECONDS (Long), not ISO
 * strings — both in the cache and the bundled seed. [end] is omitted from JSON when null
 * (see the Json config in [de.thorejahn.mountain.data.remote.AppJson]).
 */
@Serializable
data class TimeSlot(
    val bandId: Int,
    val band: String,        // denormalized band name
    val bandSlug: String,
    val stage: String,       // denormalized stage display name
    val start: Long,         // epoch seconds
    val end: Long? = null,   // epoch seconds, nullable
) {
    /** Stable across reloads — reused verbatim as the notification identifier (§7). */
    val id: String get() = "$bandId-$start"

    val startInstant: Instant get() = Instant.ofEpochSecond(start)

    /** Effective end: real end, or start + 1 hour when missing (§15.1). */
    val effectiveEnd: Instant get() = Instant.ofEpochSecond(end ?: (start + 3600L))
}
