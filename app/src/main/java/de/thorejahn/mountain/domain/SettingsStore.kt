package de.thorejahn.mountain.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.thorejahn.mountain.data.prefs.AppearanceSetting
import de.thorejahn.mountain.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Compose-observable mirror of the persisted settings (§6.3). */
class SettingsStore(
    private val prefs: SettingsPrefs,
    private val scope: CoroutineScope,
) {
    var appearance: AppearanceSetting by mutableStateOf(AppearanceSetting.SYSTEM)
        private set

    var remindersEnabled: Boolean by mutableStateOf(true)
        private set

    init {
        prefs.appearance.onEach { appearance = it }.launchIn(scope)
        prefs.remindersEnabled.onEach { remindersEnabled = it }.launchIn(scope)
    }

    fun updateAppearance(value: AppearanceSetting) {
        appearance = value
        scope.launch { prefs.setAppearance(value) }
    }

    fun updateRemindersEnabled(value: Boolean) {
        remindersEnabled = value
        scope.launch { prefs.setRemindersEnabled(value) }
    }
}
