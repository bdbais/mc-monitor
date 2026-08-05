package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.ChatMessage
import com.bellizia.mcmonitor.lgsm.JoinAttempt
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.PlayerEntry
import com.bellizia.mcmonitor.lgsm.PlayerPos
import com.bellizia.mcmonitor.rcon.RconManager
import com.bellizia.mcmonitor.ssh.SshException
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OnlineSnapshot(
    val names: List<String>,
    val maxPlayers: Int?,
    val positions: List<PlayerPos>
)

/** Punto unico da cui i fragment leggono/scrivono sul server. */
object McRepository {

    private fun cfg() = Prefs.load()

    suspend fun details(): String =
        Lgsm.clean(SshManager.exec(cfg(), Lgsm.details(cfg())).text)

    suspend fun start(): String = run(Lgsm.start(cfg()), 180_000)

    suspend fun stop(): String = run(Lgsm.stop(cfg()), 120_000)

    suspend fun restart(): String = run(Lgsm.restart(cfg()), 240_000)

    suspend fun log(lines: Int = 400): String =
        Lgsm.clean(SshManager.exec(cfg(), Lgsm.tailLog(cfg(), lines)).text)

    /**
     * LinuxGSM senza il comando `send` non fallisce: stampa l'elenco dei comandi e
     * esce con 0. Per questo l'esito non si giudica dal solo codice di uscita.
     */
    suspend fun send(command: String): String {
        val c = cfg()
        if (c.rconUsable) {
            // Con RCON la risposta del server arriva subito e non finisce nel log.
            return RconManager.exec(c, command.trimStart('/')).ifBlank { "comando eseguito" }
        }
        if (c.useLgsmSend) {
            val r = SshManager.exec(c, Lgsm.sendCommand(c, command))
            val text = Lgsm.clean(r.text).trim()
            if (r.ok && !looksUnsupported(text)) return text.ifBlank { "comando inviato" }
            // "send" non c'è (o non ha funzionato): da qui in poi si usa tmux.
            Prefs.setUseLgsmSend(false)
        }

        val tmux = c.copy(useLgsmSend = false)
        val r = SshManager.exec(tmux, Lgsm.sendCommand(tmux, command))
        val text = Lgsm.clean(r.text).trim()
        if (r.exitCode == Lgsm.EXIT_NO_SESSION) {
            throw SshException(
                "Sessione tmux \"${c.session}\" non trovata per l'utente ${c.user}.\n\n$text\n\n" +
                        "Se qui sopra non compare nessuna sessione, LinuxGSM gira con un altro " +
                        "utente: collegati con quello. Se compare con un nome diverso, scrivilo " +
                        "nel campo \"Sessione tmux\" delle Impostazioni."
            )
        }
        if (!r.ok) throw SshException("Invio comando fallito.\n$text")
        return "inviato alla console via tmux"
    }

    private fun looksUnsupported(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("unknown command") || lower.contains("usage:") ||
                lower.contains("commands") || lower.contains("not a valid")
    }

    /** Cronologia di chat e comandi di un giocatore, ordinata nel tempo. */
    suspend fun chat(player: String): List<ChatMessage> {
        val c = cfg()
        val raw = SshManager.exec(c, Lgsm.readChat(c, player), 60_000).text
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(Date())
        return Lgsm.parseChat(raw, today).filter { it.player.equals(player, ignoreCase = true) }
    }

    /**
     * Chi ha provato a entrare di recente, con l'esito dell'ultimo tentativo.
     * Il filtro fra "conosciuto" e "in attesa" lo fa il chiamante, che ha già
     * whitelist e lista ban in mano.
     */
    suspend fun joinAttempts(): List<JoinAttempt> {
        val c = cfg()
        val raw = SshManager.exec(c, Lgsm.readJoinAttempts(c), 60_000).text
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(Date())
        return Lgsm.parseJoinAttempts(raw, today)
    }

    /** true/false se il server applica la whitelist, null se non è leggibile. */
    suspend fun whitelistEnforced(): Boolean? {
        val c = cfg()
        val text = Lgsm.clean(SshManager.exec(c, Lgsm.readWhitelistMode(c), 20_000).text).trim()
        return when {
            text.contains("white-list=true") -> true
            text.contains("white-list=false") -> false
            else -> null
        }
    }

    suspend fun whitelist(): List<PlayerEntry> =
        Lgsm.parsePlayerJson(SshManager.exec(cfg(), Lgsm.readWhitelist(cfg())).stdout)

    suspend fun banlist(): List<PlayerEntry> =
        Lgsm.parsePlayerJson(SshManager.exec(cfg(), Lgsm.readBanlist(cfg())).stdout)

