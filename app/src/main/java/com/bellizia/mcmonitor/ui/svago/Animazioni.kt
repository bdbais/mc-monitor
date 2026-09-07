package com.bellizia.mcmonitor.ui.svago

/**
 * Le regole delle animazioni. Solo i conti: chi disegna sta altrove.
 *
 * Il principio da cui discende tutto il resto: **il modello si muove subito, il
 * disegno insegue.** Lo stato cambia nell'istante del tocco, e l'animazione è
 * soltanto l'immagine che recupera. Al contrario — animazione prima, stato dopo
 * — chi gioca in fretta perde le mosse, ed è il difetto peggiore che possa
 * avere un gioco a turni.
 *
 * Da lì segue che **ogni animazione è interrompibile**: se arriva la mossa
 * successiva, quella in corso salta alla fine e riparte da lì. Nessuna coda,
 * nessuna attesa.
 */
object Animazioni {

    /** Un passo normale. Corto: è il tocco che toglie gli scatti, non un effetto. */
    const val PASSO_MS = 90

    /** La picconata: il colpo va e torna. */
    const val COLPO_MS = 110

    /** I pezzi che schizzano quando un blocco cede. */
    const val ROTTURA_MS = 220

    /** Quanto dura al massimo una scivolata, per lunga che sia. */
    const val SCIVOLATA_MASSIMA_MS = 320

    /** Quanto dura ogni casella scivolata, prima del tetto. */
    private const val PER_CASELLA_MS = 45

    /**
     * Quanto deve durare uno spostamento di [caselle].
     *
     * Una casella sola è un passo. Molte caselle sono una scivolata, e deve
     * durare abbastanza da vedersi — se sei caselle sparissero in novanta
     * millisecondi sembrerebbe un teletrasporto e il ghiaccio smetterebbe di
     * spiegarsi da solo.
     *
     * Ma con un tetto: su un corridoio da venti caselle un'animazione
     * proporzionale durerebbe quasi un secondo, e in quel secondo chi gioca sta
     * già premendo la mossa dopo.
     */
    fun durata(caselle: Int): Int = when {
        caselle <= 0 -> 0
        caselle == 1 -> PASSO_MS
        else -> minOf(SCIVOLATA_MASSIMA_MS, caselle * PER_CASELLA_MS)
    }

    /**
     * A che punto è, da 0 a 1.
     *
     * Sempre limitato a 1: un fotogramma in ritardo — e ne arrivano, appena il
     * telefono ha da fare — darebbe un valore oltre la fine, e il giocatore
     * comparirebbe una casella oltre dove si è fermato.
     */
    fun progresso(trascorsoMs: Long, durataMs: Int): Float {
        if (durataMs <= 0) return 1f
        return (trascorsoMs.toFloat() / durataMs).coerceIn(0f, 1f)
    }

    /**
     * Parte veloce e si ferma piano.
     *
     * È come si muove una cosa che scivola: il ghiaccio non frena a scatti.
     */
    fun morbido(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x)
    }

    /** Posizione intermedia fra due caselle, in coordinate di casella. */
    fun fra(da: Int, a: Int, t: Float): Float = da + (a - da) * t

    /**
     * L'inclinazione del piccone durante il colpo, in gradi.
     *
     * Va e torna: a metà è al massimo, alla fine è di nuovo dritto. Se
     * restasse inclinato, due colpi di fila sembrerebbero uno solo.
     */
    fun inclinazione(t: Float, massimo: Float = 35f): Float {
        val x = t.coerceIn(0f, 1f)
        return massimo * kotlin.math.sin(x * Math.PI).toFloat()
    }

    /**
     * Dove sta un pezzo schizzato via, e quanto è ancora visibile.
     *
     * Va per la sua strada e cade: i pezzi che si allontanano in linea retta
     * sembrano una stella, quelli che ricadono sembrano detriti.
     */
    fun scheggia(t: Float, direzioneX: Float, direzioneY: Float): Triple<Float, Float, Float> {
        val x = t.coerceIn(0f, 1f)
        val dx = direzioneX * x
        val dy = direzioneY * x + 1.6f * x * x   // la gravità
        return Triple(dx, dy, 1f - x)
    }
}
