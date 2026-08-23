package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Un mod del progetto: il nome del file e la sua impronta, che lo identifica. */
data class BlueprintMod(
    val fileName: String,
    val sha1: String
)

/**
 * Tutto quello che serve per rifare questo server da un'altra parte.
 *
 * Non ci sono i mondi né i mod veri e propri: sono gigabyte, e il progetto deve
 * poter viaggiare in una chat. Dei mod resta l'impronta sha1, che su Modrinth
 * ritrova il file esatto — stessa versione, stesso pacchetto, non "uno simile".
 */
data class Blueprint(
    val serverName: String,
    val minecraft: String,
    val loader: String,
    val script: String,
    val lgsm: Map<String, String>,
    val properties: Map<String, String>,
    val mods: List<BlueprintMod>,
    val whitelist: List<String>,
    val ops: List<String>,
    val createdAt: Long = 0L
) {
    val isEmpty: Boolean get() = lgsm.isEmpty() && properties.isEmpty() && mods.isEmpty()

    /** Una riga per dire cosa c'è dentro senza aprirlo tutto. */
    fun summary(): String = buildList {
        if (lgsm.isNotEmpty()) add("${lgsm.size} impostazioni di LinuxGSM")
        if (properties.isNotEmpty()) add("${properties.size} di Minecraft")
        if (mods.isNotEmpty()) add("${mods.size} mod")
        if (whitelist.isNotEmpty()) add("${whitelist.size} in whitelist")
        if (ops.isNotEmpty()) add("${ops.size} operatori")
    }.joinToString(", ").ifBlank { "niente" }
}

/**
 * Legge e riscrive la configurazione di un server per clonarlo altrove.
 *
 * Il progetto passa di mano — finisce in una chat, in una mail, su una chiavetta
 * — quindi da qui escono solo cose che si possono far vedere: [isSecret] tiene
 * fuori password, token e webhook, che altrimenti se ne andrebbero in giro
 * insieme al resto senza che nessuno se ne accorga.
 */
object Blueprints {

    const val EXIT_NO_CONFIG = 96

    /** Le chiavi che non escono mai da qui, per quanto utili sarebbero al clone. */
    private val SECRET_PARTS = listOf(
        "password", "passwd", "token", "secret", "webhook", "apikey", "api_key",
        "key", "email", "chatid", "userid", "credential", "auth"
    )

    /**
     * Le chiavi che aprirebbero una porta a chi riceve il progetto.
     *
     * `rcon.password` è quella che conta: chi ce l'ha comanda il server come un
     * amministratore. Ma anche il webhook di Discord è un indirizzo che chiunque
     * lo conosca può usare per scrivere nella chat di qualcun altro.
     */
    fun isSecret(key: String): Boolean {
        val k = key.lowercase()
        // "level-name" e "level-seed" sono il cuore di un mondo da clonare:
        // non sono segreti e la parola "key" non li riguarda.
        if (k.startsWith("level-")) return false
        return SECRET_PARTS.any { k.contains(it) }
    }

    private fun propertiesPath(cfg: ServerConfig) = "${cfg.serverFiles.trimEnd('/')}/server.properties"

    // -------------------------------------------------------------- lettura

    /**
     * Un solo collegamento per prendere tutto: le due configurazioni, le impronte
     * dei mod e, se richiesti, i nomi di chi può entrare.
     *
     * I nomi degli altri sono un di più che si chiede apposta: sono persone che
     * non hanno deciso loro di finire nel file che stai per mandare a qualcuno.
     */
    fun read(cfg: ServerConfig, includePlayers: Boolean): String {
        val conf = Lgsm.path(GameVersion.configPath(cfg))
        val sf = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        val prop = Lgsm.path(propertiesPath(cfg))
        val players = if (includePlayers) {
            """
            echo '--- whitelist'
            [ -f "${'$'}sf/whitelist.json" ] && grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' "${'$'}sf/whitelist.json" 2>/dev/null | sed 's/.*"\([^"]*\)"${'$'}/\1/'
            echo '--- ops'
            [ -f "${'$'}sf/ops.json" ] && grep -o '"name"[[:space:]]*:[[:space:]]*"[^"]*"' "${'$'}sf/ops.json" 2>/dev/null | sed 's/.*"\([^"]*\)"${'$'}/\1/'
            """.trimIndent()
        } else ""

        return """
            conf=$conf
            sf=$sf
            prop=$prop
            [ -f "${'$'}conf" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }
            echo '--- lgsm'
            grep -E '^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*=' "${'$'}conf" 2>/dev/null
            echo '--- properties'
            [ -f "${'$'}prop" ] && grep -E '^[[:space:]]*[A-Za-z0-9._-]+=' "${'$'}prop" 2>/dev/null
            echo '--- mod'
            if [ -d "${'$'}sf/mods" ]; then
              for f in "${'$'}sf"/mods/*.jar; do
                [ -e "${'$'}f" ] || continue
                printf '%s\t%s\n' "${'$'}(sha1sum "${'$'}f" 2>/dev/null | cut -d' ' -f1)" "${'$'}(basename "${'$'}f")"
              done
            fi
            $players
            echo '--- fine'
        """.trimIndent()
    }

