package de.thorejahn.mountain.analytics

import android.util.Log

/**
 * Analytics facade (§11). Signal names and parameters match the iOS app exactly.
 *
 * The iOS app uses TelemetryDeck (App ID [APP_ID], prefix [SIGNAL_PREFIX]). This build ships a
 * privacy-preserving no-op that only logs locally — drop in the TelemetryDeck Android SDK here
 * (initialize in [init], forward in [signal]) to enable real anonymous product analytics.
 */
object Telemetry {
    const val APP_ID = "463DFAC5-B137-4E5A-B3DA-2810E0AE27B8"
    const val SIGNAL_PREFIX = "de.kurzgedanke."

    private const val TAG = "Telemetry"

    fun init() {
        // TelemetryDeck.start(context, TelemetryDeck.Configuration(APP_ID).defaultSignalPrefix(SIGNAL_PREFIX))
    }

    fun signal(name: String, params: Map<String, String> = emptyMap()) {
        Log.d(TAG, "$SIGNAL_PREFIX$name $params")
        // TelemetryDeck.signal(name, params = params)
    }

    fun bandFavorited(bandId: Int) = signal("Band.favorited", mapOf("bandID" to bandId.toString()))
    fun bandUnfavorited(bandId: Int) = signal("Band.unfavorited", mapOf("bandID" to bandId.toString()))
    fun bandViewed(bandId: Int) = signal("Band.viewed", mapOf("bandID" to bandId.toString()))
}
