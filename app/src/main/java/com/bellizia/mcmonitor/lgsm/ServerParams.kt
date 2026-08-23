package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Una riga di configurazione di LinuxGSM: chiave, valore e se è commentata. */
data class ServerParam(
    val key: String,
    val value: String,
    val commented: Boolean
) {
    val description: String get() = ServerParams.describe(key)
}

/**
 * I parametri del server LinuxGSM, tutti modificabili.
 *
 * Il file `lgsm/config-lgsm/<script>/<script>.cfg` è un elenco di assegnazioni
 * che sovrascrivono i valori di fabbrica: quanto RAM dare a Java, quale versione
 * scaricare, quanti backup tenere. Finora l'app ne toccava una sola,
 * `mcversion`, e per il resto bisognava collegarsi a mano.
 *
 * Le modifiche passano da awk e non da sed: il valore può contenere barre,
 * e-commerciali e virgolette, che in una sostituzione sed avrebbero significati
 * propri e rovinerebbero il file.
 */
object ServerParams {

    const val EXIT_NO_CONFIG = 96

    private val KEY = Regex("^[A-Za-z_][A-Za-z0-9_]{0,40}$")

    fun isValidKey(key: String) = KEY.matches(key)

    /**
     * Il valore finisce fra virgolette doppie in un file che LinuxGSM legge con
     * `source`, quindi passa dalle mani della shell.
     *
     * Vanno protetti i caratteri che spezzerebbero il file: una virgoletta chiude
     * l'assegnazione in anticipo e quel che resta del valore diventa una riga di
     * comando, una barra si mangia il carattere dopo, un apice inverso spaiato
     * lascia il file troncato a metà e `source` fallisce.
     *
     * Il dollaro invece si lascia stare: le configurazioni di LinuxGSM sono piene
     * di ${'$'}{serverfiles} e ${'$'}HOME, e proteggerlo li farebbe arrivare al
     * server scritti alla lettera. Non è una porta che si apre: chi arriva a
     * questa schermata ha già le chiavi SSH di quel computer.
     */
    fun quoteValue(value: String): String = value.trim()
        .replace(Regex("""[\r\n]+"""), " ")
        .take(300)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")

    fun readAll(cfg: ServerConfig): String {
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; cat $f"
    }

    // ------------------------------------------------- la catena dei cinque file

    /**
     * I cinque file che LinuxGSM legge in fila, dal primo all'ultimo.
     *
     * L'ordine è quello di caricamento e conta: l'ultimo che assegna una chiave
     * è quello che comanda. `_default.cfg` porta i valori di fabbrica e non si
     * tocca — LinuxGSM lo riscrive a ogni suo aggiornamento — mentre il file
     * dell'istanza è quello su cui scriviamo noi.
     */
    enum class Da(val file: String, val etichetta: String, val spiegazione: String) {
        FABBRICA("_default", "di fabbrica", "Valore di fabbrica di LinuxGSM. Non si modifica lì: si sovrascrive qui."),
        COMUNE("common", "comune", "Vale per tutti i server di questo computer."),
        SEGRETI_COMUNI("secrets-common", "segreti comuni", "File dei segreti comune a tutti i server."),
        ISTANZA("istanza", "scritto qui", "Scritto nel file di questo server: è quello che l'app modifica."),
        SEGRETI_ISTANZA("secrets-istanza", "segreti di questo server", "File dei segreti di questo server.")
    }

    /**
     * Un parametro come lo vede LinuxGSM davvero: il valore in vigore, da dove
     * viene, e cosa c'è scritto (se c'è) nel file su cui l'app scrive.
     */
    data class ParamEffettivo(
        val key: String,
        val value: String,
        val da: Da,
        /** Il valore nel file dell'istanza, se quella riga esiste. */
        val nostro: String?,
        /**
         * Un file caricato DOPO quello dell'istanza assegna la stessa chiave.
         *
         * È il caso che farebbe dire una bugia alla schermata: si scrive, si
         * mostra il valore nuovo, e il server continua a usare quello vecchio.
         */
        val coperto: Boolean
    ) {
        val description: String get() = describe(key)
        val modificabile: Boolean get() = !coperto
    }

