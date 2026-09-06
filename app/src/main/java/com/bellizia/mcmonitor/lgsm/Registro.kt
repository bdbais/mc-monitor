package com.bellizia.mcmonitor.lgsm

/**
 * Come si guarda un registro quando qualcosa si è rotto.
 *
 * Un log di Minecraft che va in errore non produce una riga: ne produce
 * centinaia. Una eccezione Java è una riga che dice cosa è successo e poi
 * cinquanta righe `at ...` che dicono da dove, e le cinquanta sono utili a chi
 * ha il sorgente davanti, non a chi deve capire se il server è ripartito. Con la
 * ricerca per testo non se ne esce: bisogna già sapere cosa cercare.
 *
 * Qui non si butta via niente — il registro intero resta ed è quello che si
 * condivide — si sceglie solo cosa mostrare.
 */
object Registro {

    /**
     * Le righe che segnalano un guaio.
     *
     * `WARN` c'è dentro perché in Minecraft molti problemi veri escono come
     * avviso: una mod che non carica, una porta occupata, un mondo che non si
     * salva. Lasciarlo fuori vorrebbe dire nascondere proprio le righe che si
     * stanno cercando.
     */
    private val GUAIO = Regex(
        """\b(ERROR|SEVERE|FATAL|WARN(?:ING)?)\b|(?:^|\s)(?:[\w.$]+\.)?\w*(?:Exception|Error|Throwable)\b|^Caused by:|^\s*\.\.\. \d+ more""",
        RegexOption.IGNORE_CASE
    )

    /** Una riga di traccia: `\tat qualcosa(...)`. Sono quelle che soffocano tutto. */
    private val TRACCIA = Regex("""^\s*at\s+\S""")

    /**
     * Quante righe di traccia si tengono sotto ogni errore.
     *
     * Le prime tre dicono già dove è successo. Le altre quaranta dicono come ci
     * si è arrivati, e servono solo con il sorgente aperto.
     */
    private const val TRACCIA_VISIBILE = 3

    /**
     * Solo le righe che parlano di un guaio, con le prime righe di traccia.
     *
     * Le tracce che restano fuori non spariscono: al loro posto si conta quante
     * erano, così si vede che c'è dell'altro e si sa che il registro intero è
     * un'altra cosa da questa vista.
     */
    fun soloGuai(testo: String): String {
        val fuori = StringBuilder()
        var tracceTenute = 0
        var tracceSaltate = 0
        var dentroUnGuaio = false

        fun chiudiTraccia() {
            if (tracceSaltate > 0) {
                fuori.append("        … e altre ").append(tracceSaltate)
                    .append(if (tracceSaltate == 1) " riga di traccia" else " righe di traccia")
                    .append('\n')
            }
            tracceTenute = 0
            tracceSaltate = 0
        }

        for (riga in testo.lines()) {
            when {
                TRACCIA.containsMatchIn(riga) && dentroUnGuaio -> {
                    if (tracceTenute < TRACCIA_VISIBILE) {
                        fuori.append(riga).append('\n')
                        tracceTenute++
                    } else {
                        tracceSaltate++
                    }
                }

                GUAIO.containsMatchIn(riga) -> {
                    chiudiTraccia()
                    fuori.append(riga).append('\n')
                    dentroUnGuaio = true
                }

                else -> {
                    chiudiTraccia()
                    dentroUnGuaio = false
                }
            }
        }
        chiudiTraccia()
        return fuori.toString().trimEnd('\n')
    }

    /** La coda di una traccia: `... 14 more`. Non e' un guaio in piu', e' lo stesso. */
    private val CODA_TRACCIA = Regex("""^\s*\.\.\. \d+ more""")

    /**
     * Quante righe del registro parlano di un guaio.
     *
     * Le righe di traccia non si contano: sono il seguito di un guaio gia'
     * contato, e contarle vorrebbe dire dire "cinquanta problemi" a chi ne ha uno.
     */
    fun quantiGuai(testo: String): Int = testo.lines().count {
        GUAIO.containsMatchIn(it) && !TRACCIA.containsMatchIn(it) && !CODA_TRACCIA.containsMatchIn(it)
    }

    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")

    /**
     * Almeno quattro gruppi, non tre.
     *
     * Con tre, `12:00:00` era un indirizzo IPv6 — e ogni riga di un registro
     * comincia con un orario. Avrebbe coperto l'ora di tutte le righe lasciando
     * in piedi gli indirizzi veri, che di gruppi ne hanno otto. Se ne è accorto
     * il test, non io.
     */
    private val IPV6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\b""")

    /**
     * Il registro pronto da mandare fuori.
     *
     * Gli indirizzi IP vengono coperti: in un log di Minecraft sono quelli da cui
     * si collegano i giocatori, cioè le case di altre persone, e per capire un
     * errore non servono mai. I nomi restano — senza, il registro non risponde
     * più alla domanda per cui lo si sta mandando.
     *
     * Quello che è stato coperto è scritto in cima: chi riceve deve sapere che
     * sta guardando un testo modificato, o cercherà a lungo un indirizzo che
     * nel file non c'è più.
     */
    fun perCondivisione(testo: String, intestazione: List<String>): String {
        var coperti = 0
        val pulito = testo.lines().joinToString("\n") { riga ->
            IPV6.replace(IPV4.replace(riga) { coperti++; "‹indirizzo›" }) { coperti++; "‹indirizzo›" }
        }
        return buildString {
            intestazione.forEach { appendLine("# $it") }
            appendLine("# righe: ${testo.lines().size}")
            appendLine(
                if (coperti > 0) "# $coperti indirizzi IP sono stati coperti: sono le case dei giocatori"
                else "# nessun indirizzo IP da coprire"
            )
            appendLine()
            append(pulito)
        }
    }
}
