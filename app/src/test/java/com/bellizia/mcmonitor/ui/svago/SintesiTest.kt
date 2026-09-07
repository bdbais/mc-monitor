package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * I suoni fatti a mano.
 *
 * Sono numeri, e i numeri si provano. Le cose che contano sono tre, e sono
 * tutte cose che si sentono subito quando sono sbagliate: che l'onda non
 * schiocchi, che non distorca, e che due suoni diversi siano davvero diversi.
 */
class SintesiTest {

    private val tutti: List<Pair<String, ShortArray>> = listOf(
        "picconata" to Sintesi.picconata(1),
        "rotto" to Sintesi.rotto(),
        "scivolata" to Sintesi.scivolata(4),
        "raccolto" to Sintesi.raccolto(),
        "vinto" to Sintesi.vinto(),
        "rifiutato" to Sintesi.rifiutato(),
        "persa" to Sintesi.persa(),
    )

    @Test
    fun `nessun suono finisce di colpo`() {
        // Un'onda che si interrompe a meta' ampiezza fa uno schiocco secco, ed
        // e' quello schiocco a far spegnere l'audio a chi gioca -- molto piu'
        // del suono in se'.
        tutti.forEach { (nome, onda) ->
            val coda = onda.takeLast(20).map { abs(it.toInt()) }.maxOrNull() ?: 0
            assertTrue("$nome finisce a $coda invece che vicino a zero", coda < 400)
        }
    }

    @Test
    fun `nessun suono comincia di colpo`() {
        tutti.forEach { (nome, onda) ->
            assertEquals("$nome parte da un valore diverso da zero", 0, onda.first().toInt())
        }
    }

    @Test
    fun `niente distorsione`() {
        // Toccare il limite del formato vuol dire gracchiare. Si resta lontani.
        tutti.forEach { (nome, onda) ->
            val picco = onda.maxOf { abs(it.toInt()) }
            assertTrue("$nome arriva a $picco, troppo vicino al limite", picco < 32000)
            assertTrue("$nome e' praticamente muto ($picco)", picco > 2000)
        }
    }

    @Test
    fun `nessun suono e' vuoto o infinito`() {
        tutti.forEach { (nome, onda) ->
            val ms = onda.size * 1000 / Sintesi.CAMPIONI_AL_SECONDO
            assertTrue("$nome dura $ms ms", ms in 30..900)
        }
    }

    @Test
    fun `lo stesso suono e' sempre lo stesso`() {
        // Con un generatore casuale di sistema il rumore cambierebbe a ogni
        // esecuzione, e questa prova direbbe si' oggi e no domani.
        assertTrue(Sintesi.rotto().contentEquals(Sintesi.rotto()))
        assertTrue(Sintesi.scivolata(3).contentEquals(Sintesi.scivolata(3)))
    }

    @Test
    fun `blocchi diversi suonano diversi`() {
        // Il tono dice che stai colpendo qualcos'altro, e lo dice prima che la
        // crepa si veda.
        assertFalse(Sintesi.picconata(0).contentEquals(Sintesi.picconata(4)))
    }

    @Test
    fun `una scivolata lunga dura piu' di una corta`() {
        // E' l'unica informazione che a schermo si perde: dal disegno finale
        // due caselle e sei non si distinguono.
        assertTrue(Sintesi.scivolata(6).size > Sintesi.scivolata(1).size)
    }

    @Test
    fun `una scivolata lunghissima non dura all'infinito`() {
        // Un corridoio di ghiaccio da venti caselle non deve produrre un
        // fruscio di due secondi che copre la mossa successiva.
        val ms = Sintesi.scivolata(40).size * 1000 / Sintesi.CAMPIONI_AL_SECONDO
        assertTrue("una scivolata lunga dura $ms ms", ms <= 450)
    }

    @Test
    fun `i suoni sono tutti diversi fra loro`() {
        val impronte = tutti.map { (_, onda) -> onda.toList().hashCode() }
        assertEquals("due suoni sono identici", impronte.size, impronte.toSet().size)
    }

    @Test
    fun `un suono di durata zero non fa esplodere niente`() {
        // Zero caselle scivolate: capita, e non deve produrre un vettore vuoto
        // che poi qualcuno prova a suonare.
        assertTrue(Sintesi.scivolata(0).size >= 2)
    }

    private fun assertFalse(condizione: Boolean) = assertTrue(!condizione)
}