    private fun configDir(cfg: ServerConfig) =
        "${cfg.lgsmDir.trimEnd('/')}/lgsm/config-lgsm/${cfg.script}"

    /**
     * Legge tutti e cinque i file in un colpo solo, marcando da quale viene ogni
     * riga. I file che non esistono si saltano: su un'installazione normale ce ne
     * sono due o tre.
     */
    fun readChain(cfg: ServerConfig): String {
        val d = Lgsm.path(configDir(cfg))
        val istanza = Lgsm.sq(cfg.script)
        return """
            d=$d
            [ -d "${'$'}d" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }
            s=$istanza
            for n in _default common secrets-common "${'$'}s" "secrets-${'$'}s"; do
              f="${'$'}d/${'$'}n.cfg"
              [ -f "${'$'}f" ] || continue
              case "${'$'}n" in
                _default) marca=_default ;;
                common) marca=common ;;
                secrets-common) marca=secrets-common ;;
                "secrets-${'$'}s") marca=secrets-istanza ;;
                *) marca=istanza ;;
              esac
              echo "--- ${'$'}marca"
              cat "${'$'}f"
            done
            echo '--- fine'
        """.trimIndent()
    }

    /**
     * Mette insieme la catena come farebbe LinuxGSM: si scorre dal primo file
     * all'ultimo e ogni assegnazione sovrascrive la precedente. Alla fine si sa
     * anche chi ha vinto, che è l'informazione che mancava del tutto.
     */
    fun parseChain(raw: String): List<ParamEffettivo> {
        val text = Lgsm.clean(raw)
        val sezioni = linkedMapOf<Da, MutableList<String>>()
        var corrente: Da? = null
        text.lines().forEach { linea ->
            val t = linea.trim()
            if (t.startsWith("--- ")) {
                val nome = t.removePrefix("--- ")
                corrente = Da.entries.firstOrNull { it.file == nome }
                corrente?.let { sezioni.getOrPut(it) { mutableListOf() } }
            } else {
                corrente?.let { sezioni.getOrPut(it) { mutableListOf() }.add(linea) }
            }
        }

        val vincitore = linkedMapOf<String, Pair<String, Da>>()
        val nostro = mutableMapOf<String, String>()
        // In ordine di caricamento: l'ultimo che assegna comanda.
        Da.entries.forEach { da ->
            val righe = sezioni[da] ?: return@forEach
            parse(righe.joinToString("\n")).filter { !it.commented }.forEach { p ->
                vincitore[p.key] = p.value to da
                if (da == Da.ISTANZA) nostro[p.key] = p.value
            }
        }

        val dopoIstanza = Da.entries.filter { it.ordinal > Da.ISTANZA.ordinal }
        return vincitore.map { (chiave, coppia) ->
            val (valore, da) = coppia
            ParamEffettivo(
                key = chiave,
                value = valore,
                da = da,
                nostro = nostro[chiave],
                coperto = da in dopoIstanza
            )
        }.sortedBy { it.key }
    }

    /**
     * Il valore di una riga, senza le virgolette e senza il commento in coda.
     *
     * LinuxGSM raccomanda di copiare le singole righe da `_default.cfg`, e quelle
     * righe sono scritte così: `javaram="1024" # -Xmx$1024M`. Togliendo solo le
     * virgolette agli estremi non se ne toglie nessuna — la riga finisce con una
     * emme, non con una virgoletta — e il valore diventava tutta la riga,
     * commento compreso. Bastava riaprirla e salvarla senza toccare niente per
     * scrivere nel file una riga che il server non sa più eseguire.
     */
    fun readValue(raw: String): String {
        val t = raw.trim()
        if (t.length > 1 && (t.startsWith("\"") || t.startsWith("'"))) {
            val virgoletta = t[0]
            val fine = t.indexOf(virgoletta, 1)
            if (fine > 0) return t.substring(1, fine)
        }
        // Senza virgolette il commento comincia a un cancelletto preceduto da spazio.
        val cancelletto = Regex("""\s#""").find(t)?.range?.first ?: return t
        return t.substring(0, cancelletto).trim()
    }

