package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Un server LinuxGSM trovato sul computer, con la sua configurazione. */
data class DiscoveredServer(
    val directory: String,
    val script: String,
    val minecraftVersion: String?,
    val branch: String?,
    val hasServerFiles: Boolean,
    val running: Boolean,
    val gamePort: String?,
    /** Porta RCON e se il server la tiene accesa: due server non possono condividerla. */
    val rconPort: String? = null,
    val rconEnabled: Boolean = false,
    val motd: String?,
    val sizeMb: Long?,
    /** Nomi dei file nella cartella mods, senza estensione. */
    val mods: List<String> = emptyList(),
    /** Fabric, Forge o niente se il server è vanilla. */
    val loader: String? = null,
    /** Versione degli script LinuxGSM, letta dallo script dell'istanza. */
    val lgsmVersion: String? = null
) {
    /** Nome leggibile: la cartella dice più dello script, che spesso è "mcserver". */
    val displayName: String
        get() = directory.trimEnd('/').substringAfterLast('/').ifBlank { script }

    val modded: Boolean
        get() = mods.isNotEmpty() || loader != null

    /**
     * Acceso o spento, tenendo conto di quello che si e' visto davvero.
     *
     * `running` viene dalla sessione tmux di LinuxGSM: un server avviato a mano,
     * fuori da LinuxGSM, risulta fermo anche mentre gira. Ma se RCON ha appena
     * risposto, il server e' acceso e non c'e' niente da discutere -- e la riga
     * non deve dire «fermo» sopra a un cartellino verde che dice «risponde».
     */
    fun acceso(rconHaRisposto: Boolean): Boolean = running || rconHaRisposto

    fun sommario(rconHaRisposto: Boolean = false): String = buildString {
        append(if (acceso(rconHaRisposto)) "in esecuzione" else "fermo")
        minecraftVersion?.let { append(" · Minecraft $it") }
        gamePort?.let { append(" · porta $it") }
        if (rconEnabled) rconPort?.let { append(" · RCON $it") }
        if (!hasServerFiles) append(" · non ancora installato")
    }

    val summary: String
        get() = buildString {
            append(if (running) "in esecuzione" else "fermo")
            minecraftVersion?.let { append(" · Minecraft $it") }
            gamePort?.let { append(" · porta $it") }
            if (rconEnabled) rconPort?.let { append(" · RCON $it") }
            if (!hasServerFiles) append(" · non ancora installato")
        }

    /** Riga delle mod: dice il numero e il loader, se c'è. */
    val modsLabel: String
        get() = when {
            !modded -> ""
            mods.isEmpty() -> "$loader, nessuna mod"
            else -> buildString {
                append(if (mods.size == 1) "1 mod" else "${mods.size} mod")
                loader?.let { append(" · $it") }
            }
        }
}

/**
 * Trova le istanze LinuxGSM nella home dell'utente.
 *
 * Il segno inconfondibile di un'istanza è la cartella `lgsm/config-lgsm/<nome>`:
 * da lì si risale alla directory del server e al nome dello script. Per ognuna si
 * leggono versione, porta, mod installate e se è in esecuzione, così l'utente
 * sceglie da un elenco invece di dover sapere percorsi e nomi.
 */
object Discovery {

    /** Profondità limitata: oltre si rischia di frugare in tutta la home per nulla. */
    private const val MAX_DEPTH = 5

    fun scan(): String = """
        for cfgdir in ${'$'}(find "${'$'}HOME" -maxdepth $MAX_DEPTH -type d -name config-lgsm 2>/dev/null); do
          base=${'$'}(dirname "${'$'}(dirname "${'$'}cfgdir")")
          for inst in "${'$'}cfgdir"/*; do
            [ -d "${'$'}inst" ] || continue
            name=${'$'}(basename "${'$'}inst")
            [ -f "${'$'}base/${'$'}name" ] || continue
            echo "--- istanza"
            echo "script=${'$'}name"
            echo "dir=${'$'}base"
            echo "lgsm=${'$'}(grep -m1 -oE '^[[:space:]]*version="?[^"[:space:]]+' "${'$'}base/${'$'}name" 2>/dev/null | sed 's/.*=//; s/"//g')"
            cfg="${'$'}inst/${'$'}name.cfg"
            echo "versione=${'$'}(grep -hoE '^[[:space:]]*mcversion="?[^"#[:space:]]+' "${'$'}cfg" 2>/dev/null | head -1 | sed 's/.*=//; s/"//g')"
            echo "ramo=${'$'}(grep -hoE '^[[:space:]]*mcbranc[h]?="?[^"#[:space:]]+' "${'$'}cfg" 2>/dev/null | head -1 | sed 's/.*=//; s/"//g')"
            files="${'$'}base/serverfiles"
            props="${'$'}files/server.properties"
            if [ -f "${'$'}props" ]; then
              echo "serverfiles=si"
              echo "porta=${'$'}(grep -hoE '^server-port=.*' "${'$'}props" 2>/dev/null | head -1 | sed 's/.*=//')"
              echo "motd=${'$'}(grep -hoE '^motd=.*' "${'$'}props" 2>/dev/null | head -1 | sed 's/.*=//' | cut -c1-40)"
              echo "rcon=${'$'}(grep -hoE '^rcon.port=.*' "${'$'}props" 2>/dev/null | head -1 | sed 's/.*=//')"
              echo "rconattivo=${'$'}(grep -hoE '^enable-rcon=.*' "${'$'}props" 2>/dev/null | head -1 | sed 's/.*=//')"
            else
              echo "serverfiles=no"
            fi
            if [ -f "${'$'}files/fabric-server-launch.jar" ] || ls "${'$'}files"/fabric-server-*.jar >/dev/null 2>&1; then
              echo "loader=Fabric"
            elif [ -d "${'$'}files/libraries/net/minecraftforge" ] || ls "${'$'}files"/forge-*.jar >/dev/null 2>&1; then
              echo "loader=Forge"
            fi
            for m in "${'$'}files"/mods/*.jar; do
              [ -f "${'$'}m" ] || continue
              echo "mod=${'$'}(basename "${'$'}m" .jar)"
            done
            if tmux has-session -t "${'$'}name" 2>/dev/null; then echo "attivo=si"; else echo "attivo=no"; fi
            echo "spazio=${'$'}(du -sm "${'$'}base" 2>/dev/null | awk '{print ${'$'}1}')"
          done
        done
        echo "--- fine"
    """.trimIndent()

