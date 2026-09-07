package com.bellizia.mcmonitor.ui.svago

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * I suoni, fatti a mano un campione alla volta.
 *
 * Non ci sono file audio nell'app, e non è un vezzo: i suoni di Minecraft sono
 * di Mojang e la sua musica non è nemmeno sua — è di chi l'ha scritta, con
 * licenze a parte. Ridistribuirli sarebbe la cosa per cui i progetti amatoriali
 * vengono chiusi.
 *
 * Quindi si sintetizzano. Onde quadre e rumore, che è quello che facevano le
 * macchine a otto bit, e che in un gioco fatto di rettangoli suona giusto. Il
 * peso aggiunto all'archivio è **zero**.
 *
 * Qui dentro non c'è niente di Android: si producono numeri. Chi li suona sta
 * altrove, e questo si può provare senza un altoparlante.
 */
object Sintesi {

    /** Bastano per delle onde quadre, e occupano metà di 44100. */
    const val CAMPIONI_AL_SECONDO = 22050

    private const val MASSIMO = 12000 // ampiezza: lontana dal limite, niente distorsione

    /**
     * Rumore ripetibile.
     *
     * Un generatore casuale di sistema darebbe un suono diverso a ogni
     * esecuzione, e soprattutto renderebbe impossibile provare che l'onda non
     * schiocca: la stessa prova direbbe sì oggi e no domani.
     */
    private class Rumore(private var seme: Int = 1) {
        fun prossimo(): Double {
            seme = seme * 1103515245 + 12345
            return ((seme ushr 16) and 0x7FFF) / 16383.5 - 1.0
        }
    }

    /**
     * L'inviluppo: come cresce e come muore il volume.
     *
     * **Deve arrivare a zero.** Un'onda che finisce a metà ampiezza fa uno
     * schiocco secco, e quello schiocco è la cosa che fa spegnere l'audio a chi
     * gioca — molto più del suono in sé.
     */
    private fun inviluppo(i: Int, totale: Int, attacco: Double = 0.02): Double {
        if (totale <= 1) return 0.0
        val t = i.toDouble() / (totale - 1)
        val salita = if (attacco <= 0.0) 1.0 else min(1.0, t / attacco)
        val discesa = (1.0 - t) * (1.0 - t)
        return salita * discesa
    }

    private fun onda(campioni: Int, corpo: (Int) -> Double): ShortArray {
        val fuori = ShortArray(campioni)
        for (i in 0 until campioni) {
            val v = corpo(i) * inviluppo(i, campioni)
            fuori[i] = (v.coerceIn(-1.0, 1.0) * MASSIMO).toInt().toShort()
        }
        return fuori
    }

    private fun campioniPer(millisecondi: Int): Int =
        (CAMPIONI_AL_SECONDO * millisecondi / 1000).coerceAtLeast(2)

    /** Onda quadra: due valori soli, come le macchine di allora. */
    private fun quadra(i: Int, hz: Double): Double {
        val periodo = CAMPIONI_AL_SECONDO / hz
        return if ((i % periodo) < periodo / 2) 1.0 else -1.0
    }

    // ------------------------------------------------------------- i suoni

    /**
     * La picconata: un colpo secco, più grave quanto più il blocco è duro.
     *
     * La differenza di tono non è decorazione: dice che stai colpendo qualcosa
     * di diverso, e lo dice prima che la crepa sia visibile.
     */
    fun picconata(durezza: Int): ShortArray {
        val r = Rumore(7 + durezza)
        val hz = 220.0 - durezza * 30
        return onda(campioniPer(70)) { i ->
            0.55 * quadra(i, hz) + 0.45 * r.prossimo()
        }
    }

    /** Il blocco che cede: più lungo, e scende. */
    fun rotto(): ShortArray {
        val r = Rumore(31)
        val n = campioniPer(200)
        return onda(n) { i ->
            val t = i.toDouble() / n
            0.5 * quadra(i, 160.0 - 90 * t) + 0.5 * r.prossimo()
        }
    }

    /**
     * Lo scivolamento: dura quanto la strada fatta.
     *
     * È l'unico suono che porta un'informazione che a schermo si perde — sei
     * caselle o due, dal disegno finale non si distingue.
     */
    fun scivolata(caselle: Int): ShortArray {
        val r = Rumore(97)
        val n = campioniPer((40 + 28 * caselle).coerceAtMost(420))
        return onda(n) { i ->
            val t = i.toDouble() / n
            0.7 * r.prossimo() * (0.4 + 0.6 * sin(PI * t))
        }
    }

    /** Raccolto qualcosa: due note che salgono. */
    fun raccolto(): ShortArray {
        val n = campioniPer(140)
        return onda(n) { i ->
            quadra(i, if (i < n / 2) 523.0 else 784.0)
        }
    }

    /** Fine del livello: quattro note in salita, e via. */
    fun vinto(): ShortArray {
        val note = doubleArrayOf(523.0, 659.0, 784.0, 1046.0)
        val n = campioniPer(520)
        val perNota = n / note.size
        return onda(n) { i ->
            quadra(i, note[min(note.size - 1, i / perNota)])
        }
    }

    /**
     * Mossa impossibile: un colpo sordo e corto.
     *
     * Serve a distinguere «non ho toccato lo schermo abbastanza» da «questa
     * mossa non si può fare»: senza, sembrano la stessa cosa e si dà la colpa
     * ai comandi.
     */
    fun rifiutato(): ShortArray = onda(campioniPer(60)) { i -> 0.6 * quadra(i, 98.0) }

    /** Una vita persa. */
    fun persa(): ShortArray {
        val note = doubleArrayOf(392.0, 330.0, 262.0, 196.0)
        val n = campioniPer(600)
        val perNota = n / note.size
        return onda(n) { i ->
            quadra(i, note[min(note.size - 1, i / perNota)])
        }
    }
}
