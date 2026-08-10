package com.bellizia.mcmonitor.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GameRelease(val id: String, val type: String, val released: String) {
    val isRelease: Boolean get() = type == "release"
    val dateLabel: String get() = released.take(10)
}

/** Elenco ufficiale delle versioni di Minecraft, dal manifesto pubblico di Mojang. */
object MojangVersions {

    private const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    suspend fun list(includeSnapshots: Boolean = false): List<GameRelease> =
        withContext(Dispatchers.IO) {
            val json = JSONObject(fetch())
            val versions = json.optJSONArray("versions")
            (0 until (versions?.length() ?: 0)).mapNotNull { i ->
                val o = versions?.optJSONObject(i) ?: return@mapNotNull null
                GameRelease(
                    id = o.optString("id"),
                    type = o.optString("type"),
                    released = o.optString("releaseTime")
                ).takeIf { it.id.isNotBlank() }
            }.filter { includeSnapshots || it.isRelease }
        }

    private fun fetch(): String {
        val connection = (URL(MANIFEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "mc-monitor-android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("manifesto Mojang non disponibile (${connection.responseCode})")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
