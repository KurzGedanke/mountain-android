package de.thorejahn.mountain.domain

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import de.thorejahn.mountain.R
import de.thorejahn.mountain.data.model.TimeSlot
import de.thorejahn.mountain.data.prefs.ReminderPrefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Schedules a local notification 15 minutes before each favorited band plays (§4.3). */
class ReminderManager(
    private val appContext: Context,
    private val prefs: ReminderPrefs,
) {
    var authorized: Boolean by mutableStateOf(notificationsAllowed())
        private set

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    private fun notificationsAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun refreshAuthorization() {
        authorized = notificationsAllowed()
    }

    /** Full rebuild (§4.3): cancel everything, then re-add future + favorited + enabled. */
    suspend fun sync(enabled: Boolean, favorites: Set<Int>, slots: List<TimeSlot>) {
        cancelAll()
        if (!enabled) return
        refreshAuthorization()
        if (!authorized) return

        val now = Instant.now()
        val lead = LEAD_MINUTES * 60L
        val scheduled = mutableSetOf<String>()

        for (slot in slots) {
            if (slot.bandId !in favorites) continue
            val fireSeconds = slot.start - lead
            val fireInstant = Instant.ofEpochSecond(fireSeconds)
            if (!fireInstant.isAfter(now)) continue // never schedule past reminders

            val time = timeFormatter.format(slot.startInstant)
            val body = appContext.getString(R.string.on_stage_at, time, slot.stage)
            schedule(slot.id, fireInstant.toEpochMilli(), slot.band, body)
            scheduled += slot.id
        }
        prefs.setScheduledIds(scheduled)
    }

    private fun schedule(id: String, triggerAtMillis: Long, title: String, body: String) {
        val pi = firePendingIntent(id, title, body, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Graceful fallback when exact alarms aren't permitted (§14).
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private suspend fun cancelAll() {
        for (id in prefs.scheduledIds()) {
            val existing = firePendingIntent(id, null, null, PendingIntent.FLAG_NO_CREATE)
            if (existing != null) {
                alarmManager.cancel(existing)
                existing.cancel()
            }
        }
        prefs.setScheduledIds(emptySet())
    }

    private fun firePendingIntent(
        id: String,
        title: String?,
        body: String?,
        extraFlags: Int,
    ): PendingIntent? {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_ID, id)
            if (title != null) putExtra(ReminderReceiver.EXTRA_TITLE, title)
            if (body != null) putExtra(ReminderReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            appContext,
            id.hashCode(),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val LEAD_MINUTES = 15
        const val CHANNEL_ID = "reminders"
    }
}
