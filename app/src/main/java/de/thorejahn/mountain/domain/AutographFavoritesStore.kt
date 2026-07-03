package de.thorejahn.mountain.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.thorejahn.mountain.analytics.Telemetry
import de.thorejahn.mountain.data.prefs.AutographFavoritesPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Favorited autograph-session ids, persisted in DataStore. Independent from band favorites (§4):
 * a band can be favorited while only one of its sessions is, and vice versa.
 */
class AutographFavoritesStore(
    private val prefs: AutographFavoritesPrefs,
    private val scope: CoroutineScope,
) {
    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    init {
        prefs.ids.onEach { ids = it }.launchIn(scope)
    }

    fun isFavorite(sessionId: String): Boolean = ids.contains(sessionId)

    fun toggle(sessionId: String) {
        val nowFavorite = !ids.contains(sessionId)
        val updated = if (nowFavorite) ids + sessionId else ids - sessionId
        ids = updated // optimistic; the prefs collector will reconcile
        scope.launch { prefs.save(updated) }
        if (nowFavorite) Telemetry.autographFavorited(sessionId) // §9: only positive-intent events
    }
}