    fun parse(raw: String): List<ServerParam> {
        val riga = Regex("""^(#?)\s*([A-Za-z_][A-Za-z0-9_]{0,40})=(.*)$""")
        return Lgsm.clean(raw).lines().mapNotNull { linea ->
            val m = riga.find(linea.trim()) ?: return@mapNotNull null
            ServerParam(
                key = m.groupValues[2],
                value = readValue(m.groupValues[3]),
                commented = m.groupValues[1] == "#"
            )
        }
            // Se la stessa chiave compare due volte, comanda l'ultima: il file
            // viene eseguito dall'alto in basso e l'ultima assegnazione vince.
            // Tenendo la prima si mostrava un valore che il server non usa.
            .reversed().distinctBy { it.key to it.commented }.reversed()
    }

    /**
     * Scrive un parametro: se c'è lo sostituisce dov'è, se manca lo aggiunge in
     * fondo, e in ogni caso tiene una copia datata del file. La riga commentata
     * con la stessa chiave viene lasciata dov'è: è la documentazione di LinuxGSM
     * e toglierla renderebbe il file più povero.
     */
    fun set(cfg: ServerConfig, key: String, value: String): String {
        require(isValidKey(key)) { "chiave non valida: $key" }
        // Una riga vuota non è "il valore di fabbrica": è una riga che il server
        // esegue davvero. `javaram=""` diventa `java -XmxM -jar` e il server non
        // parte più. Per tornare al valore di fabbrica si commenta la riga.
        require(value.isNotBlank()) { "valore vuoto: per tornare al valore di fabbrica usa Togli" }
        val f = Lgsm.path(GameVersion.configPath(cfg))
        val riga = Lgsm.sq("$key=\"${quoteValue(value)}\"")
        // La riga arriva ad awk dall'ambiente e non da `-v`: awk sulle assegnazioni
        // da riga di comando interpreta a sua volta le sequenze di escape, e
        // disferebbe la protezione appena messa da quoteValue.
        return "f=$f; [ -f \"\$f\" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "MCM_RIGA=$riga awk 'BEGIN{fatto=0; riga=ENVIRON[\"MCM_RIGA\"]} " +
                "/^[[:space:]]*$key=/{ if(!fatto){print riga; fatto=1} next } " +
                "{print} END{ if(!fatto) print riga }' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && " +
                "echo 'SCRITTO'; grep -n \"^[[:space:]]*$key=\" \"\$f\""
    }

    /**
     * Togliere un parametro significa commentarlo: LinuxGSM torna al suo valore
     * di fabbrica e la riga resta lì a ricordare cosa c'era.
     */
    fun disable(cfg: ServerConfig, key: String): String {
        require(isValidKey(key)) { "chiave non valida: $key" }
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "f=$f; [ -f \"\$f\" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "awk '/^[[:space:]]*$key=/{ print \"#\" \$0; next } {print}' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && echo 'DISATTIVATO'"
    }

    fun written(output: String) = Lgsm.clean(output).contains("SCRITTO") ||
            Lgsm.clean(output).contains("DISATTIVATO")

    // ------------------------------------------------------------- catalogo

    /** Quando ha effetto quello che si è appena scritto. */
    enum class Quando { AVVIO, AGGIORNAMENTO, SUBITO }

    data class ParamInfo(val key: String, val what: String, val quando: Quando)

