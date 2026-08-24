package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Una copia di sicurezza trovata sul server. */
data class Backup(
    val fileName: String,
    val epochSeconds: Long,
    val sizeBytes: Long
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(sizeBytes / 1073741824.0)
            sizeBytes >= 1024 * 1024 -> "%.0f MB".format(sizeBytes / 1048576.0)
            else -> "${sizeBytes / 1024} KB"
        }
}

/** Cosa c'è nella cartella dei backup, e quanto spazio resta. */
data class BackupState(
    val backups: List<Backup>,
    /** Ora del server, per dire "due giorni fa" senza fidarsi dell'orologio del telefono. */
    val nowEpochSeconds: Long,
    val usedKb: Long,
    val freeKb: Long,
    val hasFolder: Boolean
) {
    val last: Backup? get() = backups.maxByOrNull { it.epochSeconds }

    /** Da quanto tempo non si fa un backup, in giorni. Null se non ce n'è mai stato uno. */
    val daysSinceLast: Long?
        get() = last?.let { (nowEpochSeconds - it.epochSeconds) / 86_400 }

    fun spaceLabel(kb: Long): String = when {
        kb >= 1024L * 1024 -> "%.1f GB".format(kb / 1048576.0)
        kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
        else -> "$kb KB"
    }
}

/**
 * Le copie di sicurezza del mondo.
 *
 * LinuxGSM le fa con `./<script> backup` e le mette in `lgsm/backup/`, ma non ha
 * un suo modo per farle da sole a orari decisi: quello lo fa il cron del computer.
 * L'app non inventa niente — legge quella cartella e scrive quella riga di cron —
 * così un backup fatto a mano dal terminale e uno fatto dall'app sono la stessa
 * cosa, e restano tutti nello stesso posto.
 */
object Backups {

    /**
     * Dove LinuxGSM mette davvero le copie.
     *
     * Non è `backups/` sotto la cartella del server: è `lgsm/backup/`. Il nome è
     * `<script>-AAAA-MM-GG-hhmmss.tar.gz`, e la pulizia automatica di LinuxGSM
     * cerca `*.tar.*`, quindi anche un archivio rimasto a metà per il disco pieno
     * viene contato come backup.
     */
    private fun dir(cfg: ServerConfig) = "${cfg.lgsmDir.trimEnd('/')}/lgsm/backup"

    /**
     * Elenco delle copie, con quando e quanto pesano, più lo spazio della cartella
     * e quello che resta libero sul disco.
     *
     * L'ora arriva dal server: un telefono con l'orologio sballato direbbe che
     * l'ultimo backup è di domani.
     */
    fun list(cfg: ServerConfig): String {
        val d = Lgsm.path(dir(cfg))
        return """
            d=$d
            echo "ora=${'$'}(date +%s)"
            if [ ! -d "${'$'}d" ]; then echo 'cartella=no'; exit 0; fi
            echo 'cartella=si'
            for f in "${'$'}d"/*.tar.*; do
              [ -f "${'$'}f" ] || continue
              printf '%s\t%s\t%s\n' "${'$'}(stat -c %Y "${'$'}f" 2>/dev/null || echo 0)" \
                "${'$'}(stat -c %s "${'$'}f" 2>/dev/null || echo 0)" "${'$'}(basename "${'$'}f")"
            done
            echo "usati=${'$'}(du -sk "${'$'}d" 2>/dev/null | awk '{print ${'$'}1}')"
            echo "liberi=${'$'}(df -Pk "${'$'}d" 2>/dev/null | awk 'NR==2{print ${'$'}4}')"
        """.trimIndent()
    }

    fun parse(raw: String): BackupState {
        val text = Lgsm.clean(raw)
        fun valore(chiave: String) =
            Regex("(?m)^$chiave=(.*)$").find(text)?.groupValues?.get(1)?.trim().orEmpty()

        val backups = text.lines().mapNotNull { linea ->
            val parti = linea.trim().split('\t')
            if (parti.size < 3) return@mapNotNull null
            val quando = parti[0].toLongOrNull() ?: return@mapNotNull null
            val quanto = parti[1].toLongOrNull() ?: return@mapNotNull null
            val nome = parti[2].trim()
            if (nome.isBlank()) return@mapNotNull null
            Backup(nome, quando, quanto)
        }.sortedByDescending { it.epochSeconds }

        return BackupState(
            backups = backups,
            nowEpochSeconds = valore("ora").toLongOrNull() ?: 0L,
            usedKb = valore("usati").toLongOrNull() ?: 0L,
            freeKb = valore("liberi").toLongOrNull() ?: 0L,
            hasFolder = valore("cartella") == "si"
        )
    }

    /** Il comando che fa la copia adesso. È lo stesso che si userebbe da terminale. */
    fun now(cfg: ServerConfig): String = Lgsm.action(cfg, "backup")

    // ------------------------------------------------------- rimettere una copia

    /** Codice di uscita convenzionale: l'archivio non c'è più. */
    const val EXIT_NO_ARCHIVIO = 95

