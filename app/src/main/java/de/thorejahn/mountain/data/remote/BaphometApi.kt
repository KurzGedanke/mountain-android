package de.thorejahn.mountain.data.remote

import de.thorejahn.mountain.data.model.Band
import de.thorejahn.mountain.data.model.LineupSnapshot
import de.thorejahn.mountain.data.model.TimeSlot
import de.thorejahn.mountain.data.remote.dto.ApiStage
import de.thorejahn.mountain.data.remote.dto.ApiTimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

const val FESTIVAL = "Dong Open Air 2026"
const val FESTIVAL_SLUG = "dong-open-air-2026"
const val API_BASE = "https://bands.baphomet.club"

/**
 * Read-only client for the Baphomet API (§3). Any non-2xx response throws, so the caller
 * (LineupStore.refresh) falls back to cached data.
 */
class BaphometApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)        // 20s per request (§3.5)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private suspend inline fun getString(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_BASE + path)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} for $path")
            }
            response.body?.string() ?: error("Empty body for $path")
        }
    }

    private suspend fun fetchBands(): List<Band> =
        AppJson.decodeFromString(ListSerializer(Band.serializer()), getString("/api/festivals/$FESTIVAL_SLUG/bands"))

    private suspend fun fetchStages(): List<ApiStage> =
        AppJson.decodeFromString(ListSerializer(ApiStage.serializer()), getString("/api/festivals/$FESTIVAL_SLUG/stages"))

    private suspend fun fetchTimeslots(stageSlug: String): List<TimeSlot> {
        val raw = AppJson.decodeFromString(
            ListSerializer(ApiTimeSlot.serializer()),
            getString("/api/festivals/$FESTIVAL_SLUG/stages/$stageSlug/timeslots"),
        )
        return raw.map {
            TimeSlot(
                bandId = it.bandId,
                band = it.band,
                bandSlug = it.bandSlug,
                stage = it.stage,
                start = it.startTime.timestamp,
                end = it.endTime?.timestamp,
            )
        }
    }

    /** §3.4 — parallel bands fetch, sequential stage/timeslot loop, slots sorted ascending by start. */
    suspend fun fetchSnapshot(): LineupSnapshot = coroutineScope {
        val bandsDeferred = async { fetchBands() }
        val stages = fetchStages()
        val slots = stages.flatMap { fetchTimeslots(it.slug) }.sortedBy { it.start }
        LineupSnapshot(
            festival = FESTIVAL,
            stages = stages.map { it.name },
            bands = bandsDeferred.await(),
            slots = slots,
            updatedAt = Instant.now().epochSecond,
        )
    }
}
