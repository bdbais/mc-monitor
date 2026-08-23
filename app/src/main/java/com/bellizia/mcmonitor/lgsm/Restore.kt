package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Una copia datata lasciata dall'app prima di modificare un file del server. */
data class Copia(
    /** Percorso completo della copia sul server. */
    val path: String,
    /** Il file che questa copia rimpiazzerebbe. */
    val originale: String,
    val epochSeconds: Long,
    val sizeBytes: Long
) {
    /** Il nome del file toccato, senza cartelle: quello che si legge in elenco. */
    val nomeFile: String get() = originale.substringAfterLast('/')

    val quando: String get() = Restore.leggiData(path)
}

/**
 * Le copie di sicurezza che l'app lascia dietro di sé, e il modo di riprenderle.
 *
 * Ogni volta che l'app scrive su un file del server ne mette da parte una copia
 * con la data nel nome. Fin qui bene — ma finora quelle copie non si potevano
 * riprendere da nessuna parte: chi si trovava il server che non ripartiva doveva
 * collegarsi al computer e sapere cosa cercare. Una rete che non si può tirare
 * non è una rete.
 */
object Restore {

    const val EXIT_NIENTE = 94

    /** Il suffisso che l'app aggiunge: nome del file, poi questo, poi la data. */
    private const val MARCA = ".mcmonitor.bak."

    private val NOME = Regex("""^(.+)\.mcmonitor\.bak\.(\d{8})(\d{6})$""")

    private fun configDir(cfg: ServerConfig) =
        "${cfg.lgsmDir.trimEnd('/')}/lgsm/config-lgsm/${cfg.script}"

    /**
     * Cerca le copie nelle due cartelle in cui l'app scrive: quella dei file di
     * LinuxGSM e quella dei file di gioco. Non si va a cercare altrove: rimettere
     * a posto un file che non abbiamo toccato noi non è un ripristino, è un
     * indovinello.
     */
    fun list(cfg: ServerConfig): String {
        val conf = Lgsm.path(configDir(cfg))
        val gioco = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        return """
            for d in $conf $gioco; do
              [ -d "${'$'}d" ] || continue
              for f in "${'$'}d"/*$MARCA*; do
                [ -f "${'$'}f" ] || continue
                printf '%s\t%s\t%s\n' "${'$'}(stat -c %Y "${'$'}f" 2>/dev/null || echo 0)" \
                  "${'$'}(stat -c %s "${'$'}f" 2>/dev/null || echo 0)" "${'$'}f"
              done
            done
        """.trimIndent()
    }

    fun parse(raw: String): List<Copia> =
        Lgsm.clean(raw).lines().mapNotNull { linea ->
            val parti = linea.trim().split('\t')
            if (parti.size < 3) return@mapNotNull null
            val quando = parti[0].toLongOrNull() ?: return@mapNotNull null
            val quanto = parti[1].toLongOrNull() ?: return@mapNotNull null
            val path = parti[2].trim()
            val m = NOME.find(path) ?: return@mapNotNull null
            Copia(
                path = path,
                originale = m.groupValues[1],
                epochSeconds = quando,
                sizeBytes = quanto
            )
        }.sortedByDescending { it.epochSeconds }

    /**
     * La data si legge dal nome del file e non dalla data di modifica: una copia
     * spostata o toccata avrebbe la data sbagliata, e il nome invece è quello che
     * l'app ha scritto quando l'ha fatta.
     */
    fun leggiData(path: String): String {
        val m = NOME.find(path) ?: return ""
        val g = m.groupValues[2]
        val o = m.groupValues[3]
        return "${g.substring(6, 8)}/${g.substring(4, 6)}/${g.substring(0, 4)} " +
                "alle ${o.substring(0, 2)}:${o.substring(2, 4)}"
    }

    /**
     * Una copia si accetta solo se sta in una delle due cartelle che tocchiamo e
     * se il nome è quello che scriviamo noi. Il percorso arriva pur sempre da una
     * risposta del server, e finisce in una riga di shell.
     */
    fun accettabile(cfg: ServerConfig, copia: Copia): Boolean {
        if (!NOME.matches(copia.path)) return false
        if (copia.path.contains("..")) return false
        val ammesse = listOf(configDir(cfg), cfg.serverFiles.trimEnd('/'))
            .map { it.replace("~", "") }
        return ammesse.any { copia.originale.contains(it) }
    }

    /** Cosa cambierebbe: le righe che tornerebbero e quelle che sparirebbero. */
    fun differenza(copia: Copia): String {
        val b = Lgsm.sq(copia.path)
        val o = Lgsm.sq(copia.originale)
        return "b=$b; o=$o; " +
                "[ -f \"\$b\" ] || { echo 'LA COPIA NON C E PIU'; exit $EXIT_NIENTE; }; " +
                "if [ ! -f \"\$o\" ]; then echo 'Il file adesso non esiste: verrebbe ricreato.'; exit 0; fi; " +
                "if command -v diff >/dev/null 2>&1; then " +
                "if diff -u \"\$o\" \"\$b\" > /tmp/.mcm.diff 2>/dev/null; then echo 'UGUALI'; " +
                "else sed -n '3,120p' /tmp/.mcm.diff; fi; rm -f /tmp/.mcm.diff; " +
                "else echo '--- adesso ---'; head -60 \"\$o\"; echo '--- la copia ---'; head -60 \"\$b\"; fi"
    }

    /**
     * Rimette la copia al suo posto.
     *
     * Prima però mette da parte com'è adesso, con la stessa marca: anche tornare
     * indietro è una modifica, e se si torna indietro dalla cosa sbagliata si deve
     * poter tornare avanti.
     */
    fun restore(copia: Copia): String {
        val b = Lgsm.sq(copia.path)
        val o = Lgsm.sq(copia.originale)
        return "b=$b; o=$o; " +
                "[ -f \"\$b\" ] || { echo 'LA COPIA NON C E PIU'; exit $EXIT_NIENTE; }; " +
                "if [ -f \"\$o\" ]; then cp \"\$o\" \"\$o$MARCA\$(date +%Y%m%d%H%M%S)\" || exit 1; fi; " +
                "cp \"\$b\" \"\$o\" && echo 'RIPRISTINATO' && echo \"--- ora il file dice ---\" && head -40 \"\$o\""
    }

    fun ripristinato(output: String) = Lgsm.clean(output).contains("RIPRISTINATO")

    /**
     * Le copie che riguardano lo stesso file, dalla più recente.
     * In elenco si mostrano raggruppate: di solito interessa l'ultima.
     */
    fun perFile(copie: List<Copia>): List<Pair<String, List<Copia>>> =
        copie.groupBy { it.originale }
            .map { (file, elenco) -> file to elenco.sortedByDescending { it.epochSeconds } }
            .sortedByDescending { it.second.firstOrNull()?.epochSeconds ?: 0 }
}