    /** Codice di uscita convenzionale: il server è acceso. */
    const val EXIT_ACCESO = 94

    /** Codice di uscita convenzionale: non c'è spazio per estrarre. */
    const val EXIT_SPAZIO = 93

    /** Codice di uscita convenzionale: dentro l'archivio non c'è il mondo. */
    const val EXIT_NIENTE_MONDO = 92

    /** Codice di uscita convenzionale: l'estrazione è fallita ed è stato rimesso tutto com'era. */
    const val EXIT_ESTRAZIONE = 91

    private fun nomeValido(nome: String) =
        nome.isNotBlank() &&
                !nome.contains('/') &&
                !nome.contains("..") &&
                nome.all { it.isLetterOrDigit() || it in "-_." }

    /**
     * Cosa c'è dentro un archivio, prima di toccare qualcosa.
     *
     * Serve a due cose: far vedere all'utente cosa sta per tornare indietro, e
     * accorgersi che l'archivio è rotto o che non contiene il mondo. Un
     * `tar -tz` su un archivio da un giga non è gratis, ma è molto meno caro
     * di un ripristino sbagliato.
     */
    fun contenuto(cfg: ServerConfig, nome: String): String {
        require(nomeValido(nome)) { "nome di archivio non valido" }
        val a = "${Lgsm.path(dir(cfg))}/${Lgsm.sq(nome)}"
        return """
            a=$a
            [ -f "${'$'}a" ] || { echo 'ARCHIVIO NON TROVATO'; exit $EXIT_NO_ARCHIVIO; }
            echo "peso=${'$'}(stat -c %s "${'$'}a" 2>/dev/null || echo 0)"
            echo '### elenco'
            tar -tzf "${'$'}a" 2>/dev/null | head -n 4000 > "${'$'}{TMPDIR:-/tmp}/mcm-tar.txt" || {
                echo 'ARCHIVIO ILLEGGIBILE'; exit $EXIT_NO_ARCHIVIO; }
            echo "voci=${'$'}(wc -l < "${'$'}{TMPDIR:-/tmp}/mcm-tar.txt" | tr -d ' ')"
            echo "mondo=${'$'}(grep -c -E '^(\./)?serverfiles/' "${'$'}{TMPDIR:-/tmp}/mcm-tar.txt" || echo 0)"
            echo '### prime'
            head -n 25 "${'$'}{TMPDIR:-/tmp}/mcm-tar.txt"
            rm -f "${'$'}{TMPDIR:-/tmp}/mcm-tar.txt"
        """.trimIndent()
    }

    /** Quante voci dell'archivio riguardano il mondo. Null se non si e' capito. */
    fun vociMondo(raw: String): Int? =
        Regex("(?m)^mondo=(\\d+)$").find(Lgsm.clean(raw))?.groupValues?.get(1)?.toIntOrNull()

    /** Le prime righe dell'elenco, da far vedere. */
    fun anteprima(raw: String): List<String> =
        Lgsm.clean(raw).substringAfter("### prime", "").trim().lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    /**
     * Rimette il mondo com'era in quella copia.
     *
     * Lo script sta in `resources/ripristino.sh` e non qui dentro: è lungo, e uno
     * script sh si prova lanciandolo. Le tre scelte che lo governano -- si tocca
     * solo `serverfiles`, quello che c'è adesso si sposta invece di cancellarlo,
     * il server deve essere fermo -- sono spiegate lì, dove sta il codice che le
     * applica. Provato da `tools/prova-ripristino.sh`.
     */
    fun ripristina(cfg: ServerConfig, nome: String): String {
        require(nomeValido(nome)) { "nome di archivio non valido" }
        return modello()
            .replace("\r", "")
            .replace("@@DIR@@", Lgsm.path(cfg.lgsmDir.trimEnd('/')))
            .replace("@@ARCHIVIO@@", "${Lgsm.path(dir(cfg))}/${Lgsm.sq(nome)}")
            .replace("@@SESSIONE@@", Lgsm.sq(cfg.session))
            .replace("@@EXIT_NO_ARCHIVIO@@", EXIT_NO_ARCHIVIO.toString())
            .replace("@@EXIT_ACCESO@@", EXIT_ACCESO.toString())
            .replace("@@EXIT_SPAZIO@@", EXIT_SPAZIO.toString())
            .replace("@@EXIT_NIENTE_MONDO@@", EXIT_NIENTE_MONDO.toString())
            .replace("@@EXIT_ESTRAZIONE@@", EXIT_ESTRAZIONE.toString())
    }

    private fun modello(): String =
        Backups::class.java.getResourceAsStream("/ripristino.sh")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("ripristino.sh non e' nel pacchetto")

    /** Dove e' finito il mondo di prima, se il ripristino e' riuscito. */
    fun messoDaParte(raw: String): String? =
        Regex("@@RIMESSO\\s*(\\S*)").find(Lgsm.clean(raw))?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun rimesso(raw: String): Boolean = Lgsm.clean(raw).contains("@@RIMESSO")
}
