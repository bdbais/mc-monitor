package com.bellizia.mcmonitor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scarica l'APK di una release e lo passa all'installer di sistema.
 * Android chiede comunque conferma all'utente: l'app non installa nulla da sola.
 */
object ApkInstaller {

    suspend fun download(context: Context, update: Update): File = withContext(Dispatchers.IO) {
        require(update.apkUrl.startsWith("https://")) { "collegamento non sicuro" }

        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        // Una sola copia alla volta: gli aggiornamenti vecchi non servono più.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "MC-Monitor-${update.version}.apk")

        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "mc-monitor-android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("il server ha risposto ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (target.length() < 1024) {
            target.delete()
            error("download incompleto")
        }
        target
    }

    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
