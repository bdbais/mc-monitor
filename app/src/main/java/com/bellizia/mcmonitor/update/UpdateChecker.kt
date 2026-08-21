package com.bellizia.mcmonitor.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Update(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
    val sizeBytes: Long
) {
    val sizeLabel: String get() = "%.1f MB".format(sizeBytes / 1048576.0)
}

/**
 * Controlla se sul repository pubblico esiste una release più recente di quella
 * installata. L'APK viene pubblicato come allegato di ogni release GitHub.
 */
object UpdateChecker {

    private const val LATEST = "https://api.github.com/repos/bdbais/mc-monitor/releases/latest"

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /** Ritorna la novità solo se è davvero più recente di quella installata. */
    suspend fun check(context: Context): Update? = withContext(Dispatchers.IO) {
        val update = runCatching { fetchLatest() }.getOrNull() ?: return@withContext null
        if (isNewer(update.version, currentVersion(context))) update else null
    }

    /** Confronto numerico per segmenti: "1.10" è più recente di "1.9". */
    fun isNewer(remote: String, local: String): Boolean {
        val r = segments(remote)
        val l = segments(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun segments(version: String): List<Int> =
        version.trim().removePrefix("v")
            .split('.', '-', '+')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /**
     * Quale APK proporre quando la release ne contiene piu di uno.
     *
     * Prima il file che porta esattamente il nome della versione; poi qualsiasi
     * APK che non sia una variante di prova o una build senza firma. Prendere il
     * primo della lista, come faceva la 1.16, poteva installare l app di
     * diagnostica: ha un identificativo diverso e finiva accanto a quella vera
     * invece di aggiornarla.
     */
    fun pickApk(assets: List<JSONObject>, version: String): JSONObject? {
        val apk = assets.filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val atteso = "MC-Monitor-$version.apk"
        return apk.firstOrNull { it.optString("name").equals(atteso, ignoreCase = true) }
            ?: apk.firstOrNull { asset ->
                val nome = asset.optString("name").lowercase()
                listOf("prova", "diag", "non-firmato", "unsigned").none { nome.contains(it) }
            }
    }

    private fun fetchLatest(): Update? {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "mc-monitor-android")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return null
            val versione = json.optString("tag_name").removePrefix("v")
            val apk = pickApk(
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) },
                versione
            ) ?: return null

            return Update(
                version = versione,
                notes = json.optString("body").take(1500),
                apkUrl = apk.optString("browser_download_url"),
                pageUrl = json.optString("html_url"),
                sizeBytes = apk.optLong("size")
            ).takeIf { it.apkUrl.startsWith("https://") && it.version.isNotBlank() }
        } catch (e: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }
}
