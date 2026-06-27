package de.thorejahn.mountain.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.thorejahn.mountain.MountainApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms alarms after reboot (§14). iOS persists scheduled notifications across reboots
 * automatically; on Android we must re-run the full sync.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? MountainApp ?: return
        val container = app.container
        val pending = goAsync()
        container.scope.launch {
            try {
                val enabled = container.settingsPrefs.remindersEnabled.first()
                val favorites = container.favoritesPrefs.ids.first()
                container.reminders.sync(enabled, favorites, container.lineup.slots)
            } finally {
                pending.finish()
            }
        }
    }
}
