package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Come si presenta una impostazione a chi la guarda. */
enum class Tipo { INTERRUTTORE, SCELTA, NUMERO, TESTO }

/** Quando ha effetto. */
enum class Applica {
    /** Si scrive nel file e si manda anche il comando: cambia subito. */
    SUBITO,

    /** Il file basta, ma il server la legge quando riparte. */
    AL_RIAVVIO,

    /** Vale solo per un mondo che non è ancora stato creato. */
    SOLO_MONDO_NUOVO
}

/**
 * Una impostazione del gioco, con dentro tutto quello che serve per mostrarla,
 * controllarla e applicarla.
 */
data class Impostazione(
    val key: String,
    val titolo: String,
    val spiegazione: String,
    val tipo: Tipo,
    val diFabbrica: String,
    /** valore scritto nel file -> come si chiama in italiano */
    val scelte: List<Pair<String, String>> = emptyList(),
    val min: Int = 0,
    val max: Int = 0,
    val unita: String = "",
    val maxCaratteri: Int = 0,
    val applica: Applica = Applica.AL_RIAVVIO,
    /** Il comando da console che la applica al mondo che sta girando. */
    val comando: ((String) -> String)? = null,
    /** Da questa versione in poi non è più una riga del file ma una regola di gioco. */
    val gameruleDa: String? = null,
    val gamerule: String? = null,
    /** Una riga in più quando il valore scelto merita un avvertimento. */
    val attenzione: ((String) -> String?)? = null
) {
    val booleana: Boolean get() = tipo == Tipo.INTERRUTTORE

    fun etichetta(valore: String): String = when (tipo) {
        Tipo.INTERRUTTORE -> if (valore == "true") "sì" else "no"
        Tipo.SCELTA -> scelte.firstOrNull { it.first == valore }?.second ?: valore
        Tipo.NUMERO -> if (unita.isBlank()) valore else "$valore $unita"
        Tipo.TESTO -> valore.ifBlank { "(vuoto)" }
    }
}

/**
 * Le impostazioni del gioco: quelle che si cambiano davvero.
 *
 * Vivono in `serverfiles/server.properties`, che è un file diverso da quello di
 * LinuxGSM e con una vita diversa: quello di LinuxGSM si tocca il primo giorno e
 * poi sta fermo per anni, questo si tocca ogni volta che cambia qualcosa fra chi
 * gioca. Difficoltà, messaggio di benvenuto, quanti entrano, quanto lontano si
 * vede: non c'era modo di cambiarle dall'app, e sono esattamente quelle per cui
 * si apre una schermata che si chiama "impostazioni del server".
 *
 * I valori non sono testo libero: una difficoltà può essere solo una di quattro
 * parole, la distanza di visuale un numero fra 3 e 32. Scriverli come stringhe
 * qualsiasi vuol dire lasciar salvare `difficulty=medio`, che il server ignora
 * senza dire niente.
 */
object GameSettings {

    const val EXIT_NO_PROPERTIES = 91

    /**
     * Dallo snapshot 25w35a (Minecraft 1.21.9) quattro impostazioni sono uscite da
     * server.properties e sono diventate regole di gioco. Scriverle nel file su
     * quelle versioni non fa più niente.
     */
    const val VERSIONE_GAMERULE = "1.21.9"

    private val ACCESO_SPENTO = listOf("true" to "sì", "false" to "no")

