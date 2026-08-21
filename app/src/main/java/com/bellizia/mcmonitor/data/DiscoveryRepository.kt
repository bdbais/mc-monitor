package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.DiscoveredServer
import com.bellizia.mcmonitor.lgsm.Discovery
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
        return text
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