    fun parse(raw: String, cfg: ServerConfig, env: ServerEnvironment?): Blueprint {
        val text = Lgsm.clean(raw)
        val sezioni = mutableMapOf<String, MutableList<String>>()
        var corrente = ""
        text.lineSequence().forEach { linea ->
            val t = linea.trim()
            if (t.startsWith("--- ")) {
                corrente = t.removePrefix("--- ")
                sezioni.getOrPut(corrente) { mutableListOf() }
            } else if (corrente.isNotBlank() && t.isNotBlank()) {
                sezioni.getOrPut(corrente) { mutableListOf() }.add(t)
            }
        }

        fun coppie(sezione: String, spoglia: Boolean): Map<String, String> =
            sezioni[sezione].orEmpty().mapNotNull { riga ->
                if (riga.startsWith("#")) return@mapNotNull null
                val i = riga.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val chiave = riga.substring(0, i).trim()
                if (chiave.isBlank() || isSecret(chiave)) return@mapNotNull null
                var valore = riga.substring(i + 1).trim()
                if (spoglia) valore = valore.removeSurrounding("\"").removeSurrounding("'")
                chiave to valore
            }.toMap()

        val mods = sezioni["mod"].orEmpty().mapNotNull { riga ->
            val parti = riga.split('\t')
            if (parti.size < 2) return@mapNotNull null
            val sha = parti[0].trim().lowercase()
            val nome = parti[1].trim()
            if (!sha.matches(Regex("^[a-f0-9]{40}$")) || !Mods.safeFileName(nome)) return@mapNotNull null
            BlueprintMod(nome, sha)
        }

        val lgsm = coppie("lgsm", spoglia = true)
        return Blueprint(
            serverName = cfg.displayName,
            minecraft = env?.minecraftVersion ?: lgsm["mcversion"].orEmpty(),
            loader = env?.loader ?: "vanilla",
            script = cfg.script,
            lgsm = lgsm,
            properties = coppie("properties", spoglia = false),
            mods = mods,
            whitelist = sezioni["whitelist"].orEmpty().distinct(),
            ops = sezioni["ops"].orEmpty().distinct()
        )
    }

    // -------------------------------------------------------------- scrittura

    private val PROPERTY_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,60}$")

    fun isValidPropertyKey(key: String) = PROPERTY_KEY.matches(key)

    /**
     * Il valore di server.properties non sta fra virgolette e la riga finisce
     * dove finisce: basta togliere gli a capo perché resti una riga sola.
     */
    fun cleanProperty(value: String): String =
        value.replace(Regex("""[\r\n]+"""), " ").trim().take(300)

    /**
     * Scrive una riga di server.properties. Stessa strada dei parametri di
     * LinuxGSM: il valore arriva ad awk dall'ambiente, perché `-v` interpreterebbe
     * a sua volta le sequenze di escape.
     */
    fun setProperty(cfg: ServerConfig, key: String, value: String): String {
        require(isValidPropertyKey(key)) { "chiave non valida: $key" }
        val f = Lgsm.path(propertiesPath(cfg))
        val riga = Lgsm.sq("$key=${cleanProperty(value)}")
        // Nel modello di awk il punto vale per qualsiasi carattere: "rcon.port"
        // pescherebbe anche "rcon-port". Qui i punti si scrivono come punti.
        val modello = "^[[:space:]]*" + key.replace(".", "\\.") + "="
        return "f=$f; [ -f \"\$f\" ] || { echo 'PROPERTIES NON TROVATO'; exit $EXIT_NO_CONFIG; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\" || { echo 'COPIA DI SICUREZZA NON RIUSCITA'; exit ${Lgsm.EXIT_NO_BACKUP}; }; " +
                "MCM_RIGA=$riga awk 'BEGIN{fatto=0; riga=ENVIRON[\"MCM_RIGA\"]} " +
                "/$modello/{ if(!fatto){print riga; fatto=1} next } " +
                "{print} END{ if(!fatto) print riga }' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && echo 'SCRITTO'"
    }

    fun written(output: String) = Lgsm.clean(output).contains("SCRITTO")

    /**
     * Le impostazioni che ha senso portarsi dietro cambiando computer.
     *
     * Porte e indirizzi restano fuori: sul computer di chi riceve sono diversi, e
     * riscriverli vorrebbe dire spegnergli il server o accavallarlo a un altro.
     */
    private val NON_TRASFERIBILI = setOf(
        "port", "queryport", "rconport", "ip", "servername", "serverdescription",
        "server-ip", "server-port", "query.port", "rcon.port", "enable-rcon"
    )

    fun transferable(values: Map<String, String>): Map<String, String> =
        values.filterKeys { it !in NON_TRASFERIBILI && !isSecret(it) }
}
