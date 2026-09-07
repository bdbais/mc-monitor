package com.bellizia.mcmonitor.ui.svago

/**
 * La tabella dei record, con le tre lettere.
 *
 * Tre lettere e non un nome per intero: è la forma che avevano, e senza quella
 * il resto — le vite, il punteggio che scende, il game over — sarebbe solo
 * nostalgia mal fatta. È anche una decisione pratica: tre caselle si compilano
 * con i pollici in due secondi, un nome no.
 *
 * Sta tutto qui e non sa niente di Android: quello che serve è decidere chi
 * entra e in che ordine, e quella è una regola che si prova.
 */
object Record {

    /** Quante righe. Cinque stanno in uno schermo senza far scorrere niente. */
    const val QUANTI = 5

    data class Riga(val iniziali: String, val punti: Int)

    /**
     * Tre lettere, sempre e comunque.
     *
     * Chi non scrive niente non resta senza riga: il posto se l'è guadagnato e
     * glielo si tiene. Le tre trattini sono quello che scrivevano le sale
     * giochi quando il tempo per digitare finiva.
     */
    fun iniziali(scritto: String): String {
        val lettere = scritto.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(3)
        return if (lettere.isEmpty()) "---" else lettere.padEnd(3, '-')
    }

    /**
     * Se questo punteggio entra in tabella.
     *
     * Uno zero non entra mai, nemmeno con la tabella vuota: chiedere le
     * iniziali a chi ha perso subito non è un premio, è una presa in giro.
     */
    fun entra(tabella: List<Riga>, punti: Int): Boolean =
        punti > 0 && (tabella.size < QUANTI || punti > tabella.minOf { it.punti })

    /**
     * Inserisce e riordina.
     *
     * A parità di punti il nuovo arrivato sta **sotto** chi c'era già: il
     * record lo tiene chi l'ha fatto per primo, ed è l'unica regola che non
     * fa arrabbiare nessuno.
     */
    fun inserisci(tabella: List<Riga>, riga: Riga): List<Riga> =
        (tabella + riga).sortedWith(compareByDescending<Riga> { it.punti }).take(QUANTI)

    /** In che posizione è finito, da 1. Zero se non è entrato. */
    fun posizione(tabella: List<Riga>, riga: Riga): Int {
        val i = tabella.indexOfFirst { it === riga || (it.iniziali == riga.iniziali && it.punti == riga.punti) }
        return if (i < 0) 0 else i + 1
    }

    // ------------------------------------------------ come si tiene da parte

    /**
     * `ABC:1200|DEF:900`.
     *
     * Un formato di una riga, letto in modo che una riga storta non porti via
     * anche le altre: quello che non si capisce si salta. Una tabella dei
     * record che sparisce perché una riga era rotta è un danno vero per chi ci
     * teneva.
     */
    fun scrivi(tabella: List<Riga>): String =
        tabella.joinToString("|") { "${it.iniziali}:${it.punti}" }

    fun leggi(testo: String): List<Riga> =
        testo.split("|").mapNotNull { pezzo ->
            val parti = pezzo.split(":")
            if (parti.size != 2) return@mapNotNull null
            val punti = parti[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (punti <= 0) return@mapNotNull null
            Riga(iniziali(parti[0]), punti)
        }.sortedByDescending { it.punti }.take(QUANTI)

    /** Come si mostra: posizione, iniziali, punti, incolonnati. */
    fun tabellone(tabella: List<Riga>, evidenzia: Riga? = null): String {
        if (tabella.isEmpty()) return "Nessun record. Sii il primo."
        return tabella.mapIndexed { i, r ->
            val segno = if (evidenzia != null && r.iniziali == evidenzia.iniziali &&
                r.punti == evidenzia.punti
            ) "▶" else " "
            "$segno ${i + 1}.  ${r.iniziali}   ${r.punti.toString().padStart(6)}"
        }.joinToString("\n")
    }
}
