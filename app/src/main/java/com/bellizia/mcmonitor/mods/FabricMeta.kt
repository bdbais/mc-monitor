package com.bellizia.mcmonitor.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Versioni scelte per l'installazione di Fabric su un server. */
data class FabricBuild(
    val gameVersion: String,
    val loaderVersion: String,
    val installerVersion: String
) {
    /**
     * URL ufficiale del jar di avvio del server: meta.fabricmc.net lo compone e
     * reindirizza al maven di Fabric, quindi il download va fatto seguendo i redirect.
     */
    val serverJarUrl: String
        get() = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion/" +
                "$loaderVersion/$installerVersion/server/jar"

    val label: String get() = "Fabric $loaderVersion per Minecraft $gameVersion"
}

/** API pubblica di FabricMC: nessuna autenticazione. */
object FabricMeta {

    private const val BASE = "https://meta.fabricmc.net/v2/versions"

    /** Ultima combinazione stabile di loader e installer per una versione di gioco. */
    suspend fun latestBuild(gameVersion: String): FabricBuild = withContext(Dispatchers.IO) {
        val loaders = JSONArray(get("$BASE/loader/$gameVersion"))
        val loader = (0 until loaders.length())
            .mapNotNull { loaders.optJSONObject(it)?.optJSONObject("loader") }
            .firstOrNull { it.optBoolean("stable") }
            ?: (0 until loaders.length()).firstNotNullOfOrNull {
                loaders.optJSONObject(it)?.optJSONObject("loader")
            }
            ?: error("nessun loader Fabric per Minecraft $gameVersion")

        val installers = JSONArray(get("$BASE/installer"))
        val installer = (0 until installers.length())
            .mapNotNull { installers.optJSONObject(it) }
            .firstOrNull { it.optBoolean("stable") }
            ?: installers.optJSONObject(0)
            ?: error("nessun installer Fabric disponibile")

        FabricBuild(
            gameVersion = gameVersion,
            loaderVersion = loader.optString("version"),
            installerVersion = installer.optString("version")
        )
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "mc-monitor-android")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("FabricMC ha risposto ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
