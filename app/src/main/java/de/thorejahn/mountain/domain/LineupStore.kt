package de.thorejahn.mountain.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.thorejahn.mountain.data.local.SeedLoader
import de.thorejahn.mountain.data.local.SnapshotCache
import de.thorejahn.mountain.data.model.AutographSession
import de.thorejahn.mountain.data.model.Band
import de.thorejahn.mountain.data.model.LineupSnapshot
import de.thorejahn.mountain.data.model.TimeSlot
import de.thorejahn.mountain.data.remote.BaphometApi
import de.thorejahn.mountain.data.remote.FESTIVAL
import java.time.Instant

/** Owns the snapshot; offline-first (§4.1). State is Compose-observable. */
class LineupStore(
    private val cache: SnapshotCache,
    private val seed: SeedLoader,
    private val api: BaphometApi,
) {
    enum class Status { Idle, Loading, Updated, Offline }

    var snapshot: LineupSnapshot by mutableStateOf(loadCachedOrSeed())
        private set

    var status: Status by mutableStateOf(Status.Idle)
        private set

    /** §6.1 load order: cache → seed → empty. Synchronous so the UI has data immediately. */
    private fun loadCachedOrSeed(): LineupSnapshot =
        cache.read() ?: seed.load() ?: LineupSnapshot.empty(FESTIVAL)

    // --- Derived ---

    val bands: List<Band> get() = snapshot.bands.sortedBy { it.name.lowercase() }
    val slots: List<TimeSlot> get() = snapshot.slots // already sorted by start
    val autographs: List<AutographSession> get() = snapshot.autographs // already sorted by start
    val updatedAt: Long? get() = snapshot.updatedAt
    val isEmpty: Boolean get() = slots.isEmpty() && bands.isEmpty()

    fun band(id: Int): Band? = snapshot.bands.firstOrNull { it.id == id }

    fun slotsForBand(id: Int): List<TimeSlot> = slots.filter { it.bandId == id }

    /** All autograph sessions for a band, sorted by start (§6). */
    fun autographsForBand(id: Int): List<AutographSession> =
        autographs.filter { it.bandId == id }.sortedBy { it.start }

    /**
     * The single soonest upcoming favorited autograph session (§7): favorited AND not yet ended
     * (missing end treated as start + 1h). null hides the Home section entirely.
     */
    fun nextFavoriteAutograph(
        favoriteIds: Set<String>,
        at: Instant = Instant.now(),
    ): AutographSession? =
        autographs
            .filter { it.id in favoriteIds && !it.effectiveEnd.isBefore(at) }
            .minByOrNull { it.start }

    /** Slots currently on stage: start <= at < effectiveEnd (§4.1). */
    fun nowPlaying(at: Instant = Instant.now()): List<TimeSlot> =
        slots.filter { !it.startInstant.isAfter(at) && at.isBefore(it.effectiveEnd) }

    /** Single next not-yet-started slot per stage, soonest first (§4.1, §15.6). */
    fun upNext(at: Instant = Instant.now()): List<TimeSlot> {
        val perStage = LinkedHashMap<String, TimeSlot>()
        for (slot in slots) {
            if (slot.startInstant.isAfter(at) && !perStage.containsKey(slot.stage)) {
                perStage[slot.stage] = slot
            }
        }
        return perStage.values.sortedBy { it.start }
    }

    /** All favorited slots not yet finished, sorted by start (Home "Your bands" §7.1). */
    fun upcomingFavoriteSlots(favorites: Set<Int>, at: Instant = Instant.now()): List<TimeSlot> =
        slots.filter { it.bandId in favorites && !it.effectiveEnd.isBefore(at) }
            .sortedBy { it.start }

    /** §4.1 refresh — never destroys data on failure. */
    suspend fun refresh() {
        status = Status.Loading
        try {
            val fresh = api.fetchSnapshot()
            snapshot = fresh
            cache.write(fresh)
            status = Status.Updated
        } catch (_: Exception) {
            status = Status.Offline // keep the old snapshot untouched
        }
    }
}
