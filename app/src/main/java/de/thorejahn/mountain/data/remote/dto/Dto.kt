package de.thorejahn.mountain.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiStage(val name: String, val slug: String)

@Serializable
data class ApiTimeSlot(
    val band: String,
    val bandSlug: String,
    val bandId: Int,
    val stage: String,
    val startTime: PhpDate,
    val endTime: PhpDate? = null,
)

/**
 * Autograph signing session (§3). Band + signing point are embedded, so a single
 * `/autographs` call is enough. `signingPointSlug` is present in the payload but unused.
 */
@Serializable
data class ApiAutographSession(
    val band: String,
    val bandSlug: String,
    val bandId: Int,
    val signingPoint: String,
    val location: String? = null,
    val startTime: PhpDate,
    val endTime: PhpDate? = null,
)

/**
 * PHP DateTime quirk (§3.3): date fields come back as a verbose Symfony-serialized PHP DateTime
 * object with multi-megabyte timezone tables. We read ONLY the embedded integer [timestamp]
 * (unix seconds) and ignore everything else (ignoreUnknownKeys handles the rest of the blob).
 */
@Serializable
data class PhpDate(val timestamp: Long)