    val gruppi: List<Pair<String, List<Impostazione>>> = listOf(
        "Come si gioca" to listOf(
            Impostazione(
                key = "difficulty",
                titolo = "Difficoltà",
                spiegazione = "Quanto fanno male i mostri. In pacifica non compaiono affatto e la fame non scende.",
                tipo = Tipo.SCELTA,
                diFabbrica = "easy",
                scelte = listOf(
                    "peaceful" to "pacifica",
                    "easy" to "facile",
                    "normal" to "normale",
                    "hard" to "difficile"
                ),
                applica = Applica.SUBITO,
                comando = { "difficulty $it" }
            ),
            Impostazione(
                key = "pvp",
                titolo = "Ci si può fare male fra giocatori",
                spiegazione = "Spegnendolo, i colpi fra giocatori non tolgono cuori. Utile quando giocano bambini insieme.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "true",
                scelte = ACCESO_SPENTO,
                gameruleDa = VERSIONE_GAMERULE,
                gamerule = "pvp"
            ),
            Impostazione(
                key = "gamemode",
                titolo = "Modalità di chi entra",
                spiegazione = "Come comincia chi si collega la prima volta.",
                tipo = Tipo.SCELTA,
                diFabbrica = "survival",
                scelte = listOf(
                    "survival" to "sopravvivenza",
                    "creative" to "creativa",
                    "adventure" to "avventura",
                    "spectator" to "spettatore"
                )
            ),
            Impostazione(
                key = "force-gamemode",
                titolo = "Rimetti tutti in quella modalità a ogni ingresso",
                spiegazione = "Con sì, chi rientra torna nella modalità qui sopra anche se l'avevi cambiata a lui.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "false",
                scelte = ACCESO_SPENTO
            ),
            Impostazione(
                key = "allow-flight",
                titolo = "Si può volare",
                spiegazione = "Serve con le elytra e con i mod che fanno volare: se è no, chi resta in aria più di cinque secondi viene buttato fuori.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "false",
                scelte = ACCESO_SPENTO
            ),
            Impostazione(
                key = "player-idle-timeout",
                titolo = "Butta fuori chi sta fermo",
                spiegazione = "Dopo quanti minuti senza toccare niente un giocatore viene disconnesso. Con 0 non succede mai.",
                tipo = Tipo.NUMERO,
                diFabbrica = "0",
                min = 0,
                max = 120,
                unita = "minuti"
            )
        ),
        "Chi può entrare" to listOf(
            Impostazione(
                key = "white-list",
                titolo = "Solo chi è in whitelist",
                spiegazione = "Acceso, entra solo chi hai messo in elenco. È la difesa che conta su un server aperto su internet.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "false",
                scelte = ACCESO_SPENTO,
                applica = Applica.SUBITO,
                comando = { if (it == "true") "whitelist on" else "whitelist off" },
                attenzione = {
                    if (it == "false") "Da spenta entra chiunque conosca l'indirizzo." else null
                }
            ),
            Impostazione(
                key = "enforce-whitelist",
                titolo = "Butta fuori subito chi togli dalla whitelist",
                spiegazione = "Senza, chi è già dentro resta dentro finché non esce da solo.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "false",
                scelte = ACCESO_SPENTO
            ),
            Impostazione(
                key = "max-players",
                titolo = "Quanti giocatori insieme",
                spiegazione = "Il numero massimo collegati nello stesso momento. Ogni giocatore in più costa memoria.",
                tipo = Tipo.NUMERO,
                diFabbrica = "20",
                min = 1,
                max = 200,
                unita = "giocatori"
            ),
            Impostazione(
                key = "online-mode",
                titolo = "Controlla gli account Minecraft",
                spiegazione = "Verifica che chi entra sia davvero chi dice di essere.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "true",
                scelte = ACCESO_SPENTO,
                attenzione = {
                    if (it == "false") {
                        "Spento, chiunque può entrare con il nome di un altro, il tuo compreso. " +
                                "Si spegne solo su una rete di casa isolata."
                    } else null
                }
            ),
            Impostazione(
                key = "spawn-protection",
                titolo = "Zona protetta intorno al punto di nascita",
                spiegazione = "Quanti blocchi intorno allo spawn nessuno può toccare, a parte gli operatori. Con 0 si costruisce ovunque.",
                tipo = Tipo.NUMERO,
                diFabbrica = "16",
                min = 0,
                max = 64,
                unita = "blocchi"
            )
        ),
        "Come si presenta" to listOf(
            Impostazione(
                key = "motd",
                titolo = "Messaggio nella lista dei server",
                spiegazione = "La riga che si legge sotto il nome del server, nell'elenco dentro il gioco.",
                tipo = Tipo.TESTO,
                diFabbrica = "A Minecraft Server",
                maxCaratteri = 59
            ),
            Impostazione(
                key = "hide-online-players",
                titolo = "Nascondi chi sta giocando",
                spiegazione = "Da fuori si vede quanti sono ma non chi sono.",
                tipo = Tipo.INTERRUTTORE,
                diFabbrica = "false",
                scelte = ACCESO_SPENTO
            )
        ),
        "Se il server arranca" to listOf(
            Impostazione(
                key = "view-distance",
                titolo = "Quanto lontano si vede",
                spiegazione = "Il primo da abbassare quando il server singhiozza: da 10 a 7 si guadagna molto e si perde poco.",
                tipo = Tipo.NUMERO,
                diFabbrica = "10",
                min = 3,
                max = 32,
                unita = "chunk"
            ),
            Impostazione(
                key = "simulation-distance",
                titolo = "Quanto lontano il mondo continua a vivere",
                spiegazione = "Entro questa distanza crescono le piante e si muovono i mostri. È quello che pesa di più sul processore.",
                tipo = Tipo.NUMERO,
                diFabbrica = "10",
                min = 3,
                max = 32,
                unita = "chunk"
            ),
            Impostazione(
                key = "pause-when-empty-seconds",
                titolo = "Metti in pausa quando non c'è nessuno",
                spiegazione = "Dopo quanti secondi da solo il server smette di consumare. Con 0 resta sempre sveglio.",
                tipo = Tipo.NUMERO,
                diFabbrica = "60",
                min = 0,
                max = 3600,
                unita = "secondi"
            )
        )
    )

