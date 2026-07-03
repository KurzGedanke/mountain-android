package de.thorejahn.mountain.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.thorejahn.mountain.analytics.Telemetry
import de.thorejahn.mountain.data.prefs.FavoritesPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Favorited band ids, persisted in DataStore; independent of the schedule snapshot (§4.2). */
class FavoritesStore(
    private val prefs: FavoritesPrefs,
    private val scope: CoroutineScope,
) {
    var ids: Set<Int> by mutableStateOf(emptySet())
        private set

    init {
        // Keep state in sync with persisted prefs.
        prefs.ids.onEach { ids = it }.launchIn(scope)
    }

    fun isFavorite(bandId: Int): Boolean = ids.contains(bandId)

    fun toggle(bandId: Int) {
        val nowFavorite = !ids.contains(bandId)
        val updated = if (nowFavorite) ids + bandId else ids - bandId
        ids = updated // optimistic; the prefs collector will reconcile
        scope.launch { prefs.save(updated) }
        if (nowFavorite) Telemetry.bandFavorited(bandId) // §9: only positive-intent events
    }
}
