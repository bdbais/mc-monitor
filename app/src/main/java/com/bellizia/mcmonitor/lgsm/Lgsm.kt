package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.json.JSONArray

/** Un giocatore presente nella whitelist o nella lista dei ban. */
data class PlayerEntry(
    val name: String,
    val uuid: String = "",
    val reason: String = "",
    val source: String = "",
    val created: String = ""
)

/** Una riga di chat (o un comando) scritta da un giocatore, con data e ora. */
data class ChatMessage(
    val date: String,
    val time: String,
    val player: String,
    val text: String,
    val isCommand: Boolean
) {
    val stamp: String get() = "$date $time".trim()
}

/** Uno strumento richiesto sul server. */
data class Requirement(
    val command: String,
    val why: String,
    val required: Boolean,
    val alternative: String? = null
)

data class RequirementResult(val requirement: Requirement, val present: Boolean) {
    val blocking: Boolean get() = requirement.required && !present
}

/** Tentativo di accesso di un giocatore, ricavato dai log del server. */
data class JoinAttempt(
    val name: String,
    val uuid: String,
    val date: String,
    val time: String,
    val outcome: Outcome
) {
    enum class Outcome { REJECTED_WHITELIST, JOINED, ATTEMPTED }

    val stamp: String get() = "$date $time".trim()

    val description: String
        get() = when (outcome) {
            Outcome.REJECTED_WHITELIST -> "respinto: non in whitelist"
            Outcome.JOINED -> "è entrato nel server"
            Outcome.ATTEMPTED -> "tentativo di accesso"
        }
}

/** Posizione di un giocatore online, letta dalla console del server. */
data class PlayerPos(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String = "minecraft:overworld"
) {
    val shortDimension: String
        get() = dimension.substringAfter(':').replace('_', ' ')
}

/**
 * Traduce le azioni dell'app in comandi shell per LinuxGSM e ne interpreta l'output.
 * Tutti i valori variabili passano da [sq] per non rompere la riga di comando.
 */
object Lgsm {
    private val ansiEscape = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")

    fun sq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Percorso pronto per la shell. Dentro apici singoli la tilde non viene
     * espansa, quindi `~/server1` diventa `"$HOME"/'server1'`: così la home
     * resta quella dell'utente SSH, qualunque sia.
     */
    fun path(value: String): String {
        val p = value.trim().trimEnd('/')
        return when {
            p == "~" || p.isEmpty() -> "\"\$HOME\""
            p.startsWith("~/") -> "\"\$HOME\"/" + sq(p.removePrefix("~/").trimStart('/'))
            else -> sq(p)
        }
    }

    private fun cd(cfg: ServerConfig) = "cd ${path(cfg.lgsmDir)}"

    private fun script(cfg: ServerConfig) = "./${sq(cfg.script)}"

    fun action(cfg: ServerConfig, action: String) = "${cd(cfg)} && ${script(cfg)} $action 2>&1"

    fun details(cfg: ServerConfig) = action(cfg, "details")

    fun start(cfg: ServerConfig) = action(cfg, "start")

    fun stop(cfg: ServerConfig) = action(cfg, "stop")

    fun restart(cfg: ServerConfig) = action(cfg, "restart")

    /**
     * Il log "vero" di Minecraft è latest.log; se manca (server appena installato o
     * percorso diverso) si ripiega sul log di console di LinuxGSM.
     */
    private fun logPicker(cfg: ServerConfig): String {
        val mc = "${cfg.serverFiles.trimEnd('/')}/logs/latest.log"
        val lgsm = "${cfg.lgsmDir.trimEnd('/')}/log/console/${cfg.script}-console.log"
        return "f=${path(mc)}; [ -f \"\$f\" ] || f=${path(lgsm)}"
    }

    fun tailLog(cfg: ServerConfig, lines: Int = 400) =
        "${logPicker(cfg)}; tail -n $lines \"\$f\" 2>&1"

    /** Codice di uscita convenzionale: la sessione tmux richiesta non esiste. */
    const val EXIT_NO_SESSION = 90

