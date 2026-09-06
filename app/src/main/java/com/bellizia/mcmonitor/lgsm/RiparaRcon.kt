package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/**
 * Perché RCON non va, e come rimetterlo a posto.
 *
 * L'attivazione scrive quattro righe in `server.properties` e riavvia. Quando
 * dopo non funziona, la schermata diceva soltanto che l'autenticazione era
 * fallita — e da lì non si va da nessuna parte, perché le cose che possono
 * essere andate storte sono sei o sette e si aggiustano in modi diversi.
 *
 * Quasi tutte sono invisibili guardando il file con gli occhi:
 *
 * - la stessa chiave scritta **due volte**, e vince l'ultima: il file mostra
 *   `enable-rcon=true` in cima e nessuno guarda venti righe più giù;
 * - un **a capo di Windows** in fondo alla riga, che a Minecraft fa risultare
 *   la password con un carattere in più che non si vede;
 * - uno **spazio in fondo**, uguale;
 * - la **password vuota**, che a Minecraft fa spegnere RCON senza dirlo;
 * - il file **giusto ma sbagliato**: quello che l'app modifica non è quello che
 *   il server legge, perché la cartella nelle impostazioni punta altrove. Tutto
 *   riesce, e non cambia niente.
 *
 * Qui si guarda tutto in un giro solo e si dice cosa si è trovato. La
 * riparazione riscrive le quattro righe da zero — le toglie tutte, comprese le
 * doppie, e le rimette una volta sola — dopo aver tolto gli a capo di Windows
 * da tutto il file.
 */
object RiparaRcon {

    /** Quanto è grave: decide l'ordine e se c'è qualcosa da riparare. */
    enum class Gravita { ROTTO, SOSPETTO, BENE }

    data class Problema(
        val gravita: Gravita,
        val cosa: String,
        /** Cosa farà la riparazione, o cosa deve fare la persona. */
        val rimedio: String,
        /** Se lo aggiusta il tasto, o se serve una mano. */
        val automatico: Boolean,
    )

    data class Diagnosi(val problemi: List<Problema>) {
        val rotto: Boolean get() = problemi.any { it.gravita == Gravita.ROTTO }
        val riparabile: Boolean
            get() = problemi.any { it.gravita != Gravita.BENE && it.automatico }
        val daFareAMano: List<Problema>
            get() = problemi.filter { it.gravita != Gravita.BENE && !it.automatico }
    }

    private const val M = "===" // i confini fra i pezzi della risposta

    /**
     * Tutto quello che serve, in un comando solo.
     *
     * Un giro per domanda vorrebbe dire sette collegamenti e sette attese, e su
     * un server lontano è la differenza fra «un attimo» e «mezzo minuto».
     *
     * I valori si stampano con `sed -n l`, che fa vedere gli a capo di Windows
     * come `\r` e mette un `$` alla fine: è l'unico modo per accorgersi di uno
     * spazio in fondo alla riga, che a occhio non c'è.
     */
    fun comandoDiagnosi(cfg: ServerConfig, porta: Int): String {
        val dir = cfg.serverFiles.trimEnd('/')
        val f = Lgsm.path("$dir/server.properties")
        val radice = Lgsm.path(cfg.lgsmDir.trimEnd('/'))
        return buildString {
            append("f=$f; ")
            append("echo '${M}ESISTE$M'; [ -f \"\$f\" ] && echo si || echo no; ")
            append("echo '${M}QUANTE$M'; ")
            append("for k in enable-rcon rcon.port rcon.password broadcast-rcon-to-ops; do ")
            append("n=\$(grep -c \"^\$k=\" \"\$f\" 2>/dev/null || echo 0); echo \"\$k=\$n\"; done; ")
            append("echo '${M}VALORI$M'; ")
            append("grep -E '^(enable-rcon|rcon\\.port|broadcast-rcon-to-ops)=' \"\$f\" 2>/dev/null | sed -n l; ")
            append("echo '${M}PASSWORD$M'; ")
            append("p=\$(sed -n 's/^rcon\\.password=//p' \"\$f\" 2>/dev/null | head -1); ")
            append("printf 'lunghezza=%s\\n' \"\${#p}\"; ")
            append("printf '%s' \"\$p\" | tr -d '\\r\\n' | sha256sum 2>/dev/null | cut -c1-8; ")
            append("echo '${M}ALTRIFILE$M'; ")
            append("find $radice -name server.properties -not -path \"\$f\" 2>/dev/null | head -5; ")
            append("echo '${M}ASCOLTO$M'; ")
            append("(ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | ")
            append("grep -E '[:.]$porta[[:space:]]' || echo 'nessuno in ascolto sulla $porta'; ")
            append("echo '${M}FINE$M'")
        }
    }

