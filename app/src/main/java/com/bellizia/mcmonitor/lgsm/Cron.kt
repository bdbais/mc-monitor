package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import java.util.Base64

/** Ogni quanto rifare il backup. */
enum class Cadenza(val etichetta: String) {
    GIORNO("ogni giorno"),
    SETTIMANA("ogni settimana"),
    MESE("ogni mese")
}

/**
 * Quando fare il backup, detto come lo direbbe una persona.
 *
 * Non si offre la sintassi di cron: è potente e si sbaglia in silenzio. Ogni
 * giorno, ogni settimana o ogni mese, a un'ora, coprono quello che serve.
 */
data class PianoBackup(
    val cadenza: Cadenza,
    val ora: Int,
    val minuto: Int,
    /** 1 = lunedì … 7 = domenica. Serve solo con [Cadenza.SETTIMANA]. */
    val giornoSettimana: Int = 1,
    /** Dal 1 al 28: il 29, 30 e 31 salterebbero mesi interi. */
    val giornoMese: Int = 1
) {
    val valido: Boolean
        get() = ora in 0..23 && minuto in 0..59 &&
                giornoSettimana in 1..7 && giornoMese in 1..28

    private val orario: String get() = "%02d:%02d".format(ora, minuto)

    private val giorni = listOf(
        "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"
    )

    /** Come si legge in italiano, per la conferma e per la riga sotto il pulsante. */
    fun descrizione(): String = when (cadenza) {
        Cadenza.GIORNO -> "ogni giorno alle $orario"
        Cadenza.SETTIMANA -> "ogni ${giorni[giornoSettimana - 1]} alle $orario"
        Cadenza.MESE -> "il $giornoMese di ogni mese alle $orario"
    }

    /** I cinque campi di cron. La domenica per cron è 0, per noi è 7. */
    fun quando(): String = when (cadenza) {
        Cadenza.GIORNO -> "$minuto $ora * * *"
        Cadenza.SETTIMANA -> "$minuto $ora * * ${giornoSettimana % 7}"
        Cadenza.MESE -> "$minuto $ora $giornoMese * *"
    }
}

/** Cosa ha risposto il computer quando gli abbiamo chiesto il suo crontab. */
sealed interface Crontab {
    /** Letto, e questo è il contenuto (può essere vuoto: nessun crontab). */
    data class Letto(val testo: String) : Crontab

    /** Non si è capito: NON si scrive niente. */
    data class Illeggibile(val motivo: String) : Crontab
}

/**
 * La riga di cron che fa partire il backup da sola.
 *
 * Questa è l'unica parte dell'app che tocca una cosa che non è sua: il crontab
 * dell'utente può contenere righe scritte da lui, da anni, che non c'entrano
 * niente con Minecraft. Perderle sarebbe un danno vero e silenzioso.
 *
 * Da qui vengono tre scelte, tutte per lo stesso motivo:
 *
 * - **niente pipeline.** `crontab -l | filtro | crontab -` fa partire i tre
 *   processi insieme: se la lettura fallisce, il filtro non riceve niente e
 *   `crontab -` installa un crontab VUOTO restituendo successo. Qui si legge, si
 *   decide in Kotlin, e solo se la lettura è andata bene si scrive.
 * - **il testo viaggia in base64.** Un byte che non è UTF-8 nei commenti di
 *   qualcun altro, decodificato e riscritto, tornerebbe indietro storpiato.
 * - **si tiene una copia** di com'era prima di ogni scrittura, sul telefono.
 *
 * Il blocco nostro è delimitato da due righe di commento con dentro il nome del
 * server: così più server sullo stesso computer non si pestano i piedi, e tutto
 * quello che sta fuori da quelle due righe non viene mai toccato.
 */
object Cron {

    const val EXIT_ILLEGGIBILE = 88

    /** Assegnazioni che, scritte sopra il nostro blocco, cambiano cosa vuol dire un orario. */
    private val PERICOLOSE = listOf("CRON_TZ", "RANDOM_DELAY")

