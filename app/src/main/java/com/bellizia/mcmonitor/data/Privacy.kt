package com.bellizia.mcmonitor.data

/**
 * Modalità privacy: nasconde a schermo ciò che non deve finire in uno screenshot
 * mandato in giro — indirizzo del server, nomi dei giocatori, password RCON.
 *
 * Il mascheramento è parziale: restano le prime lettere, così si continua a
 * distinguere un giocatore dall'altro senza rivelare chi sia. I valori veri non
 * vengono toccati: copia e condivisione usano sempre l'originale, altrimenti la
 * funzione sarebbe inutile proprio quando serve.
 */
object Privacy {

    private const val DOTS = "•••"

    val enabled: Boolean get() = Prefs.privacyMode

    /** "Steve" -> "St•••". Sotto le tre lettere si nasconde tutto. */
    fun name(value: String): String {
        if (!enabled || value.isBlank()) return value
        return if (value.length <= 3) DOTS else value.take(2) + DOTS
    }

    /**
     * "casa.example.com" -> "casa•••", "203.0.113.10" -> "203.•••".
     * Resta riconoscibile per chi lo possiede, inutile per chi lo vede.
     */
    fun host(value: String): String {
        if (!enabled || value.isBlank()) return value
        val port = value.substringAfter(':', "")
        val bare = value.substringBefore(':')
        val masked = when {
            bare.matches(IPV4) -> bare.substringBefore('.') + ".$DOTS"
            bare.contains('.') -> bare.substringBefore('.') + DOTS
            else -> if (bare.length <= 3) DOTS else bare.take(3) + DOTS
        }
        return if (port.isBlank()) masked else "$masked:$port"
    }

    /** "mcserver@casa.example.com:22" -> "mc•••@casa•••:22" */
    fun account(user: String, hostPort: String): String =
        if (!enabled) "$user@$hostPort" else "${name(user)}@${host(hostPort)}"

    /**
     * Ripulisce un blocco di testo: log, output di LinuxGSM, report di diagnostica.
     * Nasconde indirizzi, indirizzi IP, la password RCON e i nomi che compaiono
     * nelle righe di chat o di ingresso/uscita.
     */
    fun text(raw: String, cfg: ServerConfig? = null, players: Collection<String> = emptyList()): String {
        if (!enabled) return raw
        var result = raw

        cfg?.let { server ->
            if (server.host.isNotBlank()) result = result.replace(server.host, host(server.host))
            if (server.rconPassword.isNotBlank()) result = result.replace(server.rconPassword, DOTS)
            if (server.password.isNotBlank()) result = result.replace(server.password, DOTS)
            if (server.keyPassphrase.isNotBlank()) result = result.replace(server.keyPassphrase, DOTS)
        }

        result = IPV4_IN_TEXT.replace(result) { match ->
            match.value.substringBefore('.') + ".$DOTS"
        }
        result = CHAT_LINE.replace(result) { match -> "<${name(match.groupValues[1])}>" }
        result = EVENT_LINE.replace(result) { match ->
            "${name(match.groupValues[1])} ${match.groupValues[2]} the game"
        }
        players.forEach { player ->
            if (player.length >= 3) result = result.replace(player, name(player))
        }
        return result
    }

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV4_IN_TEXT = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")
    private val CHAT_LINE = Regex("<([A-Za-z0-9_]{1,16})>")
    private val EVENT_LINE = Regex("\\b([A-Za-z0-9_]{3,16}) (joined|left) the game")
}
