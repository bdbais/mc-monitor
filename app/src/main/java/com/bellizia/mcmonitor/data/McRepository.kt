package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.BackupState
import com.bellizia.mcmonitor.lgsm.Backups
import com.bellizia.mcmonitor.lgsm.ChatMessage
import com.bellizia.mcmonitor.lgsm.ComandoLgsm
import com.bellizia.mcmonitor.lgsm.Cron
import com.bellizia.mcmonitor.lgsm.Crontab
import com.bellizia.mcmonitor.lgsm.LgsmCommands
import com.bellizia.mcmonitor.lgsm.PianoBackup
import com.bellizia.mcmonitor.lgsm.GameSettings
import com.bellizia.mcmonitor.lgsm.GameVersion
import com.bellizia.mcmonitor.lgsm.JoinAttempt
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.lgsm.ServerParam
import com.bellizia.mcmonitor.lgsm.ServerParams
import com.bellizia.mcmonitor.lgsm.VersionConfig
import com.bellizia.mcmonitor.lgsm.PlayerEntry
import com.bellizia.mcmonitor.lgsm.PlayerPos
import com.bellizia.mcmonitor.lgsm.Provision
import com.bellizia.mcmonitor.lgsm.RequirementResult
import com.bellizia.mcmonitor.lgsm.ServerInspection
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

    // ------------------------------------------- parametri di LinuxGSM

    /** Tutte le righe del file di configurazione, commentate comprese. */
    suspend fun params(): List<ServerParam> {
        val c = cfg()
        val r = SshManager.exec(c, ServerParams.readAll(c), 30_000)
        if (r.exitCode == ServerParams.EXIT_NO_CONFIG) {
            throw SshException("File di configurazione non trovato: ${GameVersion.configPath(c)}")
        }
        return ServerParams.parse(r.text)
    }

    /**
     * Tutti i parametri di LinuxGSM con il valore in vigore.
     *
     * Legge tutti e cinque i file della catena, non solo quello dell'istanza:
     * su un server appena installato quello e' vuoto, e leggerlo da solo faceva
     * dire alla schermata che non c'era nessuna impostazione mentre il server ne
     * stava usando una ventina.
     */
    suspend fun paramsChain(): List<ServerParams.ParamEffettivo> {
        val c = cfg()
        val r = SshManager.exec(c, ServerParams.readChain(c), 45_000)
        if (r.exitCode == ServerParams.EXIT_NO_CONFIG) {
            throw SshException(
                "Cartella di configurazione non trovata: " +
                        "${c.lgsmDir.trimEnd('/')}/lgsm/config-lgsm/${c.script}"
            )
        }
        return ServerParams.parseChain(r.text)
    }

    /** Scrive un parametro, tenendo una copia datata del file. */
    suspend fun setParam(key: String, value: String): String {
        val c = cfg()
        val r = SshManager.exec(c, ServerParams.set(c, key, value), 30_000)
        val testo = Lgsm.clean(r.text).trim()
        if (!ServerParams.written(testo)) throw SshException(testo.ifBlank { "Modifica non riuscita." })
        return testo
    }

    /** Commenta un parametro: LinuxGSM torna al valore di fabbrica. */
    suspend fun disableParam(key: String): String {
        val c = cfg()
        val r = SshManager.exec(c, ServerParams.disable(c, key), 30_000)
        val testo = Lgsm.clean(r.text).trim()
        if (!ServerParams.written(testo)) throw SshException(testo.ifBlank { "Modifica non riuscita." })
        return testo
    }

    // ------------------------------------------ impostazioni del gioco

    /** Le righe di server.properties, chiave per valore. */
    suspend fun properties(): Map<String, String> {
        val c = cfg()
        val r = SshManager.exec(c, GameSettings.readAll(c), 30_000)
        if (r.exitCode == GameSettings.EXIT_NO_PROPERTIES) {
            throw SshException(
                "server.properties non c'è ancora: Minecraft lo scrive al primo avvio. " +
                        "Accendi il server una volta e torna qui."
            )
        }
        return GameSettings.parse(r.text)
    }

    /**
     * Scrive più impostazioni in una volta sola, e per quelle che hanno un comando
     * lo manda anche alla console.
     *
     * Sempre il file e poi il comando, mai solo il comando: il comando cambia il
     * mondo che sta girando adesso, il file decide come riparte. Se il server è
     * spento o non risponde, la scrittura è comunque andata a buon fine e va detto,
     * altrimenti sembra che non sia successo niente.
     */
    suspend fun setProperties(
        values: Map<String, String>,
        versione: String?
    ): String {
        val c = cfg()
        val r = SshManager.exec(c, GameSettings.setProperties(c, values), 45_000)
        val testo = Lgsm.clean(r.text).trim()
        if (!GameSettings.written(testo)) throw SshException(testo.ifBlank { "Modifica non riuscita." })

        val comandi = values.mapNotNull { (chiave, valore) ->
            GameSettings.byKey(chiave)?.let { GameSettings.commandFor(it, valore, versione) }
        }
        if (comandi.isEmpty()) return "salvato"

        val falliti = comandi.count { comando -> runCatching { send(comando) }.isFailure }
        return when {
            falliti == 0 -> "salvato, e già applicato"
            falliti == comandi.size ->
                "salvato nel file, ma il server non ha ricevuto i comandi: varrà dal prossimo avvio"
            else -> "salvato; $falliti comandi su ${comandi.size} non sono arrivati al server"
        }
    }

    // ------------------------------------------------- backup e programmazione

    /** Le copie di sicurezza che ci sono adesso, con lo spazio che resta. */
    suspend fun backupState(): BackupState {
        val c = cfg()
        val r = SshManager.exec(c, Backups.list(c), 60_000)
        return Backups.parse(r.text)
    }

    /**
     * Fa la copia adesso. Ci mette quanto ci mette: su un mondo grande sono
     * minuti, e di fabbrica LinuxGSM tiene il server fermo per tutto il tempo.
     */
    suspend fun backupNow(): String {
        val c = cfg()
        val r = SshManager.exec(c, Backups.now(c), 3_600_000)
        return Lgsm.clean(r.text).trim()
    }

    /** Il crontab dell'utente, letto senza interpretarlo. */
    suspend fun cronRead(): Crontab {
        val c = cfg()
        val r = SshManager.exec(c, Cron.read(), 30_000)
        return Cron.parseRead(r.text)
    }

    /**
     * Se sul computer c'è davvero un cron che gira, non solo il comando.
     *
     * Se non si riesce a chiedere, l'errore esce: dire "questo computer non ha
     * crontab" quando in realtà non ci si è nemmeno collegati è la stessa bugia
     * che si sta togliendo dalle altre schermate.
     */
    suspend fun cronDaemon(): Pair<Boolean, String> {
        val c = cfg()
        val t = Lgsm.clean(SshManager.exec(c, Cron.demone(), 20_000).text)
        val comando = t.contains("comando=si")
        val stato = when {
            !comando -> "Su questo computer non c'è il comando crontab: la programmazione non è possibile."
            t.contains("demone=no") -> "Il comando c'è, ma non vedo nessun cron in esecuzione: l'orario potrebbe non far partire niente."
            t.contains("demone=boh") -> "Non riesco a controllare se cron sta girando: se il backup non parte, è la prima cosa da guardare."
            else -> ""
        }
        return comando to stato
    }

    /**
     * Scrive (o toglie) la riga di backup nel crontab.
     *
     * Tre cose in fila, e nessuna si salta: si legge — e se non si è capito cosa
     * c'era ci si ferma, perché scrivere partendo da una lettura fallita vuol dire
     * cancellare il crontab di qualcun altro — si tiene una copia di com'era sul
     * telefono, e alla fine si rilegge per controllare che ci sia davvero.
     */
    suspend fun cronSchedule(piano: PianoBackup?): String {
        val c = cfg()

        val letto = cronRead()
        if (letto is Crontab.Illeggibile) {
            throw SshException(
                "Non riesco a leggere il crontab, quindi non lo tocco: ${letto.motivo}"
            )
        }
        val attuale = (letto as Crontab.Letto).testo

        // La copia di com'era resta sul telefono: è l'unico modo di rimettere le
        // cose a posto se qualcosa va storto.
        Prefs.saveCronBackup(c.id, attuale)

        val nuovo = Cron.componi(attuale, c, piano?.let { Cron.blocco(c, it) })
        val problemi = Cron.problemi(nuovo)
        if (problemi.isNotEmpty()) throw SshException(problemi.joinToString("\n"))

        SshManager.exec(c, "mkdir -p \"\$HOME\"/.mcmonitor && chmod 700 \"\$HOME\"/.mcmonitor", 20_000)
        SshManager.upload(c, nuovo.toByteArray(Charsets.ISO_8859_1).inputStream(), Cron.FILE_NUOVO)

        val r = SshManager.exec(c, Cron.install(), 45_000)
        val testo = Lgsm.clean(r.text)
        if (testo.contains("@@CR")) {
            throw SshException("Il file conteneva un ritorno a capo di Windows: non l'ho installato.")
        }
        val rc = Cron.installato(testo)
        if (rc == null || rc != 0) {
            throw SshException(
                "crontab ha rifiutato il file" + (rc?.let { " (uscita $it)" } ?: "") +
                        ". Il crontab di prima è rimasto com'era."
            )
        }

        // crontab può uscire con zero senza aver scritto niente: si controlla.
        val riletto = Cron.parseRead(r.text)
        if (riletto is Crontab.Letto) {
            val esiste = Cron.programmato(riletto.testo, c)
            if (piano != null && !esiste) throw SshException("Scritto, ma rileggendo non c'è: controlla il crontab a mano.")
            if (piano == null && esiste) throw SshException("Tolto, ma rileggendo c'è ancora: controlla il crontab a mano.")
        }

        return if (piano == null) "Backup automatico tolto." else "Backup automatico: ${piano.descrizione()}."
    }

    /** Rimette il crontab com'era prima dell'ultima modifica fatta dall'app. */
    suspend fun cronRestore(): String {
        val c = cfg()
        val prima = Prefs.cronBackup(c.id)
            ?: throw SshException("Non ho nessuna copia di com'era: non è mai stato modificato da qui.")
        val testo = if (prima.isEmpty() || prima.endsWith("\n")) prima else prima + "\n"

        SshManager.exec(c, "mkdir -p \"\$HOME\"/.mcmonitor && chmod 700 \"\$HOME\"/.mcmonitor", 20_000)
        SshManager.upload(c, testo.toByteArray(Charsets.ISO_8859_1).inputStream(), Cron.FILE_NUOVO)
        val r = SshManager.exec(c, Cron.install(), 45_000)
        val rc = Cron.installato(Lgsm.clean(r.text))
        if (rc != 0) throw SshException("Ripristino non riuscito: il crontab è rimasto com'è adesso.")
        return "Crontab rimesso com'era."
    }

    /** La coda del registro dei backup automatici, per capire perché non è partito. */
    suspend fun cronLog(): String {
        val c = cfg()
        val comando = "f=\"\$HOME\"/.mcmonitor/backup.log; " +
                "[ -f \"\$f\" ] || { echo 'nessun registro: il backup automatico non e mai partito'; exit 0; }; " +
                // Si accorcia mentre lo si legge: cosi non cresce all'infinito.
                "tail -n 400 \"\$f\" > \"\$f.tmp\" && mv \"\$f.tmp\" \"\$f\"; tail -n 60 \"\$f\""
        return Lgsm.clean(SshManager.exec(c, comando, 30_000).text).trim()
    }

    // ------------------------------------------------- comandi dello script

    /** Lancia un comando di LinuxGSM fra quelli in elenco. */
    suspend fun runLgsm(comando: ComandoLgsm): Pair<String, String> {
        val c = cfg()
        val r = SshManager.exec(c, LgsmCommands.run(c, comando), comando.secondi * 1000L + 30_000L)
        val testo = Lgsm.clean(r.text).trim()
        return LgsmCommands.esito(testo) to Privacy.text(testo, c)
    }

    /** Quando quel giocatore ha lasciato l'ultima traccia nel log. */
    suspend fun lastSeen(player: String): String? {
        val c = cfg()
        val raw = runCatching { SshManager.exec(c, Lgsm.lastSeen(c, player), 45_000).text }.getOrNull()
            ?: return null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY).format(Date())
        return Lgsm.parseLastSeen(raw, today)
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
    suspend fun online(withPositions: Boolean = true): OnlineSnapshot = online(cfg(), withPositions)

    /**
     * Variante con il server esplicito: il servizio di notifica controlla anche
     * profili diversi da quello attivo e non deve cambiarlo sotto l'interfaccia.
     */
    suspend fun online(c: ServerConfig, withPositions: Boolean): OnlineSnapshot {
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

    /** Strumenti presenti sul server, con la versione di Java quando disponibile. */
    suspend fun requirements(): Pair<List<RequirementResult>, String?> {
        val c = cfg()
        val r = SshManager.exec(c, Lgsm.checkRequirements(), 30_000)
        return Lgsm.parseRequirements(r.text) to Lgsm.parseJavaVersion(r.text)
    }

    /**
     * Cambia la password dell'utente SSH e, se il profilo si autentica con
     * password, aggiorna subito quella salvata: altrimenti il collegamento
     * successivo fallirebbe. La verifica finale riapre davvero la connessione.
     */
    suspend fun changeSshPassword(current: String, new: String): String {
        val c = cfg()
        val result = SshManager.changePassword(c, current, new)
        val text = Lgsm.clean(result.text).trim()

        if (result.exitCode != 0) {
            val reason = when {
                text.contains("Authentication token manipulation", true) ->
                    "il server ha rifiutato la modifica (password attuale errata?)"
                text.contains("BAD PASSWORD", true) || text.contains("too short", true) ->
                    "la nuova password non rispetta i criteri del server"
                text.contains("do not match", true) || text.contains("non corrispondono", true) ->
                    "le due nuove password non coincidono"
                result.exitCode == -1 -> "nessuna risposta da passwd entro 30 secondi"
                else -> "uscita ${result.exitCode}"
            }
            throw SshException("Password non cambiata: $reason.\n\n$text")
        }

        // Da qui la password sul server è nuova: salvarla è la parte da non sbagliare.
        if (c.password.isNotBlank()) {
            Prefs.save(Prefs.load().copy(password = new))
        }
        SshManager.disconnect()

        val verify = runCatching { SshManager.test(Prefs.load()) }
        return buildString {
            append("Password cambiata sul server.\n")
            if (c.password.isNotBlank()) {
                append("La password salvata nel profilo è stata aggiornata.\n")
            } else {
                append("Il profilo usa la chiave SSH: non c'era una password da aggiornare.\n")
            }
            append("\nVerifica del nuovo accesso: ")
            append(verify.fold(
                onSuccess = { "riuscita.\n$it" },
                onFailure = { "FALLITA.\n${it.message}\n\nRiapri le Impostazioni e correggi la password prima di chiudere l'app." }
            ))
            if (text.isNotBlank()) append("\n\n$text")
        }
    }

    // ------------------------------------------------ preparazione da zero

    suspend fun inspectServer(): ServerInspection {
        val c = cfg()
        return Provision.parseInspection(SshManager.exec(c, Provision.inspect(c), 30_000).text)
    }

    /**
     * Installa LinuxGSM su una home vuota, scarica il server e applica le
     * impostazioni di sicurezza. Ogni passo viene riportato mentre procede:
     * l'auto-install richiede minuti e senza riscontro sembra bloccato.
     */
    suspend fun prepareServer(harden: Boolean, onStep: (String) -> Unit): String {
        val c = cfg()
        val report = StringBuilder()
        fun log(line: String) {
            report.append(line).append('\n')
            onStep(report.toString())
        }

        log("1/5 · Controllo dei requisiti…")
        val (requirements, java) = requirements()
        val missing = requirements.filter { it.blocking }
        if (missing.isNotEmpty()) {
            log("     mancano: ${missing.joinToString(", ") { it.requirement.command }}")
            log("\nSenza questi pacchetti l'installazione non può proseguire, e servono i")
            log("privilegi di amministratore per aggiungerli:")
            log("sudo apt install " + missing.joinToString(" ") { it.requirement.command })
            return report.toString()
        }
        log("     tutto presente" + (java?.let { " · Java $it" } ?: ""))

        log("\n2/5 · Ispezione della directory…")
        val inspection = inspectServer()
        log("     utente ${inspection.user ?: "?"} · home ${inspection.home ?: "?"}")
        log("     spazio libero: ${inspection.freeMegabytes ?: "?"} MB")
        if (!inspection.isEmpty) {
            log("\nLinuxGSM è già installato in ${c.lgsmDir}: preparazione annullata.")
            return report.toString()
        }
        if (!inspection.enoughSpace) {
            log("\nSpazio insufficiente: servono almeno 2 GB liberi.")
            return report.toString()
        }

        log("\n3/5 · Installazione di LinuxGSM…")
        val install = SshManager.exec(c, Provision.installLinuxGsm(c), 300_000)
        Lgsm.clean(install.text).trim().lines().takeLast(8).forEach { log("     $it") }
        if (!install.ok) {
            log("\nInstallazione interrotta (uscita ${install.exitCode}).")
            return report.toString()
        }

        log("\n4/5 · auto-install: scarico del server, può richiedere diversi minuti…")
        val auto = runCatching { SshManager.exec(c, Provision.autoInstall(c), 1_800_000) }
        auto.getOrNull()?.let {
            Lgsm.clean(it.text).trim().lines().takeLast(12).forEach { line -> log("     $line") }
        } ?: log("     ERRORE: ${auto.exceptionOrNull()?.message}")

        if (!harden) {
            log("\nFatto. Le impostazioni di sicurezza non sono state applicate.")
            return report.toString()
        }

        log("\n5/5 · Impostazioni di sicurezza…")
        val hardened = runCatching { SshManager.exec(c, Provision.harden(c), 60_000) }.getOrNull()
        val text = hardened?.let { Lgsm.clean(it.text).trim() }.orEmpty()
        if (hardened?.ok == true) {
            text.lineSequence().forEach { log("     $it") }
            log("\nIl server è pronto. La whitelist è attiva: aggiungi i giocatori dalla")
            log("scheda Giocatori prima che possano entrare.")
        } else {
            log("     non applicate: $text")
            log("\nRiprova dopo il primo avvio del server, quando server.properties esiste.")
        }
        return report.toString()
    }

    // -------------------------------------------------- versione di Minecraft

    suspend fun versionConfig(): VersionConfig {
        val c = cfg()
        val r = SshManager.exec(c, GameVersion.readConfig(c), 30_000)
        if (r.exitCode == GameVersion.EXIT_NO_CONFIG) {
            throw SshException(
                "File di configurazione LinuxGSM non trovato:\n${GameVersion.configPath(c)}\n\n" +
                        "Controlla directory e nome dello script nelle Impostazioni."
            )
        }
        return GameVersion.parseConfig(r.text)
    }

    /**
     * Cambia versione: scrive mcversion nella configurazione e lancia `update`,
     * che scarica il jar richiesto e riavvia il server.
     */
    suspend fun changeVersion(
        version: String,
        branch: String,
        branchKey: String,
        withBackup: Boolean,
        onStep: (String) -> Unit
    ): String {
        val c = cfg()
        val report = StringBuilder()
        fun log(line: String) {
            report.append(line).append('\n')
            onStep(report.toString())
        }

        if (withBackup) {
            log("1/3 · Backup del mondo con LinuxGSM (può richiedere minuti)…")
            val backup = runCatching { SshManager.exec(c, GameVersion.backup(c), 900_000) }.getOrNull()
            log("     " + (backup?.let { Lgsm.clean(it.text).trim().lines().lastOrNull() } ?: "backup non riuscito"))
        }

        log("${if (withBackup) "2/3" else "1/2"} · Scrittura di mcversion=$version…")
        val write = SshManager.exec(c, GameVersion.setVersion(c, version, branch, branchKey), 45_000)
        val writeText = Lgsm.clean(write.text).trim()
        if (!write.ok) {
            log("     FALLITO: $writeText")
            return report.toString()
        }
        writeText.lineSequence().forEach { log("     $it") }

        log("\n${if (withBackup) "3/3" else "2/2"} · ./${c.script} update — scarica e riavvia…")
        val update = runCatching { SshManager.exec(c, GameVersion.update(c), 900_000) }
        log(update.fold(
            onSuccess = { Lgsm.clean(it.text).trim().lines().takeLast(12).joinToString("\n") { l -> "     $l" } },
            onFailure = { "     ERRORE: ${it.message}" }
        ))
        return report.toString()
    }

    private suspend fun run(command: String, timeout: Long): String {
        val r = SshManager.exec(cfg(), command, timeout)
        return Lgsm.clean(r.text).trim()
    }
}
