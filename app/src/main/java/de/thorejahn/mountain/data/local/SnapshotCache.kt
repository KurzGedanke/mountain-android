package de.thorejahn.mountain.data.local

import android.content.Context
import de.thorejahn.mountain.data.model.LineupSnapshot
import de.thorejahn.mountain.data.remote.AppJson
import kotlinx.serialization.encodeToString
import java.io.File

/** On-disk snapshot cache in filesDir as `lineup_cache.json` (§6.1). */
class SnapshotCache(context: Context) {
    private val file = File(context.filesDir, "lineup_cache.json")

    fun read(): LineupSnapshot? = runCatching {
        if (!file.exists()) return null
        AppJson.decodeFromString<LineupSnapshot>(file.readText())
    }.getOrNull()

    /** Atomic best-effort write (temp file + rename). Failures are swallowed. */
    fun write(snapshot: LineupSnapshot) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(snapshot))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }
}
