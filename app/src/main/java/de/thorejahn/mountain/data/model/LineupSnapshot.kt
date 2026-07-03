package de.thorejahn.mountain.data.model

import kotlinx.serialization.Serializable

/** The whole offline-cacheable picture (§2.3). */
@Serializable
data class LineupSnapshot(
    val festival: String,
    val stages: List<String>,      // display names, e.g. ["Hauptbühne"]
    val bands: List<Band>,
    val slots: List<TimeSlot>,     // serialized with epoch-second start/end
    val autographs: List<AutographSession> = emptyList(), // default so older caches still load (§3)
    val updatedAt: Long? = null,   // epoch seconds; null in the seed
) {
    companion object {
        fun empty(festival: String) =
            LineupSnapshot(festival, emptyList(), emptyList(), emptyList(), emptyList(), null)
    }
}