    /**
     * Cancella un'istanza dal computer.
     *
     * `rm -rf` su un percorso arrivato da una scansione merita paranoia: prima di
     * toccare qualsiasi cosa il comando ricontrolla sul server che quella cartella
     * sia davvero un'istanza LinuxGSM (script più `lgsm/config-lgsm`) e rifiuta la
     * home, la radice e i percorsi relativi. Meglio un rifiuto di troppo che una
     * cartella di casa cancellata per un percorso storto.
     */
    fun remove(server: DiscoveredServer): String {
        val dir = Lgsm.sq(server.directory.trimEnd('/'))
        val script = Lgsm.sq(server.script)
        return """
            dir=$dir
            script=$script
            case "${'$'}dir" in
              /|""|"${'$'}HOME"|"${'$'}HOME"/) echo "RIFIUTATO: percorso non consentito"; exit 1;;
              /*) ;;
              *) echo "RIFIUTATO: percorso non assoluto"; exit 1;;
            esac
            [ -f "${'$'}dir/${'$'}script" ] || { echo "RIFIUTATO: niente script LinuxGSM in ${'$'}dir"; exit 1; }
            [ -d "${'$'}dir/lgsm/config-lgsm" ] || { echo "RIFIUTATO: ${'$'}dir non è un'istanza LinuxGSM"; exit 1; }
            cd "${'$'}dir" && ./"${'$'}script" stop >/dev/null 2>&1
            tmux kill-session -t "${'$'}script" >/dev/null 2>&1
            cd "${'$'}HOME" || exit 1
            rm -rf "${'$'}dir" || { echo "ERRORE: cancellazione non riuscita"; exit 1; }
            [ -d "${'$'}dir" ] && { echo "ERRORE: la cartella è ancora lì"; exit 1; }
            echo "CANCELLATO ${'$'}dir"
        """.trimIndent()
    }

    /** Vero solo se il server ha risposto che la cancellazione è andata a buon fine. */
    fun removed(output: String): Boolean = Lgsm.clean(output).contains("CANCELLATO")

    fun parse(raw: String): List<DiscoveredServer> {
        val text = Lgsm.clean(raw)
        return text.split("--- istanza")
            .drop(1)
            .mapNotNull { block ->
                fun value(key: String) = Regex("(?m)^$key=(.*)$")
                    .find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

                val script = value("script") ?: return@mapNotNull null
                val dir = value("dir") ?: return@mapNotNull null
                DiscoveredServer(
                    directory = dir,
                    script = script,
                    minecraftVersion = value("versione"),
                    branch = value("ramo"),
                    hasServerFiles = value("serverfiles") == "si",
                    running = value("attivo") == "si",
                    gamePort = value("porta"),
                    rconPort = value("rcon"),
                    rconEnabled = value("rconattivo") == "true",
                    motd = value("motd"),
                    sizeMb = value("spazio")?.toLongOrNull(),
                    mods = Regex("(?m)^mod=(.*)$").findAll(block)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotBlank() }
                        .toList(),
                    loader = value("loader"),
                    lgsmVersion = value("lgsm")
                )
            }
            .distinctBy { it.directory to it.script }
            .sortedBy { it.displayName.lowercase() }
    }

    /**
     * Porte già occupate da un'istanza precedente nell'elenco.
     *
     * Due server sulla stessa porta non possono stare accesi insieme: il secondo
     * parte e muore, e il messaggio nel log non dice mai che il problema è quello.
     * Vale per la porta di gioco e per quella di RCON, che finiscono nello stesso
     * spazio di numeri: un RCON che pesca la porta di gioco di un altro rompe
     * entrambi. La prima istanza resta pulita, le successive vengono segnalate.
     */
    fun portConflicts(servers: List<DiscoveredServer>): Map<Int, Set<String>> {
        val seen = mutableSetOf<String>()
        val conflicts = mutableMapOf<Int, MutableSet<String>>()
        servers.forEachIndexed { index, server ->
            val ports = listOfNotNull(
                server.gamePort,
                server.rconPort?.takeIf { server.rconEnabled }
            )
            ports.forEach { port ->
                if (!seen.add(port)) conflicts.getOrPut(index) { mutableSetOf() }.add(port)
            }
        }
        return conflicts
    }

    /** Applica un server trovato a un profilo, lasciando intatte le credenziali. */
    fun applyTo(account: ServerConfig, found: DiscoveredServer): ServerConfig =
        account.copy(
            name = account.name.ifBlank { found.displayName },
            slug = found.displayName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                .ifBlank { found.script },
            lgsmDir = found.directory,
            script = found.script,
            serverFilesDir = ""
        )
}
