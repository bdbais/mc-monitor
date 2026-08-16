package com.bellizia.mcmonitor.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup automatico della configurazione in una cartella scelta dall'utente.
 *
 * La cartella si sceglie con il selettore di sistema: va bene qualsiasi servizio
 * che si presenti come provider di documenti — OneDrive, Dropbox, o una cartella
 * locale sincronizzata da Drive. Così non serve nessun account dentro l'app e le
 * credenziali del cloud non passano da qui.
 *
 * Il file è lo stesso dell'esportazione manuale: cifrato con AES-GCM, inutile
 * senza la password.
 */
object BackupManager {

    private const val PREFIX = "mcmonitor-"
    private const val SUFFIX = ".mcm"
    private const val KEEP = 7

    /** Salva una copia se il backup automatico è configurato. Non lancia eccezioni. */
    suspend fun backupIfEnabled(context: Context): String? = withContext(Dispatchers.IO) {
        if (!Prefs.backupEnabled) return@withContext null
        val folder = Prefs.backupFolder.takeIf { it.isNotBlank() } ?: return@withContext null
        val password = Prefs.backupPassword.takeIf { it.length >= 8 } ?: return@withContext null
        runCatching { write(context, Uri.parse(folder), password) }.getOrNull()
    }

    /** Scrittura esplicita, con l'errore visibile: usata dal pulsante "Fai un backup ora". */
    suspend fun backupNow(context: Context, folder: Uri, password: String): String =
        withContext(Dispatchers.IO) { write(context, folder, password) }

    private fun write(context: Context, folder: Uri, password: String): String {
        val tree = DocumentFile.fromTreeUri(context, folder)
            ?: throw TransferException("Cartella di backup non più accessibile.")
        if (!tree.canWrite()) {
            throw TransferException("Non ho il permesso di scrivere nella cartella scelta.")
        }

        val servers = Prefs.servers()
        if (servers.isEmpty()) throw TransferException("Nessun server da salvare.")
        val content = ConfigTransfer.export(servers, password)

        val name = PREFIX + SimpleDateFormat("yyyyMMdd", Locale.ITALY).format(Date()) + SUFFIX
        // Un file al giorno: sovrascritto se già esiste, così la cartella non esplode.
        tree.findFile(name)?.delete()
        val file = tree.createFile("application/octet-stream", name)
            ?: throw TransferException("Impossibile creare il file di backup.")

        context.contentResolver.openOutputStream(file.uri)?.use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw TransferException("Impossibile scrivere il file di backup.")

        prune(tree)
        Prefs.backupLast = System.currentTimeMillis()
        return name
    }

    /** Tiene solo gli ultimi backup: le vecchie copie non servono e occupano spazio. */
    private fun prune(tree: DocumentFile) {
        val backups = tree.listFiles()
            .filter { it.name?.startsWith(PREFIX) == true && it.name?.endsWith(SUFFIX) == true }
            .sortedByDescending { it.name }
        backups.drop(KEEP).forEach { runCatching { it.delete() } }
    }
}