    fun sendCommand(cfg: ServerConfig, command: String): String {
        val clean = command.trimStart('/')
        if (cfg.useLgsmSend) return "${cd(cfg)} && ${script(cfg)} send ${sq(clean)} 2>&1"

        val session = sq(cfg.session)
        // Prima si verifica la sessione: altrimenti send-keys fallirebbe senza spiegare
        // che il problema è il nome della sessione (o l'utente sbagliato).
        return "tmux has-session -t $session 2>/dev/null || " +
                "{ echo 'sessioni tmux disponibili:'; tmux ls 2>&1; exit $EXIT_NO_SESSION; }; " +
                // -l invia il testo letterale, poi si conferma con Invio.
                "tmux send-keys -t $session -l ${sq(clean)} 2>&1 && " +
                "tmux send-keys -t $session Enter 2>&1"
    }

    /** Codice di uscita convenzionale: server.properties non trovato. */
    const val EXIT_NO_PROPERTIES = 91

    /**
     * Caratteri ammessi nella password RCON: restano fuori quelli che avrebbero un
     * significato per la shell o per `sed`, così il comando non può essere alterato.
     */
    val PASSWORD_CHARSET = Regex("^[A-Za-z0-9._-]{8,64}$")

    /**
     * Attiva RCON in server.properties tenendo una copia di sicurezza del file.
     * `broadcast-rcon-to-ops=false` evita che ogni comando dell'app compaia nella
     * chat degli operatori.
     */
    fun enableRcon(cfg: ServerConfig, port: Int, password: String): String {
        require(PASSWORD_CHARSET.matches(password)) { "password RCON non valida" }
        val file = "${cfg.serverFiles.trimEnd('/')}/server.properties"
        val f = sq(file)

        fun setProp(key: String, value: String): String {
            val escaped = key.replace(".", "\\.")
            return "if grep -q '^$escaped=' $f; then " +
                    "sed -i 's|^$escaped=.*|$key=$value|' $f; " +
                    "else printf '%s\\n' '$key=$value' >> $f; fi"
        }

        return "[ -f $f ] || { echo 'server.properties non trovato in $file'; exit $EXIT_NO_PROPERTIES; }; " +
                "cp $f \"$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\" && echo 'copia di sicurezza creata'; " +
                setProp("enable-rcon", "true") + "; " +
                setProp("rcon.port", port.toString()) + "; " +
                setProp("rcon.password", password) + "; " +
                setProp("broadcast-rcon-to-ops", "false") + "; " +
                "echo 'server.properties aggiornato:'; " +
                "grep -E '^(enable-rcon|rcon\\.port|broadcast-rcon-to-ops)=' $f"
    }

    /** Verifica che il server stia davvero ascoltando sulla porta RCON. */
    fun rconListening(port: Int): String =
        "(ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -E '[:.]$port[[:space:]]' " +
                "|| echo 'NESSUN PROCESSO IN ASCOLTO SULLA PORTA $port'"

    /** Legge la configurazione RCON attualmente scritta nel file del server. */
    fun readRconConfig(cfg: ServerConfig): String =
        "grep -E '^(enable-rcon|rcon\\.port)=' ${path("${cfg.serverFiles.trimEnd('/')}/server.properties")} " +
                "2>/dev/null || echo 'server.properties non leggibile'"

    /** Strumenti che devono esistere sul server, con il motivo per cui servono. */
    val REQUIREMENTS = listOf(
        Requirement("java", "esegue il server Minecraft", required = true),
        Requirement("tmux", "console del server: LinuxGSM ci gira dentro", required = true),
        Requirement("curl", "scarica mod e modpack", required = true, alternative = "wget"),
        Requirement("unzip", "legge i modpack .mrpack", required = true),
        Requirement("sha1sum", "verifica i file scaricati", required = true),
        Requirement("zgrep", "cronologia chat dei giorni scorsi", required = false)
    )

    fun checkRequirements(): String {
        val tools = (REQUIREMENTS.map { it.command } + REQUIREMENTS.mapNotNull { it.alternative })
            .distinct()
        return tools.joinToString("; ") { tool ->
            "if command -v $tool >/dev/null 2>&1; then echo '$tool=ok'; else echo '$tool=no'; fi"
        } + "; java -version 2>&1 | head -1"
    }

    fun parseRequirements(raw: String): List<RequirementResult> {
        val text = clean(raw)
        fun present(tool: String) = Regex("(?m)^$tool=ok$").containsMatchIn(text)
        return REQUIREMENTS.map { requirement ->
            val ok = present(requirement.command) ||
                    (requirement.alternative?.let { present(it) } == true)
            RequirementResult(requirement, ok)
        }
    }