    val tutte: List<Impostazione> = gruppi.flatMap { it.second }

    private val perChiave = tutte.associateBy { it.key }

    fun byKey(key: String): Impostazione? = perChiave[key]

    // ------------------------------------------------------------- lettura

    private fun path(cfg: ServerConfig) = "${cfg.serverFiles.trimEnd('/')}/server.properties"

    fun readAll(cfg: ServerConfig): String {
        val f = Lgsm.path(path(cfg))
        return "[ -f $f ] || { echo 'PROPERTIES NON TROVATO'; exit $EXIT_NO_PROPERTIES; }; cat $f"
    }

    /**
     * Le righe del file, chiave per valore.
     *
     * Le righe commentate restano fuori: in server.properties un `#` davanti vuol
     * dire che quella riga non vale, e mostrarla come impostazione attiva sarebbe
     * la stessa bugia che si sta correggendo altrove.
     */
    fun parse(raw: String): Map<String, String> =
        Lgsm.clean(raw).lines().mapNotNull { linea ->
            val t = linea.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return@mapNotNull null
            val i = t.indexOf('=')
            if (i <= 0) return@mapNotNull null
            t.substring(0, i).trim() to t.substring(i + 1).trim()
        }.toMap()

    // ------------------------------------------------------------ scrittura

