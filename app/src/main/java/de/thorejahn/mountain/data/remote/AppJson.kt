package de.thorejahn.mountain.data.remote

import kotlinx.serialization.json.Json

/**
 * Shared JSON parser.
 *
 * - [Json.ignoreUnknownKeys] / [Json.isLenient]: discard the rest of the verbose PHPDate blob (§3.3).
 * - explicitNulls = false: omit null fields (e.g. TimeSlot.end) so the cache stays tiny (§2.2).
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