    /** Versione di Java dichiarata dal server, utile perché 1.21 ne pretende almeno la 21. */
    fun parseJavaVersion(raw: String): String? =
        Regex("(?i)(openjdk|java) version \"?([0-9._]+)").find(clean(raw))?.groupValues?.get(2)

    /** Comandi usati dalla diagnostica per capire com'è fatto il server. */
    fun probes(cfg: ServerConfig): List<Pair<String, String>> = listOf(
        "utente / shell" to "id -un; echo \$SHELL",
        "tmux installato" to "command -v tmux && tmux -V || echo 'tmux ASSENTE'",
        "sessioni tmux" to "tmux ls 2>&1 || echo '(nessuna sessione per questo utente)'",
        "script LinuxGSM" to "ls -l ${path("${cfg.lgsmDir.trimEnd('/')}/${cfg.script}")} 2>&1",
        "supporto 'send'" to "${cd(cfg)} && ./${sq(cfg.script)} 2>&1 | grep -iE '^\\s*send' || " +
                "echo \"comando 'send' NON disponibile: usa la modalita' tmux\"",
        "log di gioco" to "ls -l ${path("${cfg.serverFiles.trimEnd('/')}/logs/latest.log")} " +
                "${path("${cfg.lgsmDir.trimEnd('/')}/log/console/${cfg.script}-console.log")} 2>&1",
        "RCON in server.properties" to readRconConfig(cfg),
        "porta RCON ${cfg.rconPort}" to rconListening(cfg.rconPort)
    )

    /**
     * Invia uno o più comandi alla console e restituisce le ultime righe di log:
     * è così che l'app legge il risultato di `list` o `data get entity`.
     */
    fun sendAndRead(cfg: ServerConfig, commands: List<String>, tailLines: Int = 120): String {
        val sends = commands.joinToString("; ") { "${sendCommand(cfg, it)} >/dev/null 2>&1" }
        val wait = if (commands.size > 3) "1.8" else "1.2"
        return "$sends; sleep $wait; ${tailLog(cfg, tailLines)}"
    }

    /**
     * Cerca nei log di gioco le righe di chat e i comandi di un giocatore.
     * Con `zgrep` disponibile guarda anche i log dei giorni precedenti (.log.gz).
     */
    fun readChat(cfg: ServerConfig, player: String, lines: Int = 400): String {
        val name = player.filter { it.isLetterOrDigit() || it == '_' }
        val logs = "${cfg.serverFiles.trimEnd('/')}/logs"
        val chat = sq("<$name>")
        val command = sq("$name issued server command")
        return "cd ${path(logs)} 2>/dev/null || { echo 'CARTELLA LOG NON TROVATA'; exit 0; }; " +
                "if command -v zgrep >/dev/null 2>&1; then " +
                "zgrep -aHF -e $chat -e $command latest.log *.log.gz 2>/dev/null; " +
                "else grep -aHF -e $chat -e $command latest.log 2>/dev/null; fi | tail -n $lines"
    }

    /**
     * Righe di log che segnalano un tentativo di accesso: l'assegnazione dell'UUID
     * (che avviene per ogni login autenticato), l'ingresso vero e proprio e il
     * rifiuto della whitelist.
     */
    fun readJoinAttempts(cfg: ServerConfig, lines: Int = 400): String {
        val logs = "${cfg.serverFiles.trimEnd('/')}/logs"
        return "cd ${path(logs)} 2>/dev/null || { echo 'CARTELLA LOG NON TROVATA'; exit 0; }; " +
                "if command -v zgrep >/dev/null 2>&1; then " +
                "zgrep -aHF -e 'UUID of player' -e 'joined the game' -e 'white-listed' " +
                "latest.log *.log.gz 2>/dev/null; " +
                "else grep -aHF -e 'UUID of player' -e 'joined the game' -e 'white-listed' " +
                "latest.log 2>/dev/null; fi | tail -n $lines"
    }

    /** Dice se il server sta davvero applicando la whitelist (`white-list=true`). */
    fun readWhitelistMode(cfg: ServerConfig) =
        "grep -E '^white-list=' ${path("${cfg.serverFiles.trimEnd('/')}/server.properties")} 2>/dev/null " +
                "|| echo 'white-list=?'"

    fun readWhitelist(cfg: ServerConfig) =
        "cat ${path("${cfg.serverFiles.trimEnd('/')}/whitelist.json")} 2>/dev/null || echo '[]'"

