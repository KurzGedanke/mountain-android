package de.thorejahn.mountain

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import de.thorejahn.mountain.analytics.Telemetry
import de.thorejahn.mountain.data.local.SeedLoader
import de.thorejahn.mountain.data.local.SnapshotCache
import de.thorejahn.mountain.data.prefs.AutographFavoritesPrefs
import de.thorejahn.mountain.data.prefs.FavoritesPrefs
import de.thorejahn.mountain.data.prefs.ReminderPrefs
import de.thorejahn.mountain.data.prefs.SettingsPrefs
import de.thorejahn.mountain.data.remote.BaphometApi
import de.thorejahn.mountain.domain.AutographFavoritesStore
import de.thorejahn.mountain.domain.FavoritesStore
import de.thorejahn.mountain.domain.LineupStore
import de.thorejahn.mountain.domain.ReminderManager
import de.thorejahn.mountain.domain.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MountainApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this) // analytics before UI (§5.1)
        container = AppContainer(this)
        createReminderChannel()
    }

    /** Coil image loader with OkHttp networking + disk cache so thumbnails survive offline (§6.4). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").absolutePath.toPath())
                    .build()
            }
            .build()

    private fun createReminderChannel() {
        val channel = NotificationChannel(
            ReminderManager.CHANNEL_ID,
            getString(R.string.reminders),
            NotificationManager.IMPORTANCE_HIGH,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

/** App-scoped singletons (the three stores + their dependencies), per §4. */
class AppContainer(context: Context) {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsPrefs = SettingsPrefs(context)
    val favoritesPrefs = FavoritesPrefs(context)
    val autographFavoritesPrefs = AutographFavoritesPrefs(context)
    private val reminderPrefs = ReminderPrefs(context)

    val lineup = LineupStore(
        cache = SnapshotCache(context),
        seed = SeedLoader(context),
        api = BaphometApi(),
    )
    val favorites = FavoritesStore(favoritesPrefs, scope)
    val autographFavorites = AutographFavoritesStore(autographFavoritesPrefs, scope)
    val settings = SettingsStore(settingsPrefs, scope)
    val reminders = ReminderManager(context.applicationContext, reminderPrefs)
}
