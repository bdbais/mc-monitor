package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Che i due campi si possano finire davvero.
 *
 * È l'unica prova che conta per un rompicapo, e va scritta prima di disegnare
 * un pixel: un livello impossibile non si vede guardandolo — sembra un livello
 * normale finché qualcuno non ci sbatte dentro per dieci minuti. Qui la
 * soluzione è scritta, si gioca, e alla fine si deve aver vinto.
 */
class LivelliTest {

    private val D = Direzione.DESTRA
    private val S = Direzione.SINISTRA
    private val G = Direzione.GIU
    private val U = Direzione.SU

    private fun gioca(campo: Livelli.Campo, mosse: List<Direzione>): Stato {
        var stato = campo.crea()
        mosse.forEach { stato = Motore.muovi(stato, it).stato }
        return stato
    }

    @Test
    fun `il primo si finisce`() {
        // Scava la terra a mani nude, gira sotto, raccoglie il piccone, apre la
        // pietra e arriva all'uscita.
        // La pietra col piccone di legno vuole due picconate, non una: una
        // mossa in piu' rispetto a prima, ed e' esattamente quello che la
        // regola nuova deve costare.
        val fine = gioca(
            Livelli.TUTTI[0],
            listOf(D, D, D, D, G, G, S, D, D, D, D, D, D, D)
        )
        assertTrue("il primo campo non si chiude", fine.vinto)
    }

    @Test
    fun `il secondo si finisce`() {
        // Scende sul ghiaccio e si ferma contro il muro, scivola fino alla
        // terra, la scava, riparte e scivola in fondo, poi scende ed entra.
        val fine = gioca(
            Livelli.TUTTI[1],
            listOf(G, D, D, D, G, S)
        )
        assertTrue("il secondo campo non si chiude", fine.vinto)
    }

    @Test
    fun `il primo non si vince camminando a caso`() {
        // Se bastasse andare a destra non sarebbe un rompicapo, sarebbe un
        // corridoio.
        val fine = gioca(Livelli.TUTTI[0], List(20) { D })
        assertFalse(fine.vinto)
    }

    @Test
    fun `il secondo non si vince camminando a caso`() {
        assertFalse(gioca(Livelli.TUTTI[1], List(20) { D }).vinto)
    }

    @Test
    fun `nel secondo si scivola davvero`() {
        // Se il ghiaccio non scivolasse il livello si potrebbe finire lo
        // stesso, e non insegnerebbe piu' niente: la prova deve accorgersi che
        // una mossa sola copre piu' caselle.
        var stato = Livelli.TUTTI[1].crea()
        stato = Motore.muovi(stato, G).stato
        val partenza = stato.giocatore
        stato = Motore.muovi(stato, D).stato
        assertTrue(
            "una mossa a destra doveva coprire piu' di una casella",
            stato.giocatore.x - partenza.x > 1
        )
    }

    @Test
    fun `ogni campo ha un'uscita e un giocatore`() {
        Livelli.TUTTI.forEach { campo ->
            val s = campo.crea()
            assertTrue("${campo.titolo}: nessuna uscita", s.suolo.contains(Suolo.USCITA))
            assertTrue("${campo.titolo}: il giocatore e' fuori dal campo", s.dentro(s.giocatore))
            assertEquals(
                "${campo.titolo}: la griglia non torna",
                s.larghezza * s.altezza, s.suolo.size
            )
        }
    }

    @Test
    fun `il giocatore non parte dentro un muro`() {
        Livelli.TUTTI.forEach { campo ->
            val s = campo.crea()
            assertTrue("${campo.titolo}: si parte dentro un blocco", s.libera(s.giocatore))
        }
    }

    @Test
    fun `ogni campo dice cosa insegna`() {
        Livelli.TUTTI.forEach {
            assertTrue("${it.titolo} senza titolo", it.titolo.isNotBlank())
            assertTrue("${it.titolo} senza suggerimento", it.suggerimento.length > 20)
        }
    }

    // -------------------------------------------------------- il lettore

    @Test
    fun `la maiuscola vuol dire ghiaccio sotto`() {
        val s = Livelli.disegna("@T.")
        assertEquals(Blocco.TERRA, s.bloccoIn(Punto(1, 0)))
        assertEquals(Suolo.GHIACCIO, s.suoloDi(Punto(1, 0)))
        assertEquals(Suolo.NORMALE, s.suoloDi(Punto(2, 0)))
    }

    @Test
    fun `una riga di lunghezza sbagliata viene rifiutata subito`() {
        // Meglio un errore quando si scrive il livello che un campo storto in
        // mano a chi ci gioca.
        runCatching { Livelli.disegna("@..", "#") }.fold(
            onSuccess = { throw AssertionError("ha accettato una griglia storta") },
            onFailure = { assertTrue(it is IllegalArgumentException) }
        )
    }
}
