package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti delle animazioni.
 *
 * Le cose che devono reggere sono quelle che rompono un gioco a turni: che il
 * progresso non superi mai la fine, che un'animazione lunga non faccia
 * aspettare, e che una cosa che va e torna torni davvero.
 */
class AnimazioniTest {

    @Test
    fun `un passo dura poco, una scivolata di piu'`() {
        assertEquals(Animazioni.PASSO_MS, Animazioni.durata(1))
        assertTrue(
            "sei caselle devono durare piu' di un passo",
            Animazioni.durata(6) > Animazioni.durata(1)
        )
    }

    @Test
    fun `una scivolata lunghissima non fa aspettare`() {
        // Su un corridoio da venti caselle un'animazione proporzionale durerebbe
        // quasi un secondo, e in quel secondo chi gioca sta gia' premendo la
        // mossa dopo.
        assertTrue(Animazioni.durata(40) <= Animazioni.SCIVOLATA_MASSIMA_MS)
        assertTrue(Animazioni.durata(200) <= Animazioni.SCIVOLATA_MASSIMA_MS)
    }

    @Test
    fun `stare fermi non anima niente`() {
        assertEquals(0, Animazioni.durata(0))
        assertEquals(0, Animazioni.durata(-3))
    }

    @Test
    fun `piu' caselle non durano mai meno`() {
        val durate = (0..30).map { Animazioni.durata(it) }
        assertEquals("la durata deve crescere", durate.sorted(), durate)
    }

    // ------------------------------------------------------------ progresso

    @Test
    fun `il progresso non supera mai la fine`() {
        // Un fotogramma in ritardo -- e ne arrivano, appena il telefono ha da
        // fare -- darebbe un valore oltre 1, e il giocatore comparirebbe una
        // casella oltre dove si e' fermato.
        assertEquals(1f, Animazioni.progresso(9999, 100), 0.0001f)
        assertEquals(1f, Animazioni.progresso(101, 100), 0.0001f)
    }

    @Test
    fun `il progresso non va sotto zero`() {
        assertEquals(0f, Animazioni.progresso(-50, 100), 0.0001f)
    }

    @Test
    fun `durata zero e' subito finita`() {
        // Serve a non dividere per zero e a saltare l'animazione di chi non si
        // e' mosso.
        assertEquals(1f, Animazioni.progresso(0, 0), 0.0001f)
    }

    @Test
    fun `a meta' tempo si e' a meta' strada, o piu' avanti`() {
        // Parte veloce e frena: a meta' tempo deve aver gia' fatto piu' di
        // meta' strada, o non sembra una cosa che scivola.
        assertTrue(Animazioni.morbido(0.5f) > 0.5f)
        assertEquals(0f, Animazioni.morbido(0f), 0.0001f)
        assertEquals(1f, Animazioni.morbido(1f), 0.0001f)
    }

    @Test
    fun `il movimento comincia dove parte e finisce dove arriva`() {
        assertEquals(3f, Animazioni.fra(3, 9, 0f), 0.0001f)
        assertEquals(9f, Animazioni.fra(3, 9, 1f), 0.0001f)
        assertEquals(6f, Animazioni.fra(3, 9, 0.5f), 0.0001f)
    }

    @Test
    fun `si torna indietro anche andando all'indietro`() {
        assertEquals(9f, Animazioni.fra(9, 3, 0f), 0.0001f)
        assertEquals(3f, Animazioni.fra(9, 3, 1f), 0.0001f)
    }

    // -------------------------------------------------------------- il colpo

    @Test
    fun `il piccone torna dritto alla fine del colpo`() {
        // Se restasse inclinato, due colpi di fila sembrerebbero uno solo.
        assertEquals(0f, Animazioni.inclinazione(0f), 0.01f)
        assertEquals(0f, Animazioni.inclinazione(1f), 0.01f)
    }

    @Test
    fun `il colpo e' al massimo a meta'`() {
        val meta = Animazioni.inclinazione(0.5f)
        assertTrue("a meta' dev'essere il massimo: $meta", meta > Animazioni.inclinazione(0.2f))
        assertTrue(meta > Animazioni.inclinazione(0.8f))
    }

    // ----------------------------------------------------------- le schegge

    @Test
    fun `le schegge partono dal blocco e svaniscono`() {
        val (x0, y0, o0) = Animazioni.scheggia(0f, 1f, -1f)
        assertEquals(0f, x0, 0.0001f)
        assertEquals(0f, y0, 0.0001f)
        assertEquals("all'inizio devono essere piene", 1f, o0, 0.0001f)

        val (_, _, o1) = Animazioni.scheggia(1f, 1f, -1f)
        assertEquals("alla fine devono essere sparite", 0f, o1, 0.0001f)
    }

    @Test
    fun `le schegge ricadono`() {
        // Vanno per la loro strada e cadono: quelle che si allontanano in linea
        // retta sembrano una stella, queste sembrano detriti.
        val (_, yMeta, _) = Animazioni.scheggia(0.5f, 0f, -1f)
        val (_, yFine, _) = Animazioni.scheggia(1f, 0f, -1f)
        assertTrue("a meta' doveva essere ancora in salita: $yMeta", yMeta < 0f)
        assertTrue("alla fine doveva essere ricaduta: $yFine", yFine > yMeta)
    }

    @Test
    fun `niente esce dai limiti, per qualunque tempo`() {
        listOf(-1f, 0f, 0.3f, 1f, 5f).forEach { t ->
            assertTrue(Animazioni.morbido(t) in 0f..1f)
            assertTrue(Animazioni.inclinazione(t) >= 0f)
            assertTrue(Animazioni.scheggia(t, 1f, 1f).third in 0f..1f)
        }
    }
}