    private fun pezzo(testo: String, nome: String): String {
        val inizio = testo.indexOf("$M$nome$M")
        if (inizio < 0) return ""
        val dopo = testo.indexOf(M, inizio + nome.length + 2 * M.length)
        val fine = if (dopo < 0) testo.length else dopo
        return testo.substring(inizio + nome.length + 2 * M.length, fine).trim()
    }

    /**
     * Legge la risposta e dice cosa non va.
     *
     * [improntaApp] è quella della password salvata nell'app: si confrontano le
     * impronte perché quella del server non deve tornare indietro.
     */
    fun leggi(risposta: String, portaApp: Int, improntaApp: String): Diagnosi {
        val problemi = mutableListOf<Problema>()

        if (pezzo(risposta, "ESISTE") != "si") {
            return Diagnosi(
                listOf(
                    Problema(
                        Gravita.ROTTO,
                        "Il file server.properties non c'è dove l'app lo cerca.",
                        "Controlla la cartella del server nelle impostazioni. Se il server " +
                                "non è mai stato acceso, accendilo una volta: il file lo scrive lui.",
                        automatico = false,
                    )
                )
            )
        }

        // ------------------------------------------------ righe doppie
        val quante = pezzo(risposta, "QUANTE").lineSequence()
            .mapNotNull { riga ->
                val (k, v) = riga.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                k.trim() to v.trim().toIntOrNull()
            }.toMap()

        quante.filterValues { it != null && it > 1 }.forEach { (chiave, n) ->
            problemi += Problema(
                Gravita.ROTTO,
                "«$chiave» è scritta $n volte nel file.",
                "Minecraft tiene l'ultima e ignora le altre: il file può mostrare il " +
                        "valore giusto in cima e averne un altro più sotto. La riparazione " +
                        "le toglie tutte e la riscrive una volta sola.",
                automatico = true,
            )
        }

        // ------------------------------------------- valori con sporcizia
        val valori = pezzo(risposta, "VALORI").lineSequence()
            .map { it.trim().removeSuffix("$") }
            .filter { it.isNotBlank() }
            .toList()

        fun valoreDi(chiave: String): String? =
            valori.firstOrNull { it.startsWith("$chiave=") }?.removePrefix("$chiave=")

        val abilitato = valoreDi("enable-rcon")
        when {
            abilitato == null -> problemi += Problema(
                Gravita.ROTTO,
                "«enable-rcon» non c'è nel file.",
                "Senza, RCON resta spento. La riparazione la aggiunge.",
                automatico = true,
            )
            abilitato.contains("\\r") -> problemi += Problema(
                Gravita.ROTTO,
                "«enable-rcon» finisce con un a capo di Windows.",
                "Il valore diventa «true\\r», che per Minecraft non è «true»: RCON resta " +
                        "spento e il file sembra giusto. La riparazione toglie gli a capo " +
                        "di Windows da tutto il file.",
                automatico = true,
            )
            abilitato != "true" -> problemi += Problema(
                Gravita.ROTTO,
                "RCON è spento nel file (enable-rcon=$abilitato).",
                "La riparazione lo accende.",
                automatico = true,
            )
        }

        val porta = valoreDi("rcon.port")
        when {
            porta == null -> problemi += Problema(
                Gravita.SOSPETTO,
                "«rcon.port» non c'è nel file.",
                "Minecraft userebbe la 25575. La riparazione scrive $portaApp, che è " +
                        "quella che l'app sta usando.",
                automatico = true,
            )
            porta.contains("\\r") || porta.trim() != porta -> problemi += Problema(
                Gravita.ROTTO,
                "«rcon.port» ha caratteri invisibili in fondo.",
                "Minecraft non riesce a leggerla come numero e torna alla 25575. " +
                        "La riparazione la riscrive pulita.",
                automatico = true,
            )
            porta.toIntOrNull() != portaApp -> problemi += Problema(
                Gravita.ROTTO,
                "Il server ascolta sulla porta $porta, l'app parla alla $portaApp.",
                "La riparazione scrive $portaApp nel file. Se invece è giusta quella del " +
                        "server, cambia la porta nelle impostazioni dell'app.",
                automatico = true,
            )
        }

        // ------------------------------------------------------ password
        val password = pezzo(risposta, "PASSWORD").lines()
        val lunghezza = password.firstOrNull { it.startsWith("lunghezza=") }
            ?.removePrefix("lunghezza=")?.trim()?.toIntOrNull() ?: 0
        val improntaServer = password.lastOrNull { it.trim().length == 8 }?.trim()

        when {
            lunghezza == 0 -> problemi += Problema(
                Gravita.ROTTO,
                "La password RCON nel file è vuota.",
                "Con la password vuota Minecraft spegne RCON e non lo dice da nessuna " +
                        "parte. La riparazione ci scrive quella salvata nell'app.",
                automatico = true,
            )
            improntaServer != null && improntaServer != improntaApp -> problemi += Problema(
                Gravita.ROTTO,
                "La password nel file non è quella che usa l'app.",
                "La riparazione riscrive nel file quella dell'app. Se dopo torna a essere " +
                        "diversa, qualcos'altro la sta cambiando: un pannello di controllo, " +
                        "o un riavvio che rimette una sua configurazione.",
                automatico = true,
            )
        }

        // ------------------------------------------- il file giusto?
        val altri = pezzo(risposta, "ALTRIFILE").lines().filter { it.isNotBlank() }
        if (altri.isNotEmpty()) {
            problemi += Problema(
                Gravita.SOSPETTO,
                "Ci sono altri ${altri.size} server.properties sotto la cartella del server.",
                "Se il server ne legge un altro, quello che l'app modifica non conta e " +
                        "tutto riesce senza cambiare niente. Controlla la cartella nelle " +
                        "impostazioni.\n" + altri.joinToString("\n") { "· $it" },
                automatico = false,
            )
        }

        if (problemi.isEmpty()) {
            problemi += Problema(
                Gravita.BENE,
                "Nel file è tutto a posto.",
                "Il valore, la porta e la password sono quelli giusti e non ci sono righe " +
                        "doppie. Se RCON non risponde lo stesso, il server potrebbe non " +
                        "aver riletto il file: riavvialo.",
                automatico = false,
            )
        }
        return Diagnosi(problemi.sortedBy { it.gravita.ordinal })
    }