    private val KEY = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,60}$")

    fun isValidKey(key: String) = KEY.matches(key)

    /** Una riga sola: gli a capo spezzerebbero il file in due impostazioni. */
    fun cleanValue(value: String): String =
        value.replace(Regex("""[\r\n]+"""), " ").trim().take(300)

    /**
     * Dice se un valore va bene per questa impostazione, o perché no.
     *
     * Serve perché il server non lo dice: `difficulty=medio` non fa scattare
     * nessun errore, il server usa il valore di fabbrica e chi ha scritto "medio"
     * resta convinto di aver cambiato qualcosa.
     */
    fun validate(imp: Impostazione, valore: String): String? {
        val v = valore.trim()
        return when (imp.tipo) {
            Tipo.INTERRUTTORE ->
                if (v == "true" || v == "false") null else "può essere solo sì o no"
            Tipo.SCELTA ->
                if (imp.scelte.any { it.first == v }) null
                else "valore non ammesso: " + imp.scelte.joinToString(", ") { it.second }
            Tipo.NUMERO -> {
                val n = v.toIntOrNull()
                when {
                    n == null -> "ci vuole un numero"
                    n < imp.min || n > imp.max -> "va da ${imp.min} a ${imp.max}"
                    else -> null
                }
            }
            Tipo.TESTO -> when {
                v.contains('\n') || v.contains('\r') -> "deve stare su una riga sola"
                imp.maxCaratteri > 0 && v.length > imp.maxCaratteri ->
                    "al massimo ${imp.maxCaratteri} caratteri"
                else -> null
            }
        }
    }

    /**
     * Scrive più impostazioni in un colpo solo.
     *
     * Una connessione, una copia di sicurezza, un messaggio. Scrivendone una per
     * volta si aprirebbero dieci connessioni e resterebbero dieci copie del file,
     * e a metà strada il server avrebbe una configurazione che nessuno ha scelto.
     *
     * Le righe arrivano ad awk dall'ambiente, come per i parametri di LinuxGSM: le
     * assegnazioni da riga di comando reinterpretano le sequenze di escape. Il
     * confronto della chiave è fra stringhe e non con un modello, così il punto di
     * `rcon.port` è un punto e non un carattere qualsiasi.
     */
    fun setProperties(cfg: ServerConfig, values: Map<String, String>): String {
        require(values.isNotEmpty()) { "niente da scrivere" }
        values.keys.forEach { require(isValidKey(it)) { "chiave non valida: $it" } }

        val f = Lgsm.path(path(cfg))
        val ambiente = values.entries.mapIndexed { i, (chiave, valore) ->
            "MCM_${i + 1}=${Lgsm.sq("$chiave=${cleanValue(valore)}")}"
        }.joinToString(" ")

        val programma = buildString {
            append("BEGIN{ n=0; while ((\"MCM_\" (n+1)) in ENVIRON) { n++; ")
            append("r[n]=ENVIRON[\"MCM_\" n]; k[n]=substr(r[n], 1, index(r[n], \"=\") - 1); f[n]=0 } } ")
            append("{ p=index(\$0, \"=\"); if (p > 1) { c=substr(\$0, 1, p-1); ")
            append("gsub(/^[ \\t]+|[ \\t]+\$/, \"\", c); ")
            append("for (i=1; i<=n; i++) if (!f[i] && c == k[i]) { print r[i]; f[i]=1; next } } ")
            append("print } ")
            append("END{ for (i=1; i<=n; i++) if (!f[i]) print r[i] }")
        }

        return "f=$f; [ -f \"\$f\" ] || { echo 'PROPERTIES NON TROVATO'; exit $EXIT_NO_PROPERTIES; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\" || { echo 'COPIA DI SICUREZZA NON RIUSCITA'; exit ${Lgsm.EXIT_NO_BACKUP}; }; " +
                "$ambiente awk '$programma' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && echo 'SCRITTO ${values.size}'"
    }

    fun written(output: String) = Lgsm.clean(output).contains("SCRITTO")

    // -------------------------------------------------------------- versione

    /**
     * Confronto fra versioni di Minecraft, numero per numero.
     *
     * "1.21.10" viene dopo "1.21.9", che confrontate come testo darebbero il
     * contrario. Una versione che non si sa leggere ("latest", uno snapshot) non
     * risponde né sì né no: risponde null, e chi chiama decide cosa farne.
     */
    fun atLeast(versione: String?, soglia: String): Boolean? {
        val a = numeri(versione) ?: return null
        val b = numeri(soglia) ?: return null
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return true
    }

    private fun numeri(v: String?): List<Int>? {
        if (v.isNullOrBlank()) return null
        if (!Regex("""^[0-9]+(\.[0-9]+)*$""").matches(v.trim())) return null
        return v.trim().split('.').map { it.toInt() }
    }

    /**
     * Se su questa versione l'impostazione va mandata come regola di gioco invece
     * che scritta nel file.
     *
     * Nel dubbio — versione sconosciuta — si risponde di no: il file si scrive
     * comunque, e se serve anche il comando lo decide chi chiama dicendolo.
     */
    fun isGamerule(imp: Impostazione, versione: String?): Boolean {
        val da = imp.gameruleDa ?: return false
        return atLeast(versione, da) == true
    }

    /** Il comando da mandare alla console per applicarla al mondo che sta girando. */
    fun commandFor(imp: Impostazione, valore: String, versione: String?): String? {
        if (isGamerule(imp, versione) && imp.gamerule != null) {
            return "gamerule ${imp.gamerule} $valore"
        }
        return imp.comando?.invoke(valore)
    }
}
