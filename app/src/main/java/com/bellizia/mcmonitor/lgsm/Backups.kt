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
}