    fun inizio(slug: String) = "# >>> MC Monitor: backup di ${pulisci(slug)} (scritto dall'app)"

    fun fine(slug: String) = "# <<< MC Monitor: backup di ${pulisci(slug)}"

    private fun pulisci(s: String) =
        s.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(24).ifBlank { "server" }

    // -------------------------------------------------------------- lettura

    /**
     * Legge il crontab, tenendo separati esito, uscita ed errore.
     *
     * I marcatori servono perché la shell di login dell'utente può stampare di
     * suo (un saluto in `.bashrc`, un `neofetch`): senza, quella roba entrerebbe
     * nella base e verrebbe riscritta dentro il crontab.
     */
    fun read(): String = """
        t=${'$'}(mktemp) || exit $EXIT_ILLEGGIBILE
        e=${'$'}(mktemp) || exit $EXIT_ILLEGGIBILE
        LC_ALL=C crontab -l >"${'$'}t" 2>"${'$'}e"; rc=${'$'}?
        echo '@@INIZIO'
        echo "@@RC ${'$'}rc"
        echo '@@OUT'
        base64 <"${'$'}t"
        echo '@@ERR'
        base64 <"${'$'}e"
        echo '@@FINE'
        rm -f "${'$'}t" "${'$'}e"
    """.trimIndent()

    /**
     * Decide se quello che è tornato è un crontab vero.
     *
     * Non avere nessun crontab è normale e non è un errore: `crontab -l` esce con
     * 1 e scrive "no crontab for utente". Qualsiasi altro fallimento invece è un
     * "non ho capito", e in quel caso non si scrive niente: scrivere partendo da
     * una lettura fallita vuol dire cancellare quello che c'era.
     */
    fun parseRead(raw: String): Crontab {
        val text = Lgsm.clean(raw)
        val dentro = text.substringAfter("@@INIZIO", "").substringBefore("@@FINE", "")
        if (dentro.isBlank()) return Crontab.Illeggibile("il computer non ha risposto come previsto")

        val rc = Regex("@@RC\\s+(-?\\d+)").find(dentro)?.groupValues?.get(1)?.toIntOrNull()
            ?: return Crontab.Illeggibile("manca l'esito del comando crontab")

        val out = decodifica(dentro.substringAfter("@@OUT", "").substringBefore("@@ERR", ""))
        val err = decodifica(dentro.substringAfter("@@ERR", ""))

        if (rc == 0) return Crontab.Letto(out ?: return Crontab.Illeggibile("crontab illeggibile"))

        val motivo = (err ?: "").lowercase()
        val nessunCrontab = motivo.contains("no crontab for") ||
                motivo.contains("no crontab") ||
                (out.isNullOrBlank() && motivo.isBlank())
        return if (nessunCrontab) {
            Crontab.Letto("")
        } else {
            Crontab.Illeggibile(
                (err ?: "").trim().ifBlank { "crontab -l è uscito con $rc" }.take(200)
            )
        }
    }

    private fun decodifica(b64: String): String? {
        val pulito = b64.filter { !it.isWhitespace() }
        if (pulito.isEmpty()) return ""
        return runCatching {
            // I byte del crontab di qualcun altro non sono per forza UTF-8: si
            // tengono come sono, uno a uno, e si riscrivono identici.
            String(Base64.getDecoder().decode(pulito), Charsets.ISO_8859_1)
        }.getOrNull()
    }

    // ------------------------------------------------------------ scrittura

