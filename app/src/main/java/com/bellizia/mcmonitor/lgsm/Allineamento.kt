package com.bellizia.mcmonitor.lgsm

/** Le due liste di un server, come stanno adesso. */
data class Liste(
    val ammessi: List<PlayerEntry>,
    val bannati: List<PlayerEntry>
)

/** Chi manca di là, diviso per lista. */
data class Differenza(
    val daAmmettere: List<PlayerEntry>,
    val daBannare: List<PlayerEntry>
) {
    val quanti: Int get() = daAmmettere.size + daBannare.size
    val vuota: Boolean get() = quanti == 0
}

/**
 * Portare chi è già in elenco su un altro server.
 *
 * È diverso dal ripetere un provvedimento appena preso: qui il ban c'era già,
 * magari da mesi, e il server nuovo è arrivato dopo. Rifarli a mano uno per uno
 * guardando due schermate è il modo migliore per saltarne uno, e quello saltato
 * è esattamente quello che poi rientra.
 *
 * **Si aggiunge soltanto.** Chi è ammesso di qua e non di là può esserlo per una
 * ragione — un mondo per gli amici stretti, uno per tutti — e togliere qualcuno
 * da un elenco perché manca dall'altro sarebbe una decisione che nessuno ha
 * preso. La differenza si mostra in tutte e due le direzioni, ma il gesto è
 * sempre "porta di là", mai "togli di qua".
 */
object Allineamento {

    /**
     * Chi c'è nelle liste di partenza e non in quelle di arrivo.
     *
     * Il confronto è sul nome senza badare alle maiuscole: Minecraft le
     * conserva ma non le distingue, e due elenchi scritti in momenti diversi
     * possono avere lo stesso giocatore in due modi.
     */
    fun differenza(partenza: Liste, arrivo: Liste): Differenza {
        fun mancanti(qui: List<PlayerEntry>, la: List<PlayerEntry>): List<PlayerEntry> {
            val nomi = la.map { it.name.lowercase() }.toSet()
            return qui.filterNot { it.name.lowercase() in nomi }
                .sortedBy { it.name.lowercase() }
        }
        // Chi di la' sta nell'altra lista non si tocca: qualcuno ha deciso che
        // su quel mondo quella persona sta li', e portarci sopra la decisione
        // presa altrove vorrebbe dire disfarla senza chiedere. Si fa vedere in
        // [incoerenti], e la decisione resta a chi guarda.
        val bannatiLa = arrivo.bannati.map { it.name.lowercase() }.toSet()
        val ammessiLa = arrivo.ammessi.map { it.name.lowercase() }.toSet()
        return Differenza(
            daAmmettere = mancanti(partenza.ammessi, arrivo.ammessi)
                .filterNot { it.name.lowercase() in bannatiLa },
            daBannare = mancanti(partenza.bannati, arrivo.bannati)
                .filterNot { it.name.lowercase() in ammessiLa }
        )
    }

    /**
     * Chi è in una lista di qua e nell'altra di là.
     *
     * È il caso che non va deciso da un programma: se qualcuno è ammesso su un
     * mondo e bannato sull'altro, qualcuno ha preso una decisione, e portarla
     * via sarebbe disfarla senza chiedere. Si fa vedere e basta.
     */
    fun incoerenti(a: Liste, b: Liste): List<String> {
        val ammessiA = a.ammessi.map { it.name.lowercase() }.toSet()
        val bannatiA = a.bannati.map { it.name.lowercase() }.toSet()
        val ammessiB = b.ammessi.map { it.name.lowercase() }.toSet()
        val bannatiB = b.bannati.map { it.name.lowercase() }.toSet()
        return ((ammessiA intersect bannatiB) + (bannatiA intersect ammessiB))
            .sorted()
            .toList()
    }

    /** I comandi da mandare al server di arrivo, nell'ordine. */
    fun comandi(d: Differenza): List<String> =
        d.daAmmettere.map { "whitelist add ${it.name}" } +
                d.daBannare.map { "ban ${it.name}" }

    /** Come si legge la differenza, prima di toccare qualcosa. */
    fun descrizione(
        nomePartenza: String,
        nomeArrivo: String,
        d: Differenza,
        incoerenti: List<String>
    ): String = buildString {
        if (d.vuota) {
            append("Le liste di $nomeArrivo hanno già tutti quelli di $nomePartenza.")
        } else {
            append("Su $nomeArrivo mancano:")
            if (d.daAmmettere.isNotEmpty()) {
                append("\n\n${d.daAmmettere.size} da ammettere:")
                d.daAmmettere.take(15).forEach { append("\n· ").append(it.name) }
                if (d.daAmmettere.size > 15) append("\n· e altri ${d.daAmmettere.size - 15}")
            }
            if (d.daBannare.isNotEmpty()) {
                append("\n\n${d.daBannare.size} da bannare:")
                d.daBannare.take(15).forEach { append("\n· ").append(it.name) }
                if (d.daBannare.size > 15) append("\n· e altri ${d.daBannare.size - 15}")
            }
            append("\n\nSi aggiunge soltanto: da $nomeArrivo non viene tolto nessuno.")
        }
        if (incoerenti.isNotEmpty()) {
            append("\n\nAttenzione: ")
            append(incoerenti.joinToString(", "))
            append(
                if (incoerenti.size == 1) " è ammesso su un server e bannato sull'altro."
                else " sono ammessi su un server e bannati sull'altro."
            )
            append(" Non li tocco: è una decisione che ha preso qualcuno, e disfarla non tocca a me.")
        }
    }
}