    /**
     * I parametri che si toccano davvero, con quello che fanno detto in italiano.
     *
     * Ci sono solo chiavi che LinuxGSM legge per davvero, controllate una per una
     * sul suo `_default.cfg`. Prima ce n'erano sei che non esistono — `port`,
     * `queryport`, `ip`, `servername`, `javaparms`, `mcbranch` — e per un server
     * Minecraft non esistono per un motivo: porta, indirizzo e nome LinuxGSM li
     * legge da `server.properties` a ogni avvio. Scriverle qui non faceva niente,
     * e la schermata offriva poi di riavviare il server per applicarle: giocatori
     * buttati fuori per un effetto nullo.
     *
     * L'elenco completo sta nella documentazione di LinuxGSM, richiamata
     * dall'aiuto della pagina.
     */
    val catalogue: List<ParamInfo> = listOf(
        ParamInfo(
            "javaram",
            "Memoria data a Java, in megabyte: di fabbrica 1024. È la manopola che conta su un computer piccolo: troppo poca fa singhiozzare il server, troppa lo fa chiudere di forza dal sistema.",
            Quando.AVVIO
        ),
        ParamInfo(
            "startparameters",
            "Riga con cui il server viene avviato: la tocca l'app quando installa Fabric.",
            Quando.AVVIO
        ),
        ParamInfo(
            "mcversion",
            "Versione di Minecraft da scaricare, per esempio 1.20.4. Con \"latest\" prende l'ultima.",
            Quando.AGGIORNAMENTO
        ),
        ParamInfo(
            "updateonstart",
            "Con \"on\" cerca aggiornamenti a ogni avvio. Comodo, ma allunga l'accensione.",
            Quando.SUBITO
        ),
        ParamInfo("maxbackups", "Quante copie di backup tenere prima di cancellare le più vecchie: di fabbrica 4.", Quando.SUBITO),
        ParamInfo("maxbackupdays", "Da quanti giorni in là i backup vengono buttati: di fabbrica 30.", Quando.SUBITO),
        ParamInfo("stoponbackup", "Di fabbrica \"on\": il server si ferma durante il backup. Copia più sicura, giocatori fuori per qualche minuto.", Quando.SUBITO),
        ParamInfo("logdays", "Per quanti giorni tenere i log di LinuxGSM prima di cancellarli: di fabbrica 7.", Quando.SUBITO),
        ParamInfo("postalert", "Con \"on\" manda un avviso quando il server cade o riparte.", Quando.SUBITO),
        ParamInfo("discordalert", "Avvisi su Discord: serve anche discordwebhook.", Quando.SUBITO),
        ParamInfo("discordwebhook", "Indirizzo del webhook di Discord dove mandare gli avvisi.", Quando.SUBITO),
        ParamInfo("telegramalert", "Avvisi su Telegram: servono anche telegramtoken e telegramchatid.", Quando.SUBITO),
        ParamInfo("telegramtoken", "Token del bot Telegram che manda gli avvisi.", Quando.SUBITO),
        ParamInfo("telegramchatid", "Identificativo della chat Telegram a cui mandare gli avvisi.", Quando.SUBITO),
        ParamInfo("emailalert", "Avvisi per posta: serve anche email.", Quando.SUBITO),
        ParamInfo("email", "Indirizzo a cui mandare gli avvisi.", Quando.SUBITO)
    )

    private val perChiave = catalogue.associateBy { it.key }

    fun describe(key: String): String = perChiave[key]?.what ?: ""

    fun quando(key: String): Quando? = perChiave[key]?.quando

    /** Come dire, accanto alla riga, quando avrà effetto. */
    fun effectLabel(key: String): String = when (quando(key)) {
        Quando.AVVIO -> "vale dal prossimo avvio del server"
        Quando.AGGIORNAMENTO -> "per applicarla serve un aggiornamento, dalla scheda Stato"
        Quando.SUBITO -> "non serve riavviare: LinuxGSM la rilegge da sola"
        null -> ""
    }

    /** Solo questi chiedono davvero un riavvio: gli altri non buttano fuori nessuno. */
    fun needsRestart(keys: Collection<String>): Boolean =
        keys.any { quando(it) == Quando.AVVIO }

    /** Quelli del catalogo che nel file non ci sono ancora, pronti da aggiungere. */
    fun addable(existing: List<ServerParam>): List<ParamInfo> {
        val presenti = existing.filter { !it.commented }.map { it.key }.toSet()
        return catalogue.filterNot { it.key in presenti }
    }
}
