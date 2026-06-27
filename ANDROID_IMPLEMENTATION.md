# Mountain for Android — Complete Implementation Specification

> Port of **Mountain**, an unofficial, offline-first companion app for the **Dong Open Air** metal festival.
> This document describes the existing iOS/SwiftUI app in exhaustive detail and maps every behaviour to an Android (Kotlin + Jetpack Compose) implementation so the port can be built without seeing the original source.

---

## 0. What the app is

A single-festival companion app. The user can:

1. See what is **playing now** and what is **up next** on each stage (live, self-updating).
2. Browse the **full running order** grouped by day, search bands, and filter to favorites.
3. **Favorite** bands (a star). Favorites are stored independently from the schedule.
4. Get an optional **local notification 15 minutes before** a favorited band plays.
5. Open a **band detail** page: artwork, genre, set times, description, streaming/social links.
6. Change **appearance** (System / Light / Dark) and toggle reminders in **Settings**.
7. Read an **About** page about the developer.

Design pillars (must be preserved on Android):

- **Offline-first.** App ships with a bundled schedule snapshot ("seed"). On launch it shows cached-or-seed data instantly, then refreshes from the network in the background. A failed refresh silently keeps existing data. The app is fully usable with no connection — the festival grounds have poor signal.
- **Favorites survive refresh.** Favorites are keyed by stable band id and never touched by a schedule refresh.
- **Bilingual.** German is the **default/source** language; English is fully supported. Follows device language.
- **No accounts, no tracking of the user.** Only anonymous product analytics (TelemetryDeck).

Current version: **1.0** (build 1). iOS bundle id: `thorejahn.de.mountain`. Display name: *"Mountain - Unofficial Dong Open Air App"*.

---

## 1. Recommended Android stack

| Concern | iOS original | Android target |
|---|---|---|
| UI | SwiftUI (iOS 26) | Jetpack Compose (Material 3) |
| Min OS | iOS 26 | Android 8.0 (API 26) suggested; pick per audience |
| Language | Swift 6 | Kotlin |
| State containers | `@Observable` classes (`@MainActor`) | `ViewModel` + `StateFlow` (or Compose `mutableStateOf`) |
| DI | manual (env injection) | Hilt or manual singletons |
| Navigation | `TabView` + `NavigationStack` | `NavHost` + bottom `NavigationBar`, or per-tab nested nav |
| Networking | `URLSession` + `Codable` | Retrofit/OkHttp or Ktor + kotlinx.serialization |
| JSON | `JSONDecoder`/`JSONEncoder` | kotlinx.serialization (Moshi/Gson acceptable) |
| Persistent cache | `Codable` snapshot file in Application Support | JSON file in `context.filesDir`, or DataStore/Room |
| Key/value prefs | `UserDefaults` / `@AppStorage` | Jetpack **DataStore** (Preferences) |
| Local notifications | `UNUserNotificationCenter` + calendar trigger | `AlarmManager` (exact alarm) + `NotificationManager`, or WorkManager |
| Remote images | `AsyncImage` + `URLCache` | **Coil** `AsyncImage` (disk cache on by default) |
| Analytics | TelemetryDeck SDK | TelemetryDeck Android SDK (or chosen equivalent) |
| Icons | SF Symbols | Material Icons (mapping table in §12) |

Compose + Material 3 + a single-Activity architecture is assumed throughout. Adapt freely, but **preserve behaviour exactly**.

---

## 2. Data model

### 2.1 Band

Matches the API `/bands` payload. All fields except `id`, `name`, `slug` are optional/nullable.

```kotlin
@Serializable
data class Band(
    val id: Int,
    val name: String,
    val slug: String,
    val genre: String? = null,
    val logo: String? = null,
    val image: String? = null,
    val instagram: String? = null,
    val spotify: String? = null,
    val appleMusic: String? = null,
    val bandcamp: String? = null,
    val description: String? = null,
)
```

**URL sanitization rule (critical, easy to miss):** The API returns a *trailing-slash* URL (e.g. `https://.../logos/`) when a value is actually missing. Treat a string as a valid URL **only if** it is non-null, non-empty, and **does not end with `/`**. Apply this to *every* URL-bearing field (`image`, `logo`, `spotify`, `appleMusic`, `bandcamp`, `instagram`).

```kotlin
fun sanitizedUrl(value: String?): String? =
    value?.takeIf { it.isNotEmpty() && !it.endsWith("/") }

val Band.imageUrl get() = sanitizedUrl(image)
val Band.logoUrl get() = sanitizedUrl(logo)
val Band.spotifyUrl get() = sanitizedUrl(spotify)
val Band.appleMusicUrl get() = sanitizedUrl(appleMusic)
val Band.bandcampUrl get() = sanitizedUrl(bandcamp)
val Band.instagramUrl get() = sanitizedUrl(instagram)

// True if any of spotify/appleMusic/bandcamp/instagram resolves (note: NOT logo/image)
val Band.hasLinks get() =
    spotifyUrl != null || appleMusicUrl != null || bandcampUrl != null || instagramUrl != null
```

### 2.2 TimeSlot

One band playing one stage at one time.

```kotlin
data class TimeSlot(
    val bandId: Int,
    val band: String,      // denormalized band name
    val bandSlug: String,
    val stage: String,     // denormalized stage display name
    val start: Instant,    // or epoch-seconds Long throughout
    val end: Instant?,     // nullable
) {
    // Stable across reloads — a band plays a given start-time at most once.
    val id: String get() = "$bandId-${start.epochSeconds}"
}
```

- `id` is `"<bandId>-<startEpochSeconds>"`. This exact format is reused as the **notification identifier** (see §7). Keep it identical.
- **Persistence format:** in the cache/seed JSON, `start` and `end` are stored as **unix epoch seconds** (integers), not ISO strings. Keep the cache file tiny and portable. End is omitted entirely when null.

