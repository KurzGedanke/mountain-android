package de.thorejahn.mountain.data.local

import android.content.Context
import de.thorejahn.mountain.data.model.LineupSnapshot
import de.thorejahn.mountain.data.remote.AppJson

/** Loads the bundled `assets/lineup_seed.json` snapshot (§6.1, §9). */
class SeedLoader(private val context: Context) {
    fun load(): LineupSnapshot? = runCatching {
        val text = context.assets.open("lineup_seed.json").bufferedReader().use { it.readText() }
        AppJson.decodeFromString<LineupSnapshot>(text)
    }.getOrNull()
}
