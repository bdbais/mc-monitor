package com.bellizia.mcmonitor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    /** La pagina da cui si scarica a mano, quando l'app non può farlo da sola. */
    const val PAGINA_VERSIONI = "https://github.com/bdbais/mc-monitor/releases/latest"

    /**
     * Se questa copia dell'app è in grado di passare un file all'installer.
     *
     * La variante di prova nasce senza FileProvider — è tolto apposta, serviva a
     * capire quale componente un telefono rifiutasse — e senza quello il file non
     * si può consegnare a nessuno. Meglio saperlo prima di scaricare venti
     * megabyte, e dirlo con parole invece che con l'eccezione di Android.
     */
    fun puoInstallare(context: Context): Boolean =
        context.packageManager.resolveContentProvider("${context.packageName}.updates", 0) != null

    /**
     * Se manca solo il consenso di sistema a installare app da questa app.
     *
     * È una cosa diversa dalla precedente: qui l'app potrebbe, ma Android non la
     * lascia finché non si dice di sì una volta nelle impostazioni.
     */
    fun servePermesso(context: Context): Boolean =
        !context.packageManager.canRequestPackageInstalls()

    /**
     * La schermata di sistema dove si dà quel consenso.
     *
     * Se il telefono non ha quella schermata — capita su certe versioni — si
     * ripiega sui dettagli dell'app, da cui ci si arriva comunque.
     */
    fun impostazioniPermesso(context: Context): Intent {
        val diretto = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (diretto.resolveActivity(context.packageManager) != null) return diretto

        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun paginaVersioni(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(PAGINA_VERSIONI))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
