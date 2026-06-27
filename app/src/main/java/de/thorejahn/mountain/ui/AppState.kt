package de.thorejahn.mountain.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import de.thorejahn.mountain.domain.FavoritesStore
import de.thorejahn.mountain.domain.LineupStore
import de.thorejahn.mountain.domain.ReminderManager
import de.thorejahn.mountain.domain.SettingsStore

enum class AppTab { NOW, LINEUP, INFO }

/** Within-tab destinations layered above each tab's root screen. */
sealed interface Destination {
    data class BandDetail(val bandId: Int) : Destination
    data object Settings : Destination
    data object About : Destination
}

/** Minimal per-tab back stack navigation (§5.3). */
class NavController {
    var tab: AppTab by mutableStateOf(AppTab.NOW)
        private set

    private val stacks: Map<AppTab, SnapshotStateList<Destination>> =
        AppTab.entries.associateWith { emptyList<Destination>().toMutableStateList() }

    /** Current overlay destination on the active tab, or null when at the tab root. */
    val current: Destination? get() = stacks.getValue(tab).lastOrNull()

    fun selectTab(target: AppTab) {
        tab = target
    }

    fun push(destination: Destination) {
        stacks.getValue(tab).add(destination)
    }

    /** Pop the active tab. Returns false when already at the root (nothing to pop). */
    fun pop(): Boolean {
        val stack = stacks.getValue(tab)
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalLineupStore = staticCompositionLocalOf<LineupStore> { error("LineupStore not provided") }
val LocalFavoritesStore = staticCompositionLocalOf<FavoritesStore> { error("FavoritesStore not provided") }
val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> { error("SettingsStore not provided") }
val LocalReminderManager = staticCompositionLocalOf<ReminderManager> { error("ReminderManager not provided") }
val LocalNav = staticCompositionLocalOf<NavController> { error("NavController not provided") }
val LocalOpenUrl = staticCompositionLocalOf<(String) -> Unit> { error("openUrl not provided") }