    /**
     * Il pezzo di crontab che appartiene a noi.
     *
     * Il `cd` nella cartella e il `./script` non sono forma: LinuxGSM riconosce il
     * proprio backup in corso cercando esattamente `/bin/bash ./mcserver backup`
     * fra i processi. Lanciato con il percorso assoluto non si riconosce, e il
     * suo guardiano riavvia il server mentre l'archivio si sta ancora scrivendo.
     */
    fun blocco(cfg: ServerConfig, piano: PianoBackup): String {
        require(piano.valido) { "orario non valido" }
        val dir = Lgsm.path(cfg.lgsmDir.trimEnd('/'))
        val script = "./" + cfg.script.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val log = "\"\$HOME\"/.mcmonitor/backup.log"
        val comando = "cd $dir && $script backup >> $log 2>&1"
        return buildString {
            append(inizio(cfg.slug)).append('\n')
            append("# ").append(piano.descrizione()).append('\n')
            append(piano.quando()).append(' ').append(protezione(comando)).append('\n')
            append(fine(cfg.slug)).append('\n')
        }
    }

    /**
     * Nel comando di una riga di cron il segno di percentuale vuol dire "a capo",
     * e taglierebbe il comando in due.
     */
    fun protezione(comando: String): String = comando.replace("%", "\\%")

    /**
     * Toglie il vecchio blocco e, se c'è, mette il nuovo. Tutto quello che sta
     * fuori dai due marcatori resta esattamente com'era.
     */
    fun componi(attuale: String, cfg: ServerConfig, blocco: String?): String {
        val apre = inizio(cfg.slug)
        val chiude = fine(cfg.slug)
        val tenute = mutableListOf<String>()
        var dentro = false
        attuale.split("\n").forEach { riga ->
            when {
                riga.trimEnd() == apre -> dentro = true
                riga.trimEnd() == chiude -> dentro = false
                !dentro -> tenute += riga
            }
        }
        // Le righe vuote in fondo si accumulerebbero a ogni giro.
        while (tenute.isNotEmpty() && tenute.last().isBlank()) tenute.removeAt(tenute.size - 1)

        val testo = buildString {
            if (tenute.isNotEmpty()) {
                append(tenute.joinToString("\n"))
                append('\n')
            }
            if (blocco != null) append(blocco)
        }
        // Senza l'a capo finale crontab rifiuta il file, il vecchio resta al suo
        // posto, e l'app direbbe "programmato" senza che sia programmato niente.
        return if (testo.isEmpty() || testo.endsWith("\n")) testo else "$testo\n"
    }

    /**
     * Controlla che quello che stiamo per installare sia scrivibile senza danni.
     *
     * Un ritorno a capo di Windows non è un errore di sintassi per cron: lo accetta
     * in silenzio, e da quel momento il comando è `backup\r`, che non esiste. Il
     * backup non parte più, tutte le notti, senza che nessuno se ne accorga.
     */
    fun problemi(nuovo: String): List<String> = buildList {
        if (nuovo.contains('\r')) {
            add("c'è un ritorno a capo di Windows: cron lo accetterebbe e il comando non partirebbe mai")
        }
        if (nuovo.isNotEmpty() && !nuovo.endsWith("\n")) {
            add("manca l'a capo finale: crontab rifiuterebbe il file")
        }
        nuovo.split("\n").forEach { riga ->
            if (riga.length > 1000) add("una riga è troppo lunga per cron")
        }
    }

    /**
     * Cose scritte da altri che cambierebbero il significato del nostro orario.
     *
     * `CRON_TZ` sposta il fuso di tutte le righe che vengono dopo, `RANDOM_DELAY`
     * fa partire il comando fino a mezz'ora più tardi. Con una di queste in giro,
     * l'orario mostrato nell'app non sarebbe l'orario vero: meglio dirlo che
     * mostrare un numero sbagliato.
     */
    fun avvertenze(attuale: String): List<String> = buildList {
        attuale.split("\n").forEach { riga ->
            val t = riga.trim()
            if (t.startsWith("#")) return@forEach
            PERICOLOSE.forEach { nome ->
                if (Regex("^$nome\\s*=").containsMatchIn(t)) {
                    add("nel crontab c'è $t: l'orario vero potrebbe non essere quello scelto qui")
                }
            }
        }
    }

    // ------------------------------------------------------------- rilettura