### 2.3 LineupSnapshot

The whole offline-cacheable picture.

```kotlin
@Serializable
data class LineupSnapshot(
    val festival: String,
    val stages: List<String>,    // display names, e.g. ["Hauptbühne"]
    val bands: List<Band>,
    val slots: List<TimeSlot>,   // serialized with epoch-second start/end
    val updatedAt: Long? = null, // epoch seconds (or ISO); null in the seed
) {
    companion object {
        fun empty(festival: String) =
            LineupSnapshot(festival, emptyList(), emptyList(), emptyList(), null)
    }
}
```

For `TimeSlot` JSON (de)serialization within the snapshot, use a custom serializer or a DTO with `start: Long` / `end: Long?` epoch seconds, converting to `Instant` in memory. The seed file (§9) shows the exact shape.

---

## 3. Networking — Baphomet API client

Read-only client for `https://bands.baphomet.club`.

### 3.1 Constants

```kotlin
const val FESTIVAL = "Dong Open Air 2026"
const val FESTIVAL_SLUG = "dong-open-air-2026"
const val API_BASE = "https://bands.baphomet.club"
```

### 3.2 Endpoints (all GET, `Accept: application/json`, 20s timeout)

| Purpose | Path |
|---|---|
| Bands | `GET /api/festivals/{festivalSlug}/bands` → `[Band]` |
| Stages | `GET /api/festivals/{festivalSlug}/stages` → `[{name, slug}]` |
| Timeslots for a stage | `GET /api/festivals/{festivalSlug}/stages/{stageSlug}/timeslots` → `[APITimeSlot]` |

The festival and stages are addressed by **slug** (`dong-open-air-2026`, `hauptbuhne`). The list endpoints return the slugs to use.

### 3.3 API DTOs

```kotlin
@Serializable data class ApiStage(val name: String, val slug: String)

@Serializable data class ApiTimeSlot(
    val band: String,
    val bandSlug: String,
    val bandId: Int,
    val stage: String,
    val startTime: PhpDate,
    val endTime: PhpDate? = null,
)
```

**PHP DateTime quirk (critical):** Date fields come back as a **verbose Symfony-serialized PHP `DateTime` object** with multi-megabyte timezone tables. Do **not** parse the whole thing. Read only the embedded integer field `timestamp` (unix seconds) and ignore everything else.

```kotlin
@Serializable data class PhpDate(val timestamp: Long) {
    // ignore date string, timezone, timezone_type fields entirely
    val instant get() = Instant.fromEpochSeconds(timestamp)
}
```

Configure the JSON parser to **ignore unknown keys** (`ignoreUnknownKeys = true` / `isLenient`) so the rest of the PHPDate blob is discarded.

### 3.4 fetchSnapshot() algorithm

1. Kick off bands fetch concurrently (`async`).
2. Fetch stages.
3. For **each stage**, fetch its timeslots and accumulate (sequential loop is fine; original does sequential after the parallel bands fetch). Map each `ApiTimeSlot` → `TimeSlot` using `startTime.instant` / `endTime?.instant`.
4. Await bands.
5. **Sort all slots ascending by `start`.**
6. Build `LineupSnapshot(festival = FESTIVAL, stages = stages.map { it.name }, bands, slots, updatedAt = now)`.

```kotlin
suspend fun fetchSnapshot(): LineupSnapshot = coroutineScope {
    val bandsDeferred = async { fetchBands() }
    val stages = fetchStages()
    val slots = stages.flatMap { fetchTimeslots(it.slug) }
        .sortedBy { it.start }
    LineupSnapshot(FESTIVAL, stages.map { it.name }, bandsDeferred.await(), slots, nowEpochSeconds())
}
```

### 3.5 HTTP behaviour

- Timeout: **20 seconds** per request.
- Header: `Accept: application/json`.
- Treat any non-2xx (outside `200..<300`) as an error → throws → caller falls back to cached data.
- Known server quirks (from project memory; handle gracefully, do not crash): the festival is fetched by name/slug; `/stages` has been observed returning 500 occasionally; there is effectively a single stage (`Hauptbühne`). Any failure in the snapshot fetch must propagate up so the store stays on cached data.

---

## 4. State containers (ViewModels)

Three independent stores. On iOS these are `@Observable @MainActor` classes injected into the environment. On Android model them as singletons (Hilt `@Singleton` or app-scoped) exposed through ViewModels, or as ViewModels shared at the Activity scope. They must outlive individual screens and be observable by Compose.

### 4.1 LineupStore

Owns the snapshot. Offline-first.

State:
- `snapshot: LineupSnapshot` (private set)
- `status: Status` where `Status = { Idle, Loading, Updated, Offline }` (private set, starts `Idle`)

Init: load **cached-or-seed** synchronously (see §6) so the UI has data immediately.

Derived/computed:
- `bands`: snapshot bands **sorted case-insensitively by name** ascending.
- `slots`: snapshot.slots (already sorted by start from the API/seed).
- `updatedAt`: snapshot.updatedAt.
- `isEmpty`: `slots.isEmpty() && bands.isEmpty()`.
- `band(id: Int): Band?` — first band with matching id.
- `slotsForBand(id: Int): List<TimeSlot>` — slots filtered by bandId.
- **`nowPlaying(at: Instant = now): List<TimeSlot>`** — slots where `start <= at < end`, with `end` defaulting to `start + 3600s` (1 hour) when null.
- **`upNext(at: Instant = now): List<TimeSlot>`** — the **single next not-yet-started slot per stage**, soonest first. Algorithm: iterate slots (in start order) with `start > at`; keep the first one seen for each stage (a map keyed by stage); return the map's values sorted by start ascending.

