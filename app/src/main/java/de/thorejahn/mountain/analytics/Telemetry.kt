package de.thorejahn.mountain.analytics

import android.content.Context
import android.util.Log
import com.telemetrydeck.sdk.TelemetryDeck

/**
 * Analytics facade (§11). Signal names and parameters match the iOS app exactly.
 *
 * Backed by the TelemetryDeck Android SDK (App ID [APP_ID], signal prefix [SIGNAL_PREFIX]).
 * Only anonymous, aggregate product signals are sent — the SDK hashes user identifiers before
 * they ever reach the server. We keep a local [Log] echo so signals are visible in logcat.
 *
 * Per §9 only positive-intent events are tracked (a favorite being *added*, never removed) and
 * "band viewed" is deduped to once per app session.
 */
object Telemetry {
    const val APP_ID = "F646E219-C782-4F4C-AF8E-2122CE91FE95"
    const val SIGNAL_PREFIX = "de.kurzgedanke."

    private const val TAG = "Telemetry"

    /** Bands already reported as viewed this process — dedupes Band.viewed (§9). */
    private val viewedBands = mutableSetOf<Int>()

    fun init(context: Context) {
        val builder = TelemetryDeck.Builder().appID(APP_ID)
        TelemetryDeck.start(context.applicationContext, builder)
    }

    fun signal(name: String, params: Map<String, String> = emptyMap()) {
        Log.d(TAG, "$SIGNAL_PREFIX$name $params")
        TelemetryDeck.signal("$SIGNAL_PREFIX$name", null, params)
    }

    fun bandFavorited(bandId: Int) = signal("Band.favorited", mapOf("bandID" to bandId.toString()))

    /** Fires at most once per band per app session (§9). */
    fun bandViewed(bandId: Int) {
        if (!viewedBands.add(bandId)) return
        signal("Band.viewed", mapOf("bandID" to bandId.toString()))
    }

    fun autographFavorited(autographId: String) =
        signal("Autograph.favorited", mapOf("autographID" to autographId))
}
