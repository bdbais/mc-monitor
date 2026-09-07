package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.DiscoveredServer
import com.bellizia.mcmonitor.lgsm.Discovery
import com.bellizia.mcmonitor.lgsm.Crontab
import com.bellizia.mcmonitor.lgsm.Cron
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.LgsmUpdate
import com.bellizia.mcmonitor.ssh.SshException
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Cerca sul computer i server già installati, per non farli cercare all'utente. */
object DiscoveryRepository {

    /**
     * La scansione attraversa la home e calcola le dimensioni delle cartelle:
     * su dischi lenti può richiedere qualche secondo, quindi timeout generoso.
     */
    suspend fun discover(account: ServerConfig): List<DiscoveredServer> {
        val result = SshManager.exec(account, Discovery.scan(), 120_000)
        return Discovery.parse(result.text)
    }

    /**
     * Cancella un'istanza dal computer. Il comando si ferma da solo se la cartella
     * non è un'istanza LinuxGSM: qui si controlla solo che abbia detto di sì.
     */
    suspend fun remove(account: ServerConfig, server: DiscoveredServer): String {
        val result = SshManager.exec(account, Discovery.remove(server), 180_000)
        val text = Lgsm.clean(result.text).trim()
        if (!Discovery.removed(text)) {
            throw SshException(text.ifBlank { "Il server non ha risposto alla cancellazione." })
        }
        return text + pulisciQuelCheResta(account, server)
    }

    /**
     * Quello che l'app aveva lasciato sul computer per quel server.
     *
     * La cartella dell'istanza se n'e' andata; restavano le righe di crontab e
     * i file sotto `~/.mcmonitor/`. Una riga di cron che ogni notte prova a
     * fare il backup di una cartella che non c'e' piu' e' una cosa che non si
     * spegne da sola e che nessuno sa da dove arrivi -- perche' da quel momento
     * l'app quel server non lo elenca nemmeno piu'.
     *
     * Non fa fallire la cancellazione: il server e' gia' andato, e dire
     * «cancellazione non riuscita» perche' non si e' potuto ripulire il crontab
     * sarebbe falso. Si dice cosa e' rimasto.
     */
    private suspend fun pulisciQuelCheResta(
        account: ServerConfig,
        server: DiscoveredServer,
    ): String = buildString {
        val slug = server.directory.trimEnd('/').substringAfterLast('/')

        val cron = runCatching {
            val letto = Cron.parseRead(SshManager.exec(account, Cron.read(), 30_000).text)
            val attuale = (letto as? Crontab.Letto)?.testo ?: return@runCatching 0
            val quanti = Cron.quantiBlocchi(attuale, slug)
            if (quanti > 0) {
                val nuovo = Cron.senzaBlocchi(attuale, slug)
                SshManager.upload(
                    account,
                    nuovo.toByteArray(Charsets.ISO_8859_1).inputStream(),
                    Cron.FILE_NUOVO
                )
                SshManager.exec(account, Cron.install(), 45_000)
            }
            quanti
        }.getOrNull()

        when {
            cron == null -> append("\n\nAttenzione: non sono riuscito a leggere il crontab. " +
                    "Se avevi programmato dei backup, quelle righe sono ancora li'.")
            cron > 0 -> append("\n\nTolte anche $cron righe programmate dal crontab.")
        }

        runCatching {
            SshManager.exec(account, Discovery.rimuoviTracce(slug), 30_000)
        }
    }

    /** Aggiorna gli script LinuxGSM di un'istanza: scarica e sostituisce i moduli. */
    suspend fun updateLgsm(account: ServerConfig, server: DiscoveredServer): String {
        val result = SshManager.exec(account, LgsmUpdate.updateCommand(server), 300_000)
        return Lgsm.clean(result.text).trim()
    }

    /**
     * Ultima versione pubblicata di LinuxGSM. Se GitHub non risponde si torna
     * null e l'avviso non compare: meglio niente che un allarme campato in aria.
     */
    suspend fun latestLgsmVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LgsmUpdate.LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "mc-monitor")
            }
            try {
                if (connection.responseCode != 200) return@runCatching null
                LgsmUpdate.parseTag(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
