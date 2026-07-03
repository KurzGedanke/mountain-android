package de.thorejahn.mountain.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mountain_prefs")

private object Keys {
    val FAVORITE_BAND_IDS = stringSetPreferencesKey("favoriteBandIDs")
    val FAVORITE_AUTOGRAPH_IDS = stringSetPreferencesKey("favoriteAutographIDs")
    val APPEARANCE = stringPreferencesKey("appearance")
    val REMINDERS_ENABLED = booleanPreferencesKey("remindersEnabled")
    val SCHEDULED_REMINDER_IDS = stringSetPreferencesKey("scheduledReminderIDs")
}

enum class AppearanceSetting(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(key: String?): AppearanceSetting =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Settings prefs (§6.3): appearance + reminders master switch. */
class SettingsPrefs(private val context: Context) {
    val appearance: Flow<AppearanceSetting> =
        context.dataStore.data.map { AppearanceSetting.from(it[Keys.APPEARANCE]) }

    val remindersEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REMINDERS_ENABLED] ?: true }

    suspend fun setAppearance(value: AppearanceSetting) {
        context.dataStore.edit { it[Keys.APPEARANCE] = value.key }
    }

    suspend fun setRemindersEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.REMINDERS_ENABLED] = value }
    }
}

/** Favorited band ids (§6.2). Stored as a Set<String> of int ids; converted at the boundary. */
class FavoritesPrefs(private val context: Context) {
    val ids: Flow<Set<Int>> =
        context.dataStore.data.map { prefs ->
            (prefs[Keys.FAVORITE_BAND_IDS] ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        }

    suspend fun save(ids: Set<Int>) {
        context.dataStore.edit { it[Keys.FAVORITE_BAND_IDS] = ids.map(Int::toString).toSet() }
    }
}

/**
 * Favorited autograph-session ids (§4). Stored under a *separate* key from band favorites so
 * refreshing the schedule never disturbs them. Ids are the stable session id `{bandId}-{start}`.
 */
class AutographFavoritesPrefs(private val context: Context) {
    val ids: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.FAVORITE_AUTOGRAPH_IDS] ?: emptySet() }

    suspend fun save(ids: Set<String>) {
        context.dataStore.edit { it[Keys.FAVORITE_AUTOGRAPH_IDS] = ids }
    }
}

/** Tracks which slot-ids currently have a scheduled alarm, so a rebuild can cancel them (§4.3). */
class ReminderPrefs(private val context: Context) {
    suspend fun scheduledIds(): Set<String> {
        var result: Set<String> = emptySet()
        context.dataStore.edit { result = it[Keys.SCHEDULED_REMINDER_IDS] ?: emptySet() }
        return result
    }

    suspend fun setScheduledIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.SCHEDULED_REMINDER_IDS] = ids }
    }
}
