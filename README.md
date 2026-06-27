# Mountain for Android

An unofficial, offline-first companion app for the **Dong Open Air** metal festival — Android port of the iOS/SwiftUI [Mountain](https://github.com/KurzGedanke/mountain) app.

Built with Kotlin and Jetpack Compose (Material 3).

## Features

- **Now & Next** — see what's playing now and up next per stage, self-updating every 30 seconds.
- **Full line-up** — browse the running order grouped by day, search bands, filter to favorites.
- **Favorites** — star bands; favorites are stored independently and survive schedule refreshes.
- **Reminders** — optional local notification 15 minutes before a favorited band plays.
- **Band detail** — artwork, genre, set times, description, and streaming/social links.
- **Offline-first** — ships with a bundled schedule snapshot and works fully without a connection; refreshes in the background when online.
- **Bilingual** — German (default) and English, following the device language.
- **Appearance** — System / Light / Dark.

No accounts, no user tracking — only anonymous product analytics.

## Tech stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 36 |
| Networking | read-only [Baphomet API](https://github.com/KurzGedanke/band-api) client |
| JSON | kotlinx.serialization |
| Notifications | AlarmManager (exact alarms) + NotificationManager |
| Analytics | TelemetryDeck (privacy-preserving) |

## Building

This project requires no system-wide Java install — build with the JDK bundled in Android Studio (JBR) via the Gradle wrapper.

```sh
# Debug build
./gradlew assembleDebug

# Release build (unsigned unless keystore.properties is present)
./gradlew assembleRelease
```

### Release signing

Release signing config is read from `keystore.properties`, which is kept out of version control. Copy the example and fill in your values:

```sh
cp keystore.properties.example keystore.properties
```

Without that file, release builds are produced **unsigned** (fine for local checks).

## Project layout

See [`ANDROID_IMPLEMENTATION.md`](ANDROID_IMPLEMENTATION.md) for the complete implementation specification, including the data model, networking, offline caching, screen specs, and behavioural edge cases.

```
app/
 ├─ data/      models, remote API client, local cache, DataStore prefs
 ├─ domain/    LineupStore, FavoritesStore, ReminderManager
 ├─ ui/        Compose screens (home, lineup, band, settings) + shared components
 └─ analytics/ TelemetryDeck wrapper
```

## Related projects

- **Mountain (iOS)** — https://github.com/KurzGedanke/mountain
- **Band API** — https://github.com/KurzGedanke/band-api

## License

[MIT](LICENSE) © Thore Jahn
