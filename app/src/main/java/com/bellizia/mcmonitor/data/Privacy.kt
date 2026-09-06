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

    /**
     * "Steve" -> "St•••". Sotto le tre lettere si nasconde tutto.
     *
     * Questa maschera sempre, senza guardare le preferenze: e' la parte che si
     * puo' provare da sola. [name] e' quella che decide se applicarla.
     */
    internal fun accorciaNome(value: String): String =
        if (value.length <= 3) DOTS else value.take(2) + DOTS

    fun name(value: String): String =
        if (!enabled || value.isBlank()) value else accorciaNome(value)

    /**
     * "casa.example.com" -> "casa•••", "203.0.113.10" -> "203.•••".
     * Resta riconoscibile per chi lo possiede, inutile per chi lo vede.
     */
    fun host(value: String): String =
        if (!enabled || value.isBlank()) value else accorciaHost(value)

    /** Come [host], ma maschera sempre: e' la parte provabile senza Android. */
    internal fun accorciaHost(value: String): String {
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
    fun text(raw: String, cfg: ServerConfig? = null, players: Collection<String> = emptyList()): String =
        if (!enabled) raw else maschera(raw, cfg, players)

    /**
     * Il mascheramento vero, staccato dall'interruttore.
     *
     * Sta separato per un motivo preciso: leggere le preferenze richiede un
     * contesto Android, e finche' le due cose erano insieme le verifiche su
     * questo codice uscivano subito senza provare niente. Passavano, e non
     * provavano niente.
     */
    internal fun maschera(
        raw: String,
        cfg: ServerConfig? = null,
        players: Collection<String> = emptyList(),
    ): String {
        var result = raw

        cfg?.let { server ->
            if (server.host.isNotBlank()) result = result.replace(server.host, accorciaHost(server.host))
            if (server.rconPassword.isNotBlank()) result = result.replace(server.rconPassword, DOTS)
            if (server.password.isNotBlank()) result = result.replace(server.password, DOTS)
            if (server.keyPassphrase.isNotBlank()) result = result.replace(server.keyPassphrase, DOTS)
        }

        result = IPV4_IN_TEXT.replace(result) { match ->
            match.value.substringBefore('.') + ".$DOTS"
        }

        /*
         * L'identificativo Mojang e' un dato personale quanto il nome, e piu'
         * duraturo: il nome si cambia, quello no. Nel registro compare per esteso
         * a ogni ingresso.
         */
        result = UUID_IN_TEXT.replace(result) { it.value.take(8) + DOTS }

        /*
         * Prima si raccolgono i nomi, poi si coprono ovunque.
         *
         * Riga per riga non si finisce piu': il registro nomina un giocatore in
         * una dozzina di forme diverse -- entra, esce, prende un progresso, viene
         * espulso, muore, scrive in chat -- e ogni forma dimenticata e' un nome in
         * chiaro in uno screenshot mandato a qualcuno. Le forme qui sotto servono
         * solo a capire QUALI sono nomi di persone; una volta saputo, si copre
         * ogni occorrenza, comprese quelle in righe mai previste.
         */
        val nomi = buildSet {
            listOf(CHAT_LINE, EVENT_LINE, UUID_LINE, LOGIN_LINE, PROVVEDIMENTO).forEach { re ->
                re.findAll(result).forEach { add(it.groupValues[1]) }
            }
            players.forEach { add(it) }
        }
        nomi.filter { it.length >= 3 && it.length <= 16 }
            .sortedByDescending { it.length }   // prima i lunghi, o "Bai" mangerebbe "Baisso"
            .forEach { giocatore ->
                result = Regex("\\b${Regex.escape(giocatore)}\\b").replace(result, accorciaNome(giocatore))
            }
        return result
    }

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV4_IN_TEXT = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")
    private val UUID_IN_TEXT =
        Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b")

    // Le forme da cui si capisce che una parola e' il nome di una persona.
    private val CHAT_LINE = Regex("<([A-Za-z0-9_]{1,16})>")
    private val EVENT_LINE = Regex("\\b([A-Za-z0-9_]{3,16}) (?:joined|left) the game")
    private val UUID_LINE = Regex("UUID of player ([A-Za-z0-9_]{3,16})")
    /**
     * `Baisso[/1.2.3.4:23081] logged in` e `FifthColumnMC (/1.2.3.4:13627) lost
     * connection`: due forme per la stessa cosa, e con la sola prima il nome di
     * chi si scollega restava in chiaro.
     */
    private val LOGIN_LINE = Regex("\\b([A-Za-z0-9_]{3,16})\\s?[\\[(]/")
    private val PROVVEDIMENTO = Regex(
        "\\b([A-Za-z0-9_]{3,16}) (?:lost connection|has made the advancement|has completed|" +
                "has reached|was kicked|issued server command|left the game|joined the game)"
    )
}