Actions:
- **`refresh()`** (suspend): set `status = Loading`; try `api.fetchSnapshot()`. On success: replace `snapshot`, **persist** it to disk, set `status = Updated`. On any error: set `status = Offline` (and **keep the old snapshot** untouched).

### 4.2 FavoritesStore

Favorited band ids, persisted in key/value prefs. Completely separate from the snapshot.

- Prefs key: `"favoriteBandIDs"` → stored as a list/array of ints. (On Android, DataStore: a `Set<String>` of ids works well; convert.)
- State: `ids: Set<Int>` (private set), loaded from prefs in init (empty if absent).
- `isFavorite(bandId: Int): Boolean` → `ids.contains(bandId)`.
- **`toggle(bandId: Int)`**: compute `nowFavorite = !ids.contains(bandId)`; insert or remove; **write the full set back to prefs**; then emit analytics signal `"Band.favorited"` or `"Band.unfavorited"` with parameter `bandID = bandId.toString()` (see §11).

### 4.3 ReminderManager

Schedules a local notification 15 minutes before each favorited band plays.

- `leadMinutes = 15` (constant).
- State: `authorized: Boolean` (private set).
- **`requestAuthorization()`**: request notification permission (alert+sound+badge on iOS). On Android 13+ (API 33) request `POST_NOTIFICATIONS` runtime permission; set `authorized` to the granted result.
- **`sync(enabled: Boolean, favorites: Set<Int>, slots: List<TimeSlot>)`** — full rebuild:
  1. **Cancel all** pending/scheduled reminders first (every time).
  2. If `enabled == false` → return (nothing scheduled). This is how the Settings "Reminders" toggle turns everything off.
  3. Check OS notification authorization status; only proceed if authorized (or provisional). Otherwise return.
  4. `now = currentTime`; `lead = 15 * 60` seconds.
  5. For each slot whose `bandId` is in `favorites`:
     - `fireDate = slot.start - lead`.
     - Skip if `fireDate <= now` (don't schedule past reminders).
     - Build a notification:
       - **title** = `slot.band`
       - **body** = localized `"On stage at %1$@ · %2$@"` → `"On stage at <HH:mm> · <stage>"` where time is the slot start formatted as hour:minute in the device locale, and the second arg is `slot.stage`.
       - sound = default.
     - Schedule it to fire at `fireDate` (calendar/exact-time trigger, non-repeating).
     - **identifier = `slot.id`** (the `"<bandId>-<startEpoch>"` string). Reusing the stable id means a rebuild replaces rather than duplicates.

**Android scheduling notes:**
- Use `AlarmManager.setExactAndAllowWhileIdle` (with `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` permission on API 31+) targeting a `BroadcastReceiver` that posts the notification, OR WorkManager with an initial delay (less precise). The festival use-case wants the reminder reasonably on-time, so exact alarms are preferable.
- "Cancel all pending" = cancel every alarm you previously scheduled. Track scheduled request ids (derive a stable int request code from `slot.id`'s hash, or keep the id→PendingIntent mapping) so you can cancel them. Simplest robust approach: keep the set of scheduled slot-ids in DataStore, cancel each on rebuild.
- Create a **notification channel** (e.g. id `reminders`, name "Reminders") at app start (API 26+).
- The notification, when tapped, should open the app (deep-link to the band detail is a nice-to-have, not in the iOS original — iOS just shows it).

---

## 5. App composition & lifecycle

### 5.1 Startup sequence (from `mountainApp` + `ContentView`)

1. Initialize analytics (TelemetryDeck) **before** UI — see §11.
2. Create the three stores.
3. On first composition / app launch, run a startup task:
   - `await lineup.refresh()` (pulls fresh data; falls back to cache on failure).
   - then `reminders.sync(enabled = remindersEnabled, favorites = favorites.ids, slots = lineup.slots)`.
4. Apply the persisted **appearance** (System/Light/Dark) app-wide (see §10).

### 5.2 Reminder re-sync trigger (important reactive behaviour)

On iOS a `.task(id:)` re-runs whenever a key composed of `(remindersEnabled, favorites.ids)` changes. Replicate this: **observe** both the `remindersEnabled` pref and `favorites.ids`; whenever **either** changes:

1. If `remindersEnabled && favorites.ids.isNotEmpty() && !reminders.authorized` → call `requestAuthorization()` first (this is the moment permission is requested — lazily, the first time the user enables a reminder by favoriting a band with reminders on).
2. Then call `reminders.sync(...)`.

So: favoriting your first band (with the default reminders-on) triggers the permission prompt. Toggling reminders off cancels everything; toggling on re-schedules.

### 5.3 Navigation structure

Bottom navigation bar with **3 tabs** (`enum AppTab { NOW, LINEUP, SETTINGS }`):

| Tab | Title (en / de) | Icon (iOS SF Symbol) | Screen |
|---|---|---|---|
| Now | "Now" / "Jetzt" | `play.circle.fill` | HomeView |
| Line-up | "Line-up" / "Programm" | `list.bullet` | RunningOrderView |
| Settings | "Settings" / "Einstellungen" | `gearshape` | SettingsView |

Each tab hosts its own nav stack (so band-detail pushes stay within the tab). Default selected tab: **Now**.

**Launch-argument deep link (for UI tests / shortcuts):** iOS reads a process argument `-startTab <value>`; `"lineup"` → Line-up, `"settings"` or `"about"` → Settings, otherwise Now. On Android, support an equivalent via an Intent extra (e.g. `startTab` extra or a deep-link URI) so automated tests / app shortcuts can open a specific tab. Not user-visible.

---

## 6. Persistence & offline-first caching

### 6.1 Snapshot cache file

- **Location:** Application Support dir → on Android use `context.filesDir`, file name **`lineup_cache.json`**.
- **Load order on launch (`loadCachedOrSeed`)**:
  1. Try to read+decode `lineup_cache.json` from filesDir. If success → use it.
  2. Else read+decode the bundled **`lineup_seed.json`** asset (in `assets/` or `res/raw`). If success → use it.
  3. Else `LineupSnapshot.empty(FESTIVAL)`.
- **Persist on successful refresh** (`persist`): encode snapshot to JSON, create parent dir if needed, write **atomically** (write temp then rename). Failures are swallowed — the in-memory snapshot still works ("best effort").

### 6.2 Favorites

- Prefs key `favoriteBandIDs`. On Android: DataStore Preferences, store as a `Set<String>` of int ids (convert at the boundary). Survives reinstall? No — local only, same as iOS.

### 6.3 Settings prefs

| Key | Type | Default | Meaning |
|---|---|---|---|
| `appearance` | string enum `system`/`light`/`dark` | `system` | app color scheme |
| `remindersEnabled` | bool | `true` | master switch for reminders |

Use DataStore for these (the iOS originals are `@AppStorage`, i.e. UserDefaults).

### 6.4 Image caching

iOS relies on `URLCache` so `AsyncImage` thumbnails keep working offline once seen. On Android, **Coil** does disk caching by default — ensure the disk cache is enabled (it is by default) so band thumbnails/artwork survive offline after first view.

---

## 7. Screens — detailed spec

All user-facing strings must come from string resources (see §13). Times/dates use the device locale formatter.

### 7.1 HomeView — the "Now" tab ("Now & Next" board)

Title (large): **"Dong Open Air"** (this literal is **not** localized in the original — keep as-is). Toolbar: a **refresh button** (trailing). Pull-to-refresh enabled → calls `lineup.refresh()`.

**Live updates:** wrapped in a timeline that ticks **every 30 seconds**, recomputing `now`. On Android: a coroutine emitting current time every 30s (e.g. `flow { while(true){ emit(now); delay(30_000) } }`) driving recomposition, so "Now"/"Up next" advance automatically without user action.

Content (a grouped/inset list):

- **If `lineup.isEmpty`** → full-screen empty state ("No line-up yet" content-unavailable view): icon `wifi.slash`, title **"No line-up yet"**, description **"Connect to the internet once to download the schedule."**
- **Else** three sections:

  1. **Section "Now"** (header "Now" / "Jetzt"):
     - `playing = nowPlaying(now)`.
     - If empty → secondary text **"Nothing on stage right now."**
     - Else a `SlotRow` per slot with `emphasized = true` (band name in headline weight).

  2. **Section "Up next"** (header "Up next" / "Demnächst") — **only rendered if non-empty**:
     - `next = upNext(now)`.
     - A `SlotRow` per slot, `emphasized = false`.

  3. **Section "Your bands"** (header "Your bands" / "Deine Bands"):
     - `favoriteSlots = upcomingFavoriteSlots(now)` = all slots whose band is favorited AND whose end (or start+1h if no end) `>= now`, sorted by start.
     - If no favorites at all → callout secondary text: **"Tap the star on a band to follow it. You'll get a reminder before they play."**
     - Else if favorites exist but no upcoming slots → secondary text: **"No upcoming sets for your favorites."**
     - Else a `SlotRow` per slot with `emphasized = false, showDay = true` (time shows day prefix).

**SlotRow** (shared list row):
- Tapping navigates to **BandDetailView(slot.bandId)**.
- Layout (horizontal, 12dp spacing): `BandThumbnail` (if band found) · column[ band name (headline if emphasized, else body) ; time/stage subtitle in secondary color ] · spacer · `FavoriteButton`.
- Subtitle text = `"<time> · <stage>"` where:
  - if `showDay` → `Fmt.dayTime(start)` (e.g. "Sat 22:00").
  - else → `Fmt.range(start, end)` (e.g. "20:00 – 21:30", or just start if no end).

### 7.2 RunningOrderView — the "Line-up" tab

Title: **"Line-up"** / "Programm". 

Toolbar:
- Trailing: refresh button (shared, same as Home).
- Leading: a **"Favorites only"** toggle rendered as a button (icon `star.fill` when on / `star` when off, label "Favorites only" / "Nur Favoriten"). Toggling animates the list (`.animation(.default)`).
- **Search bar** ("searchable") with prompt **"Search bands"** / "Bands suchen". Filters by band name, case-insensitive `contains`, trimmed of whitespace.

Pull-to-refresh enabled.

Body:
- If `lineup.isEmpty` → same "No line-up yet" content-unavailable view as Home.
- Else the **schedule list**:

  **`visibleSlots`** = `lineup.slots`, then:
  - if `favoritesOnly` → keep only favorited bands' slots,
  - if search query non-empty → keep only slots whose band name contains the query (case-insensitive).

  **Grouping by day:** `days` = the set of `startOfDay(slot.start)` over visibleSlots, sorted ascending. For each day → a `Section` with header `Fmt.day(day)` (e.g. "Saturday, 18 July"), containing the day's slots sorted by start.

  **`ScheduleRow`** (one row): tap → BandDetailView(bandId). Layout (12dp spacing): start time `Fmt.time(start)` in **monospaced** subheadline, fixed width 48dp, secondary color · `BandThumbnail(size=40)` · column[ band name ; stage in caption secondary ] · spacer · `FavoriteButton`.

  **Footer "Updated" line:** if `updatedAt != null` and there are visible slots → a centered, tertiary, caption text **"Updated <relative>"** where `<relative>` is a named relative format (e.g. "Updated 2 hours ago" / "Aktualisiert vor 2 Stunden"). On Android use `DateUtils.getRelativeTimeSpanString` or equivalent.

  **Empty-state overlay** when `visibleSlots` is empty:
  - if search query non-empty → standard "no search results for `<query>`" view.
  - else if `favoritesOnly` → content-unavailable: icon `star`, title **"No favorites"**, description **"Star a band to see it here."**

### 7.3 BandDetailView

Pushed from any slot/band row. Argument: `bandId: Int`.

- Resolve `band = lineup.band(bandId)`.
  - If **not found** → content-unavailable: icon `questionmark`, title **"Band not found"** (with top padding). Nav title fallback "Band".
- Nav title = band name (inline display mode). Toolbar trailing: a larger `FavoriteButton` (title3 size).
- **On appear:** emit analytics `"Band.viewed"` with `bandID = bandId.toString()` (§11).
- Scrollable content (20dp vertical spacing, padded):

  1. **Header artwork:** only if `band.imageUrl != null`. A full-width box of **fixed height 220dp**, image scaled to **fill** and clipped (the fill image is in an overlay so it can't push layout wider than the screen — on Android use `ContentScale.Crop` + `clip(RoundedCornerShape(16.dp))` + fixed height + `fillMaxWidth`). Async load (Coil) with:
     - success → the image.
     - failure → placeholder art.
     - loading → placeholder art with a progress spinner overlaid.
     - **placeholder art** = a quaternary-filled rectangle with a centered `music.mic` glyph (large, secondary color).
  2. **Genre** (if non-empty): the genre **uppercased**, caption semibold, secondary color.
  3. **Set times block** (`sets`): `slotsForBand(bandId)` sorted by start. If non-empty → a rounded (12dp) quaternary-tinted card containing, per slot, a `Label` with clock icon + text `"<Fmt.dayTime(start)> · <stage>"` (headline font).
  4. **Description** (if present & non-empty): body text.
  5. **Links block** (`links`): only if `band.hasLinks`. Header **"Listen & Follow"** / "Anhören & Folgen", then a horizontal row of up to 4 link buttons, each rendered **only if its URL exists**, in this order:
     - Spotify — icon `music.note`, label "Spotify"
     - Apple Music — icon `applelogo`, label "Apple Music"
     - Bandcamp — icon `waveform`, label "Bandcamp"
     - Instagram — icon `camera`, label "Instagram"
     - Each button: vertical [icon (title2) + label (caption2)], full-width within its slot, 10dp vertical padding, rounded 12dp quaternary background. Tapping opens the URL in the browser/app (`Link` → on Android an `ACTION_VIEW` intent).

### 7.4 SettingsView

Title: **"Settings"** / "Einstellungen". A grouped form:

1. **Section "Appearance"** / "Erscheinungsbild": a **segmented picker** with the three `AppearanceSetting` options (System / Light / Dark — labels localized). Bound to the `appearance` pref. Changing it re-themes the whole app immediately.
2. **Section, header "Notifications"** / "Benachrichtigungen", **footer** "Get a reminder 15 minutes before a favorited band plays." / "Erhalte 15 Minuten vorher eine Erinnerung, bevor eine favorisierte Band spielt.": a **Toggle "Reminders"** / "Erinnerungen" bound to `remindersEnabled`. Toggling drives the reminder re-sync (§5.2).
3. **Section** with a single **navigation row "About"** / "Über mich" (icon `person.crop.circle`) → pushes AboutView.

### 7.5 AboutView

Title: **"About"** / "Über mich". Scrollable, centered content (28dp spacing, padded):

1. **Header:** a circular avatar image **"thore"** (bundled asset `thore.jpg/png`), 128×128, circle-clipped, 1px quaternary stroke border, subtle shadow. Below: name **"Thore"** (largeTitle bold), and subtitle **"Podcaster & software developer from the Ruhr area"** / "Podcaster & Softwareentwickler aus dem Ruhrgebiet" (subheadline, secondary, centered).
2. **Bio** (two paragraphs, body, left-aligned):
   - *"Hey, I'm Thore! A podcaster and software developer from the Ruhr area. I've loved Dong Open Air for many years, and this app is basically my little love letter to the festival."*
   - *"I'm also a huge Magic: The Gathering fan. If we bump into each other at Dong, let's play a round. And if you enjoy the app, I'd be happy if you grabbed me a beer."*
   - (German versions in §13.)
3. **Section "Open Source":** intro callout secondary text "Both apps and the API that serves the data are open source." Then two **LinkRow**s:
   - **Mountain** — icon `chevron.left.forwardslash.chevron.right`, tint **purple**, subtitle `github.com/KurzGedanke/mountain`, URL `https://github.com/KurzGedanke/mountain`.
   - **Band API** — icon `server.rack`, tint **teal**, subtitle `github.com/KurzGedanke/band-api`, URL `https://github.com/KurzGedanke/band-api`.
4. **Section "Find me online":**
   - **Mastodon** — icon `bubble.left.and.bubble.right.fill`, tint RGB(0.38, 0.39, 1.0) ≈ `#6163FF`, subtitle `@kurzgedanke@chaos.social`, URL `https://chaos.social/@kurzgedanke`.
   - **Bluesky** — icon `cloud.fill`, tint RGB(0.0, 0.53, 1.0) ≈ `#0087FF`, subtitle `@kurzgedanke.de`, URL `https://bsky.app/profile/kurzgedanke.de`.
5. **Standalone LinkRow** — "Questions or bug reports?" / "Fragen oder Bug-Reports?", icon `envelope.fill`, tint **orange**, subtitle `app@kurzgedanke.me`, URL `mailto:app@kurzgedanke.me`.
6. **Footer:** verbatim `"Mountain v<appVersion>"` (footnote, tertiary), where appVersion is the app's marketing version string (e.g. "1.0").

**LinkRow** component: tappable, opens URL. Layout: colored rounded-square icon badge (38×38, white glyph on `tint.gradient`, 10dp corners) · column[ title (body) ; subtitle (caption, secondary) ] · spacer · trailing `arrow.up.right` glyph (caption, tertiary). Whole row has a 12dp-padded rounded (14dp) quaternary background.

Note: "Mastodon", "Bluesky", "Band API", "Mountain", "Thore", "Dong Open Air" are **not localized** (same string in both languages / proper nouns).

---

## 8. Shared formatting & components

### 8.1 Date/time formatting (`Fmt`) — locale-aware

| Function | Output example (en) | Compose/Java approach |
|---|---|---|
| `time(date)` | `20:00` | hour:minute, locale format |
| `range(start, end)` | `20:00 – 21:30` (or just `20:00` if end null) | two times joined with " – " (en dash) |
| `day(date)` | `Saturday, 18 July` | weekday(wide) + day + month(wide) |
| `dayTime(date)` | `Sat 22:00` | weekday(abbrev) + hour:minute |

Use `java.time` + `DateTimeFormatter`/`DateUtils` with the device locale. German output will differ (e.g. "Samstag, 18. Juli", "Sa. 22:00") — let the locale formatter handle it; don't hardcode.

### 8.2 BandThumbnail

Square thumbnail, default size 48dp (40dp in schedule rows). Coil `AsyncImage(band.imageUrl)`:
- success → image, `ContentScale.Crop`.
- otherwise (loading/failure/no url) → quaternary rectangle with centered `music.mic` glyph sized `size * 0.4`, secondary color.
- Clipped to a rounded square, corner radius `size * 0.18`.

### 8.3 FavoriteButton

A star toggle. Icon `star.fill` (yellow) when favorited, else `star` (secondary). Tapping → `favorites.toggle(bandId)`. Accessibility label: "Remove favorite" / "Favorit entfernen" when favorited, else "Add favorite" / "Favorit hinzufügen".

### 8.4 Refresh button (toolbar)

Trailing toolbar action shared by Home & Line-up. Shows a spinner while `lineup.status == Loading` (and is disabled then), otherwise an `arrow.clockwise` icon. Tap → launches `lineup.refresh()`.

---

## 9. Bundled seed data (`lineup_seed.json`)

Ship the seed as an asset (e.g. `assets/lineup_seed.json` or `res/raw/lineup_seed.json`). It is the exact `LineupSnapshot` shape and is what the app shows before the first successful network refresh.

Structure (real data — **28 bands, 28 slots, 1 stage "Hauptbühne"**, `updatedAt: null`):

```json
{
  "festival": "Dong Open Air 2026",
  "stages": ["Hauptbühne"],
  "bands": [
    {
      "id": 1,
      "name": "Aereum",
      "slug": "aereum",
      "genre": "Melodic Death / Folk Metal",
      "logo": null,
      "image": "https://bands.baphomet.club/images/band/images/aereum.jpg",
      "instagram": null,
      "spotify": "https://open.spotify.com/artist/4InllsE71WEGbU1sM1nUtN",
      "appleMusic": "https://music.apple.com/us/artist/aereum/1492839659",
      "bandcamp": null,
      "description": "Melodic death and folk metal band from Duisburg…"
    }
    // … 27 more bands
  ],
  "slots": [
    { "bandId": 1, "band": "Aereum", "bandSlug": "aereum", "stage": "Hauptbühne", "start": 1784197500, "end": 1784201400 },
    { "bandId": 2, "band": "Onyxsin", "bandSlug": "onyxsin", "stage": "Hauptbühne", "start": 1784201400, "end": 1784205300 }
    // … one slot per band, start/end as unix epoch SECONDS
  ],
  "updatedAt": null
}
```

**Copy the existing `mountain/Resources/lineup_seed.json` verbatim** into the Android project — it is the canonical seed (28 bands with real names, genres, images, Spotify/Apple Music links, descriptions). Do not regenerate it. Note `start`/`end` are integer epoch **seconds**; the snapshot serializer must read them as such.

---

## 10. Theming / appearance

- Three modes via `appearance` pref: **System** (follow OS), **Light**, **Dark**.
- Apply app-wide: Compose — choose `darkTheme` for the `MaterialTheme` based on the pref (`system → isSystemInDarkTheme()`, else forced).
- **Accent color:** the iOS asset `AccentColor` is **unset** (uses the system default blue/tint). On Android, use the Material 3 default or define a tasteful brand accent; nothing brand-specific is mandated. Favorited stars are explicitly **yellow**. Various surfaces use a "quaternary" fill — map to Material `surfaceVariant`/low-emphasis containers.
- Supported orientations (iOS): iPhone portrait + landscape (no upside-down); iPad all. On Android, allow rotation; layouts are simple lists so they reflow fine.
- Device families: iPhone + iPad. On Android support phones and tablets (lists scale; consider larger content padding on tablets but not required for parity).

---

## 11. Analytics (TelemetryDeck)

iOS initializes TelemetryDeck at launch:
- App ID: **`463DFAC5-B137-4E5A-B3DA-2810E0AE27B8`**
- Default signal prefix: **`de.kurzgedanke.`**

Signals emitted (signal name → parameters):

| Signal | When | Parameters |
|---|---|---|
| `Band.favorited` | a band is favorited | `bandID` = id as string |
| `Band.unfavorited` | a band is unfavorited | `bandID` = id as string |
| `Band.viewed` | BandDetailView appears | `bandID` = id as string |

(Effective signal names become `de.kurzgedanke.Band.favorited` etc. via the prefix.) Use the **TelemetryDeck Android/Kotlin SDK** with the same app ID and prefix, or swap for your analytics of choice keeping the same event names. TelemetryDeck is privacy-preserving (no PII; anonymous user hash).

---

## 12. SF Symbol → Material icon mapping

| SF Symbol | Used for | Material Icons suggestion |
|---|---|---|
| `play.circle.fill` | Now tab | `Icons.Filled.PlayCircle` |
| `list.bullet` | Line-up tab | `Icons.AutoMirrored.Filled.List` |
| `gearshape` | Settings tab | `Icons.Filled.Settings` |
| `arrow.clockwise` | refresh | `Icons.Filled.Refresh` |
| `star` / `star.fill` | favorite toggle | `Icons.Outlined.StarBorder` / `Icons.Filled.Star` |
| `wifi.slash` | offline empty state | `Icons.Filled.WifiOff` |
| `music.mic` | thumbnail/artwork placeholder | `Icons.Filled.Mic` (or a music note) |
| `clock` | set times | `Icons.Filled.Schedule` |
| `questionmark` | band not found | `Icons.AutoMirrored.Filled.HelpOutline` |
| `music.note` | Spotify link | `Icons.Filled.MusicNote` |
| `applelogo` | Apple Music link | custom Apple glyph or `MusicNote` |
| `waveform` | Bandcamp link | `Icons.Filled.GraphicEq` |
| `camera` | Instagram link | `Icons.Filled.PhotoCamera` |
| `person.crop.circle` | About row | `Icons.Filled.AccountCircle` |
| `chevron.left.forwardslash.chevron.right` | Mountain repo | `Icons.Filled.Code` |
| `server.rack` | Band API repo | `Icons.Filled.Dns` / `Storage` |
| `bubble.left.and.bubble.right.fill` | Mastodon | `Icons.AutoMirrored.Filled.Chat` |
| `cloud.fill` | Bluesky | `Icons.Filled.Cloud` |
| `envelope.fill` | email | `Icons.Filled.Email` |
| `arrow.up.right` | external link affordance | `Icons.AutoMirrored.Filled.OpenInNew` |

Streaming brand logos: iOS bundles `spotify_logo`, `apple_music`, `bandcamp_logo` imagesets (under `Assets/Streaming/`) but the detail screen actually uses **SF Symbols**, not the brand logos. For Android, the symbol mapping above is sufficient; optionally use real brand logos if licensing permits.

---

## 13. Localization

- **Source/default language: German (`de`).** English (`en`) fully supported. App follows device language; unknown languages fall back to German (source).
- Provide `res/values/strings.xml` (German, the default) and `res/values-en/strings.xml` (English). Yes — German is the *base* `values/`, English is the override, because German is the source language. (If you prefer English base, invert, but then set a German `values-de/`.)
- Format strings use positional args: `On stage at %1$s · %2$s` and `%1$s · %2$s`.

Complete string table (key → German / English):

| Key (semantic) | German | English |
|---|---|---|
| about_title | Über mich | About |
| add_favorite | Favorit hinzufügen | Add favorite |
| remove_favorite | Favorit entfernen | Remove favorite |
| appearance | Erscheinungsbild | Appearance |
| band | Band | Band |
| band_not_found | Band nicht gefunden | Band not found |
| open_source_intro | Beide Apps und die API, die die Daten liefert, sind Open Source. | Both apps and the API that serves the data are open source. |
| download_schedule_hint | Verbinde dich einmal mit dem Internet, um das Programm zu laden. | Connect to the internet once to download the schedule. |
| appearance_dark | Dunkel | Dark |
| appearance_light | Hell | Light |
| appearance_system | System | System |
| favorites_only | Nur Favoriten | Favorites only |
| find_me_online | Online findest du mich | Find me online |
| reminders_footer | Erhalte 15 Minuten vorher eine Erinnerung, bevor eine favorisierte Band spielt. | Get a reminder 15 minutes before a favorited band plays. |
| bio_1 | Moin, ich bin Thore! Podcaster und Softwareentwickler aus dem Ruhrgebiet. Das Dong Open Air liebe ich schon seit vielen Jahren, und diese App ist quasi mein kleiner Liebesbrief an das Festival. | Hey, I'm Thore! A podcaster and software developer from the Ruhr area. I've loved Dong Open Air for many years, and this app is basically my little love letter to the festival. |
| bio_2 | Außerdem bin ich ein riesiger Magic: The Gathering Fan. Wenn wir uns auf dem Dong über den Weg laufen, lass uns gerne eine Runde spielen. Und wenn dir die App gefällt, freue ich mich riesig über ein Bier. | I'm also a huge Magic: The Gathering fan. If we bump into each other at Dong, let's play a round. And if you enjoy the app, I'd be happy if you grabbed me a beer. |
| lineup_title | Programm | Line-up |
| listen_and_follow | Anhören & Folgen | Listen & Follow |
| no_favorites | Keine Favoriten | No favorites |
| no_lineup_yet | Noch kein Programm | No line-up yet |
| no_upcoming_favorites | Keine anstehenden Auftritte deiner Favoriten. | No upcoming sets for your favorites. |
| nothing_on_stage | Gerade spielt niemand. | Nothing on stage right now. |
| notifications | Benachrichtigungen | Notifications |
| now | Jetzt | Now |
| on_stage_at (format) | Auf der Bühne um %1$s · %2$s | On stage at %1$s · %2$s |
| open_source | Open Source | Open Source |
| about_subtitle | Podcaster & Softwareentwickler aus dem Ruhrgebiet | Podcaster & software developer from the Ruhr area |
| questions_or_bugs | Fragen oder Bug-Reports? | Questions or bug reports? |
| reminders | Erinnerungen | Reminders |
| search_bands | Bands suchen | Search bands |
| settings | Einstellungen | Settings |
| star_to_see_here | Markiere eine Band mit einem Stern, damit sie hier erscheint. | Star a band to see it here. |
| tap_star_hint | Tippe auf den Stern einer Band, um ihr zu folgen. Du wirst erinnert, bevor sie spielt. | Tap the star on a band to follow it. You'll get a reminder before they play. |
| up_next | Demnächst | Up next |
| updated (format) | Aktualisiert %1$s | Updated %1$s |
| your_bands | Deine Bands | Your bands |
| dot_separator (format) | %1$s · %2$s | %1$s · %2$s |

**Not localized (verbatim in both):** "Dong Open Air" (Home title), "Mountain", "Band API", "Mastodon", "Bluesky", "Thore", and all URLs/handles/email.

---

## 14. Privacy & permissions

- iOS privacy manifest: **no tracking**, no collected data types declared; only declares UserDefaults API usage (reason `CA92.1`). TelemetryDeck is configured non-tracking.
- Android permissions needed:
  - `INTERNET` (network fetch).
  - `POST_NOTIFICATIONS` (API 33+) — request lazily, the first time reminders are enabled with a favorite (mirror §5.2).
  - `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` (API 31+) if using exact alarms for reminders. Handle the "exact alarm not permitted" case gracefully (fall back to inexact / WorkManager, or guide the user to settings).
  - `RECEIVE_BOOT_COMPLETED` if you want reminders to survive a reboot (re-schedule on boot via a receiver that re-runs `ReminderManager.sync`). The iOS scheduling persists across reboots automatically; on Android you must re-arm alarms after boot.
- No location, no contacts, no account.

---

## 15. Edge cases & behavioural details to preserve

1. **Slot without end time** → assume a **1-hour** duration for "now playing" / "upcoming" calculations (`end ?? start + 3600s`). Display shows only the start time when end is null.
2. **Trailing-slash URLs are "missing"** → never render an image/link for a URL ending in `/` (§2.1).
3. **PHPDate** → only the integer `timestamp` matters; ignore the rest (§3.3).
4. **Refresh never destroys data** on failure → status goes `Offline`, snapshot unchanged.
5. **Favorites are independent** of the schedule snapshot — refreshing replaces bands/slots but never favorites.
6. **upNext = one slot per stage**, the soonest not-yet-started one. With a single stage this is at most one row.
7. **Reminders are a full rebuild** every sync: cancel everything, re-add only future + favorited + enabled. Past reminders are never scheduled.
8. **Reminder identifier = `"<bandId>-<startEpochSeconds>"`** so re-syncs dedupe.
9. **30-second live tick** drives Now/Up next; the "Updated <relative>" line updates as time passes too.
10. **bands sorted case-insensitively** by name; **slots sorted by start**; **days sorted ascending**.
11. **"No line-up yet"** only when both bands and slots are empty (i.e. seed failed to load and never fetched). With the bundled seed present this should normally never show.
12. **Search + favorites-only compose** (both filters apply together); empty result shows the appropriate empty overlay (search-specific vs favorites-specific).
13. **Settings reminders toggle off** cancels all scheduled reminders immediately.
14. App title on Home is the literal **"Dong Open Air"** (unlocalized); tab/nav titles are localized.

---

## 16. Suggested module/file layout (Android)

```
app/
 ├─ data/
 │   ├─ model/        Band.kt, TimeSlot.kt, LineupSnapshot.kt
 │   ├─ remote/       BaphometApi.kt, dto/ (ApiStage, ApiTimeSlot, PhpDate)
 │   ├─ local/        SnapshotCache.kt (filesDir json), SeedLoader.kt
 │   └─ prefs/        SettingsPrefs.kt, FavoritesPrefs.kt (DataStore)
 ├─ domain/
 │   ├─ LineupStore.kt        (repository/holder, exposes StateFlow)
 │   ├─ FavoritesStore.kt
 │   └─ ReminderManager.kt    (+ ReminderReceiver, BootReceiver)
 ├─ ui/
 │   ├─ MainActivity.kt + AppScaffold (bottom nav, 3 tabs)
 │   ├─ home/         HomeScreen.kt, HomeViewModel.kt
 │   ├─ lineup/       RunningOrderScreen.kt, LineupViewModel.kt
 │   ├─ band/         BandDetailScreen.kt
 │   ├─ settings/     SettingsScreen.kt, AboutScreen.kt
 │   └─ common/       BandThumbnail.kt, FavoriteButton.kt, SlotRow.kt, Fmt.kt
 ├─ analytics/        Telemetry.kt
 └─ assets/ or res/raw/  lineup_seed.json + thore image
```

---

## 17. Acceptance checklist

- [ ] Cold launch with no network shows the **seed** schedule immediately; Now/Up next/Your bands populate correctly relative to current time.
- [ ] With network, a refresh updates data and writes `lineup_cache.json`; the "Updated …" footer appears.
- [ ] Refresh failure keeps existing data and does not crash.
- [ ] PHPDate timestamps parse; large timezone blobs are ignored.
- [ ] Trailing-slash URLs render no image/link.
- [ ] Favoriting persists across app restarts and is untouched by refresh.
- [ ] Favoriting the first band (reminders on) prompts for notification permission; a reminder fires ~15 min before the set.
- [ ] Turning reminders off cancels all scheduled notifications.
- [ ] Line-up groups by day, search + favorites-only filter correctly, with correct empty states.
- [ ] Band detail shows artwork/genre/sets/description/links conditionally; opens external links.
- [ ] Appearance switch (System/Light/Dark) re-themes the app.
- [ ] Full German + English localization; German is the default.
- [ ] Analytics signals `Band.favorited`/`Band.unfavorited`/`Band.viewed` fire with `bandID`.
- [ ] 30-second live tick advances Now/Up next without interaction.
```