    /**
     * Riscrive le quattro righe da zero.
     *
     * Non si corregge riga per riga: si tolgono tutte e si rimettono una volta
     * sola. Correggendo la prima, una seconda copia più in basso resterebbe lì
     * a vincere, ed è proprio il caso che nessuno vede guardando il file.
     *
     * Gli a capo di Windows si tolgono da tutto il file e non solo da queste
     * righe: se ce n'è uno qui, ce ne sono ovunque, e il prossimo a rompersi
     * sarebbe un'altra impostazione.
     *
     * Il contenuto si riversa dentro il file esistente invece di sostituirlo:
     * così restano il proprietario e i permessi che aveva, che su un server dove
     * Minecraft gira con un utente suo è la differenza fra un file leggibile e
     * un server che non riparte.
     */
    fun comandoRiparazione(cfg: ServerConfig, porta: Int, password: String): String {
        require(Lgsm.PASSWORD_CHARSET.matches(password)) { "password RCON non valida" }
        require(porta in 1..65535) { "porta RCON non valida" }
        val f = Lgsm.path("${cfg.serverFiles.trimEnd('/')}/server.properties")
        return buildString {
            append("f=$f; ")
            append("[ -f \"\$f\" ] || { echo 'server.properties non trovato'; exit ${Lgsm.EXIT_NO_PROPERTIES}; }; ")
            append("cp -p \"\$f\" \"\$f.mcmonitor-\$(date +%Y%m%d-%H%M%S)\" && ")
            append("echo 'copia di sicurezza fatta'; ")
            append("t=\$(mktemp) || exit 1; ")
            append("tr -d '\\r' < \"\$f\" | ")
            append("grep -v -E '^(enable-rcon|rcon\\.port|rcon\\.password|broadcast-rcon-to-ops)[[:space:]]*=' ")
            append("> \"\$t\"; ")
            append("{ printf '%s\\n' 'enable-rcon=true' 'rcon.port=$porta' ")
            append("'broadcast-rcon-to-ops=false'; ")
            append("printf 'rcon.password=%s\\n' ${Lgsm.sq(password)}; } >> \"\$t\"; ")
            append("cat \"\$t\" > \"\$f\" && rm -f \"\$t\" && ")
            append("echo 'quattro righe riscritte'; ")
            append("grep -c '^rcon\\.password=' \"\$f\" | sed 's/^/righe password: /'; ")
            append("grep -E '^(enable-rcon|rcon\\.port|broadcast-rcon-to-ops)=' \"\$f\"")
        }
    }
}