    fun readBanlist(cfg: ServerConfig) =
        "cat ${path("${cfg.serverFiles.trimEnd('/')}/banned-players.json")} 2>/dev/null || echo '[]'"

    // --------------------------------------------------------------- parsing

    /**
     * Toglie i colori ANSI di LinuxGSM lasciando intatto il testo del log
     * (che contiene parentesi quadre legittime come "[Server thread/INFO]").
     */
    fun clean(raw: String): String =
        ansiEscape.replace(raw, "").replace("\r", "")

    /** Estrae lo stato dichiarato da `lgsm details` (STARTED / STOPPED / ...). */
    fun parseStatus(details: String): String? =
        Regex("(?im)^\\s*Status:\\s*(.+)$").find(clean(details))?.groupValues?.get(1)?.trim()

    /** Coppie "Chiave: valore" della sezione details, usate per la scheda di stato. */
    fun parseDetails(details: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        clean(details).lineSequence().forEach { line ->
            val m = Regex("^\\s*([A-Za-z][A-Za-z0-9 /_.-]{1,28}):\\s+(\\S.*)$").find(line)
            if (m != null) {
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].trim()
                if (value.isNotEmpty() && !map.containsKey(key)) map[key] = value
            }
        }
        return map
    }

    fun parsePlayerJson(json: String): List<PlayerEntry> = runCatching {
        val arr = JSONArray(json.trim().ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").ifBlank { return@mapNotNull null }
            PlayerEntry(
                name = name,
                uuid = o.optString("uuid"),
                reason = o.optString("reason"),
                source = o.optString("source"),
                created = o.optString("created")
            )
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    /**
     * Legge l'ultima risposta al comando `list` presente nel log.
     * Copre sia il formato moderno ("There are 2 of a max of 20 players online: a, b")
     * sia quello vecchio ("There are 2/20 players online: a, b").
     */
    fun parseOnlinePlayers(log: String): List<String>? {
        val text = clean(log)
        val re = Regex("(?i)There are (\\d+)(?: of a max(?: of)? | */ *)(\\d+) players? online:?(.*)")
        val match = text.lineSequence().mapNotNull { re.find(it) }.lastOrNull() ?: return null
        return match.groupValues[3]
            .split(',')
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotBlank() && it.none { c -> c == '[' || c == ']' } }
    }

    fun parseMaxPlayers(log: String): Int? {
        val re = Regex("(?i)There are (\\d+)(?: of a max(?: of)? | */ *)(\\d+) players? online")
        return clean(log).lineSequence().mapNotNull { re.find(it) }.lastOrNull()
            ?.groupValues?.get(2)?.toIntOrNull()
    }

    /**
     * Estrae le posizioni dalle risposte di `data get entity <nome> Pos`
     * e la dimensione da `data get entity <nome> Dimension`.
     */
    fun parsePositions(log: String, players: List<String>): List<PlayerPos> {
        val text = clean(log)
        val positions = HashMap<String, Triple<Double, Double, Double>>()
        val dimensions = HashMap<String, String>()

        val posRe = Regex(
            "(\\w{1,32}) has the following entity data:\\s*\\[\\s*(-?[\\d.]+)d?,\\s*(-?[\\d.]+)d?,\\s*(-?[\\d.]+)d?\\s*]"
        )
        val dimRe = Regex("(\\w{1,32}) has the following entity data:\\s*\"([a-z0-9_:./-]+)\"")

        text.lineSequence().forEach { line ->
            posRe.find(line)?.let { m ->
                val x = m.groupValues[2].toDoubleOrNull()
                val y = m.groupValues[3].toDoubleOrNull()
                val z = m.groupValues[4].toDoubleOrNull()
                if (x != null && y != null && z != null) {
                    positions[m.groupValues[1]] = Triple(x, y, z)
                }
            }
            dimRe.find(line)?.let { m -> dimensions[m.groupValues[1]] = m.groupValues[2] }
        }

        return players.mapNotNull { name ->
            val p = positions[name] ?: return@mapNotNull null
            PlayerPos(name, p.first, p.second, p.third, dimensions[name] ?: "minecraft:overworld")
        }
    }

        /**
     * Ricostruisce data e ora di ogni riga: l'ora sta nella riga di log, la data nel
     * nome del file (`2026-08-01-1.log.gz`), mentre `latest.log` è la giornata odierna.
     */
    fun parseChat(raw: String, today: String): List<ChatMessage> {
        val fileRe = Regex("^([A-Za-z0-9._-]+\\.log(?:\\.gz)?):(.*)$")
        val timeRe = Regex("^\\[(\\d{2}:\\d{2}:\\d{2})")
        val chatRe = Regex("<([A-Za-z0-9_]{1,16})>\\s?(.*)$")
        val cmdRe = Regex("([A-Za-z0-9_]{1,16}) issued server command:\\s*(.*)$")
        val dateRe = Regex("^(\\d{4}-\\d{2}-\\d{2})")

        return clean(raw).lineSequence().mapNotNull { line ->
            val fileMatch = fileRe.find(line)
            val file = fileMatch?.groupValues?.get(1)
            val body = fileMatch?.groupValues?.get(2) ?: line
            val date = when {
                file == null || file == "latest.log" -> today
                else -> dateRe.find(file)?.groupValues?.get(1) ?: today
            }
            val time = timeRe.find(body.trim())?.groupValues?.get(1) ?: ""

            chatRe.find(body)?.let { m ->
                return@mapNotNull ChatMessage(date, time, m.groupValues[1], m.groupValues[2].trim(), false)
            }
            cmdRe.find(body)?.let { m ->
                return@mapNotNull ChatMessage(date, time, m.groupValues[1], m.groupValues[2].trim(), true)
            }
            null
        }.sortedWith(compareBy({ it.date }, { it.time })).toList()
    }

    /**
     * Ricostruisce l'ultimo tentativo di accesso per ogni giocatore.
     * L'UUID arriva dalla riga "UUID of player X is …", che precede sia gli ingressi
     * riusciti sia quelli respinti dalla whitelist.
     */
    fun parseJoinAttempts(raw: String, today: String): List<JoinAttempt> {
        val fileRe = Regex("^([A-Za-z0-9._-]+\\.log(?:\\.gz)?):(.*)$")
        val dateRe = Regex("^(\\d{4}-\\d{2}-\\d{2})")
        val timeRe = Regex("\\[(\\d{2}:\\d{2}:\\d{2})")
        val uuidRe = Regex("UUID of player ([A-Za-z0-9_]{1,16}) is ([0-9a-fA-F-]{32,36})")
        val joinRe = Regex("^([A-Za-z0-9_]{1,16}) joined the game")
        val rejectRe = Regex("name=([A-Za-z0-9_]{1,16})[,\\]].*white-listed")

        val uuids = HashMap<String, String>()
        val latest = LinkedHashMap<String, JoinAttempt>()

        clean(raw).lineSequence().forEach { line ->
            val fileMatch = fileRe.find(line)
            val file = fileMatch?.groupValues?.get(1)
            val body = fileMatch?.groupValues?.get(2) ?: line
            val date = when {
                file == null || file == "latest.log" -> today
                else -> dateRe.find(file)?.groupValues?.get(1) ?: today
            }
            val time = timeRe.find(body)?.groupValues?.get(1) ?: ""
            val message = body.substringAfter("]: ", body)

            uuidRe.find(message)?.let { m -> uuids[m.groupValues[1]] = m.groupValues[2] }

            val (name, outcome) = when {
                rejectRe.find(message) != null ->
                    rejectRe.find(message)!!.groupValues[1] to JoinAttempt.Outcome.REJECTED_WHITELIST
                joinRe.find(message) != null ->
                    joinRe.find(message)!!.groupValues[1] to JoinAttempt.Outcome.JOINED
                uuidRe.find(message) != null ->
                    uuidRe.find(message)!!.groupValues[1] to JoinAttempt.Outcome.ATTEMPTED
                else -> return@forEach
            }

            val attempt = JoinAttempt(name, uuids[name].orEmpty(), date, time, outcome)
            val previous = latest[name]
            if (previous == null || previous.stamp <= attempt.stamp) latest[name] = attempt
        }

        // L'UUID può comparire su una riga successiva a quella dell'esito.
        return latest.values
            .map { if (it.uuid.isBlank()) it.copy(uuid = uuids[it.name].orEmpty()) else it }
            .sortedByDescending { it.stamp }
    }

    /** Comandi da inviare per interrogare la posizione di ogni giocatore online. */
    fun positionQueries(players: List<String>): List<String> =
        players.flatMap { listOf("data get entity $it Pos", "data get entity $it Dimension") }
}