    /**
     * Chiede alla console chi è online e dove si trova.
     * Sono due giri: prima `list`, poi una `data get entity` per ogni giocatore.
     */
    suspend fun online(withPositions: Boolean = true): OnlineSnapshot {
        val c = cfg()
        if (c.rconUsable) return onlineViaRcon(c, withPositions)

        val listLog = Lgsm.clean(SshManager.exec(c, Lgsm.sendAndRead(c, listOf("list"), 60)).text)
        val names = Lgsm.parseOnlinePlayers(listLog) ?: emptyList()
        val max = Lgsm.parseMaxPlayers(listLog)
        if (!withPositions || names.isEmpty()) return OnlineSnapshot(names, max, emptyList())

        val queries = Lgsm.positionQueries(names)
        val posLog = Lgsm.clean(
            SshManager.exec(c, Lgsm.sendAndRead(c, queries, 40 + queries.size * 4), 90_000).text
        )
        val positions = Lgsm.parsePositions(posLog, names)
        PlayerTracker.update(positions)
        return OnlineSnapshot(names, max, positions)
    }

    /** Stessa lettura, ma su RCON: risposte dirette, nessuna riga scritta nel log. */
    private suspend fun onlineViaRcon(c: ServerConfig, withPositions: Boolean): OnlineSnapshot {
        val listReply = RconManager.exec(c, "list")
        val names = Lgsm.parseOnlinePlayers(listReply) ?: emptyList()
        val max = Lgsm.parseMaxPlayers(listReply)
        if (!withPositions || names.isEmpty()) return OnlineSnapshot(names, max, emptyList())

        val replies = buildString {
            names.forEach { name ->
                appendLine(RconManager.exec(c, "data get entity $name Pos"))
                appendLine(RconManager.exec(c, "data get entity $name Dimension"))
            }
        }
        val positions = Lgsm.parsePositions(replies, names)
        PlayerTracker.update(positions)
        return OnlineSnapshot(names, max, positions)
    }

    /**
     * Attiva RCON sul server: modifica server.properties (con copia di sicurezza),
     * riavvia LinuxGSM, attende che la porta risponda e prova l'autenticazione.
     * [step] riceve il report man mano, per mostrarlo mentre procede.
     */
    suspend fun setupRcon(port: Int, password: String, step: (String) -> Unit): String {
        val report = StringBuilder()
        fun log(line: String) {
            report.append(line).append('\n')
            step(report.toString())
        }

        val c = cfg()
        log("1/5 · Scrittura di server.properties…")
        val edit = SshManager.exec(c, Lgsm.enableRcon(c, port, password), 45_000)
        val editText = Lgsm.clean(edit.text).trim()
        if (edit.exitCode == Lgsm.EXIT_NO_PROPERTIES) {
            log("     FALLITO: $editText")
            log("\nControlla il percorso \"serverfiles\" nelle Impostazioni.")
            return report.toString()
        }
        if (!edit.ok) {
            log("     FALLITO (uscita ${edit.exitCode}): $editText")
            return report.toString()
        }
        editText.lineSequence().forEach { log("     $it") }

        log("\n2/5 · Riavvio del server…")
        val restart = runCatching { restart() }
        log(restart.fold(onSuccess = { "     riavvio inviato" }, onFailure = { "     ERRORE: ${it.message}" }))

        log("\n3/5 · Attesa della porta $port…")
        var listening = false
        repeat(12) { attempt ->
            if (listening) return@repeat
            delay(5_000)
            val check = runCatching { SshManager.exec(c, Lgsm.rconListening(port), 20_000) }.getOrNull()
            val text = check?.let { Lgsm.clean(it.text).trim() }.orEmpty()
            if (text.isNotBlank() && !text.contains("NESSUN PROCESSO")) {
                listening = true
                log("     in ascolto dopo ${(attempt + 1) * 5}s: ${text.lineSequence().first().trim()}")
            }
        }
        if (!listening) {
            log("     la porta $port non risulta in ascolto dopo 60s.")
            log("\nIl server potrebbe essere ancora in avvio: riprova \"Prova RCON\" tra poco.")
        }

        log("\n4/5 · Salvataggio della configurazione nell'app…")
        Prefs.save(c.copy(rconEnabled = true, rconPort = port, rconPassword = password))
        RconManager.disconnect()
        log("     RCON attivo, porta $port" + if (c.rconTunnel) ", tramite tunnel SSH" else ", collegamento diretto")

        log("\n5/5 · Prova di autenticazione…")
        val test = runCatching { RconManager.test(Prefs.load()) }
        log(test.fold(
            onSuccess = { "     OK · risposta del server: $it" },
            onFailure = { "     FALLITA: ${it.message}" }
        ))
        return report.toString()
    }

    private suspend fun run(command: String, timeout: Long): String {
        val r = SshManager.exec(cfg(), command, timeout)
        return Lgsm.clean(r.text).trim()
    }
}