    /** Ritrova il piano dal crontab, per mostrare cosa c'è programmato adesso. */
    fun leggiPiano(crontab: String, cfg: ServerConfig): PianoBackup? {
        val apre = inizio(cfg.slug)
        val chiude = fine(cfg.slug)
        var dentro = false
        crontab.split("\n").forEach { riga ->
            val t = riga.trimEnd()
            when {
                t == apre -> dentro = true
                t == chiude -> dentro = false
                dentro && !t.trim().startsWith("#") && t.isNotBlank() -> {
                    return dalCampi(t.trim())
                }
            }
        }
        return null
    }

    private fun dalCampi(riga: String): PianoBackup? {
        val campi = riga.split(Regex("\\s+"))
        if (campi.size < 5) return null
        val minuto = campi[0].toIntOrNull() ?: return null
        val ora = campi[1].toIntOrNull() ?: return null
        val giornoMese = campi[2]
        val giornoSettimana = campi[4]
        return when {
            giornoMese == "*" && giornoSettimana == "*" ->
                PianoBackup(Cadenza.GIORNO, ora, minuto)
            giornoMese == "*" -> {
                val g = giornoSettimana.toIntOrNull() ?: return null
                PianoBackup(Cadenza.SETTIMANA, ora, minuto, giornoSettimana = if (g == 0) 7 else g)
            }
            else -> {
                val g = giornoMese.toIntOrNull() ?: return null
                PianoBackup(Cadenza.MESE, ora, minuto, giornoMese = g.coerceIn(1, 28))
            }
        }
    }

    fun programmato(crontab: String, cfg: ServerConfig): Boolean =
        crontab.contains(inizio(cfg.slug))

    // --------------------------------------------------------- installazione

    /** Dove il nuovo crontab viene depositato prima di essere installato. */
    const val FILE_NUOVO = ".mcmonitor/cron.nuovo"

    /**
     * Installa il file appena caricato, e rilegge per controllare.
     *
     * Non si dice "fatto" perché `crontab` è uscito con zero: su alcuni sistemi
     * esce con zero anche quando non ha scritto niente. Si rilegge, e il
     * chiamante confronta.
     */
    fun install(): String {
        // Il ritorno a capo di Windows si cerca contando i byte, non con grep né
        // con awk: a seconda di come sono compilati, quei due lo tolgono da soli
        // leggendo il file e direbbero che non c'è. Contare non si può sbagliare.
        val controllo = """
            f="${'$'}HOME"/$FILE_NUOVO
            [ -s "${'$'}f" ] || { echo '@@VUOTO'; exit $EXIT_ILLEGGIBILE; }
            n=${'$'}(wc -c < "${'$'}f")
            m=${'$'}(tr -d '\r' < "${'$'}f" | wc -c)
            [ "${'$'}n" = "${'$'}m" ] || { echo '@@CR'; exit $EXIT_ILLEGGIBILE; }
            LC_ALL=C crontab "${'$'}f"; rc=${'$'}?
            echo "@@INSTALLATO ${'$'}rc"
            rm -f "${'$'}f"
        """.trimIndent()
        // La rilettura viene dopo: `crontab` su alcuni sistemi esce con zero
        // anche quando non ha scritto niente, quindi l'esito lo dice il confronto.
        return controllo + "\n" + read()
    }

    fun installato(raw: String): Int? =
        Regex("@@INSTALLATO\\s+(-?\\d+)").find(Lgsm.clean(raw))?.groupValues?.get(1)?.toIntOrNull()

    /** Se sul computer c'è davvero un cron che gira, non solo il comando. */
    fun demone(): String = """
        if command -v crontab >/dev/null 2>&1; then echo 'comando=si'; else echo 'comando=no'; fi
        if command -v pgrep >/dev/null 2>&1; then
          if pgrep -x cron >/dev/null 2>&1 || pgrep -x crond >/dev/null 2>&1; then
            echo 'demone=si'
          else
            echo 'demone=no'
          fi
        else
          echo 'demone=boh'
        fi
    """.trimIndent()
}
