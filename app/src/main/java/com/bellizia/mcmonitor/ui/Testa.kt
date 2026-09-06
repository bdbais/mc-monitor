package com.bellizia.mcmonitor.ui

/**
 * La faccia di un giocatore, disegnata qui.
 *
 * Gli avatar veri stanno su servizi esterni: per averli bisognerebbe mandare a
 * un sito di terzi i nomi di chi gioca sul tuo server, ogni volta che il
 * cruscotto si aggiorna. Sarebbe l'unica cosa in tutta l'app a uscire verso
 * qualcuno che non sei tu, per una decorazione. Quindi la testa se la disegna
 * l'app, dal nome e basta: niente rete, niente attesa, e funziona anche col
 * telefono senza campo.
 *
 * Non somiglia alla skin vera e non ci prova. Serve a una cosa sola: che sei
 * facce affiancate si distinguano da lontano senza leggere i nomi.
 *
 * Otto per otto, che e' la misura della faccia di una testa in Minecraft.
 */
object Testa {

    const val LATO = 8

    /**
     * I toni della pelle. Sono pochi e ben distanti fra loro: servono a
     * distinguere, non a rappresentare qualcuno.
     */
    private val PELLE = intArrayOf(
        0xFFF9DCC4.toInt(), 0xFFEEC39A.toInt(), 0xFFD4A276.toInt(), 0xFFC68642.toInt(),
        0xFFA9713B.toInt(), 0xFF8D5524.toInt(), 0xFF6B4423.toInt(), 0xFF4A2F1A.toInt(),
        0xFF9BE3C6.toInt(), 0xFFB8C7E8.toInt(),
    )

    /** I colori dei capelli, compresi quelli che in natura non esistono. */
    private val CAPELLI = intArrayOf(
        0xFF1C1206.toInt(), 0xFF3B2412.toInt(), 0xFF6A3D1B.toInt(), 0xFFA45A28.toInt(),
        0xFFD9A441.toInt(), 0xFFEFD9A0.toInt(), 0xFF8C8C8C.toInt(), 0xFFE6E6E6.toInt(),
        0xFF2F5D8C.toInt(), 0xFF3E8C4A.toInt(), 0xFF8C2F4A.toInt(), 0xFF6A3E8C.toInt(),
    )

    /** Gli occhi. Il bianco resta bianco: e' quello che rende una faccia una faccia. */
    private val IRIDE = intArrayOf(
        0xFF2B1B0E.toInt(), 0xFF3E6B3A.toInt(), 0xFF2F5D8C.toInt(), 0xFF6B4A2F.toInt(),
    )

    private const val BIANCO = 0xFFF2F2F2.toInt()

    /**
     * Un numero stabile ricavato dal nome.
     *
     * Non si usa `hashCode` della stringa: e' definito dal linguaggio e oggi
     * torna sempre lo stesso, ma e' una promessa che non abbiamo fatto noi. Qui
     * la faccia di un giocatore non deve cambiare mai, nemmeno fra due versioni
     * di Android, quindi il conto lo facciamo per intero.
     */
    internal fun impronta(nome: String): Int {
        var h = -0x7ee3623b // 2166136261: FNV-1a
        nome.lowercase().forEach { c ->
            h = h xor c.code
            h *= 16777619
        }
        return h
    }

    /** Bit i-esimo dell'impronta, come numero fra 0 e [quanti]-1. */
    private fun scelta(impronta: Int, spostamento: Int, quanti: Int): Int =
        ((impronta ushr spostamento) and 0xFF) % quanti

    /** Quanto due colori si distinguono a occhio, contando che il verde pesa di piu'. */
    internal fun distanza(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db
    }

    /** Sotto questa distanza due colori affiancati sembrano lo stesso colore. */
    private const val ABBASTANZA_DIVERSI = 30_000

    /** All'iride basta meno: accanto ha il bianco dell'occhio. */
    private const val OCCHIO_DISTINTO = 10_000

    /**
     * Disegna la faccia: otto righe di otto colori, tutti opachi.
     *
     * I capelli non si prendono dove capita: si parte dal colore che dice
     * l'impronta e si va avanti finche' non e' abbastanza diverso dalla pelle.
     * Senza questo giro, prima o poi esce una testa castana su pelle castana,
     * che da lontano e' una macchia e non una faccia — cioe' esattamente quello
     * a cui serve.
     */
    fun disegna(nome: String): Array<IntArray> {
        val h = impronta(nome)
        val pelle = PELLE[scelta(h, 0, PELLE.size)]
        val capelli = lontanoDa(CAPELLI, scelta(h, 8, CAPELLI.size), pelle, ABBASTANZA_DIVERSI)
        // L'iride puo' stare piu' vicina alla pelle dei capelli: e' due caselle
        // accanto al bianco dell'occhio, che la stacca da solo. Ma non identica,
        // o l'occhio diventa mezzo occhio.
        val iride = lontanoDa(IRIDE, scelta(h, 16, IRIDE.size), pelle, OCCHIO_DISTINTO)
        val ombra = scurisci(pelle)

        // La frangia: quali delle sei caselle centrali della terza riga sono
        // capelli. E' da qui che viene quasi tutta la differenza fra due facce.
        val frangia = (h ushr 24) and 0x3F
        val barba = ((h ushr 22) and 0x1) == 1

        val griglia = Array(LATO) { IntArray(LATO) { pelle } }

        for (x in 0 until LATO) {
            griglia[0][x] = capelli
            griglia[1][x] = capelli
        }
        for (y in 2 until LATO - 1) {
            griglia[y][0] = capelli
            griglia[y][LATO - 1] = capelli
        }
        for (i in 0 until 6) {
            if ((frangia shr i) and 1 == 1) griglia[2][1 + i] = capelli
        }

        // Occhi: il bianco sta all'esterno e l'iride verso il naso, come sulla
        // faccia di Steve. Al contrario le facce guardano tutte in fuori.
        griglia[3][1] = BIANCO
        griglia[3][2] = iride
        griglia[3][5] = iride
        griglia[3][6] = BIANCO

        // Bocca.
        griglia[5][3] = ombra
        griglia[5][4] = ombra

        if (barba) {
            for (x in 1 until LATO - 1) griglia[6][x] = capelli
        }

        return griglia
    }

    /**
     * Il primo colore della tavolozza, a partire da quello scelto, che si
     * distingue abbastanza dalla pelle.
     *
     * Senza questo giro prima o poi esce una testa castana su pelle castana, o
     * un occhio scuro su pelle scura: da lontano sono macchie, cioe' esattamente
     * il contrario di quello a cui servono. Se nessuno va bene si prende quello
     * piu' lontano che c'e', che e' comunque meglio di uno a caso.
     */
    private fun lontanoDa(tavolozza: IntArray, partenza: Int, pelle: Int, minima: Int): Int {
        for (i in tavolozza.indices) {
            val candidato = tavolozza[(partenza + i) % tavolozza.size]
            if (distanza(candidato, pelle) >= minima) return candidato
        }
        return tavolozza.maxByOrNull { distanza(it, pelle) } ?: tavolozza[0]
    }

    private fun scurisci(colore: Int): Int {
        val r = (((colore shr 16) and 0xFF) * 55 / 100)
        val g = (((colore shr 8) and 0xFF) * 55 / 100)
        val b = ((colore and 0xFF) * 55 / 100)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
