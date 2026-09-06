package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le regole del campo.
 *
 * Un rompicapo si prova qui e non a schermo: quello che conta e' che le stesse
 * mosse diano sempre lo stesso esito, e che una situazione senza uscita sia
 * senza uscita davvero. Se queste due cose non reggono, un livello generato
 * puo' risultare impossibile senza che nessuno se ne accorga fino a quando ci
 * sbatte dentro qualcuno.
 */
class MotoreTest {

    /**
     * Costruisce un campo da un disegno, che e' l'unico modo di leggere una
     * prova di un gioco a griglia senza contare le coordinate a mano.
     *
     * `.` vuoto · `#` ossidiana · `t` terra · `p` pietra · `f` ferro
     * `g` ghiaia · `_` ghiaccio · `U` uscita · `@` giocatore
     * `1`..`4` un piccone per terra (legno, pietra, ferro, diamante)
     */
    /**
     * Il campo si legge dal disegno con lo stesso lettore che usano i livelli
     * veri: due lettori diversi vorrebbero dire provare un campo e giocarne un
     * altro.
     */
    private fun campo(vararg righe: String, inMano: Piccone? = null): Stato =
        Livelli.disegna(*righe, inMano = inMano)

    // ------------------------------------------------------- il movimento

    @Test
    fun `si cammina in una casella libera`() {
        val m = Motore.muovi(campo("@.."), Direzione.DESTRA)
        assertEquals(Esito.MOSSO, m.esito)
        assertEquals(Punto(1, 0), m.stato.giocatore)
    }

    @Test
    fun `fuori dal campo non si va`() {
        val m = Motore.muovi(campo("@.."), Direzione.SINISTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertEquals(Punto(0, 0), m.stato.giocatore)
    }

    @Test
    fun `l'ossidiana e' il bordo del mondo`() {
        val m = Motore.muovi(campo("@#."), Direzione.DESTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertEquals(Punto(0, 0), m.stato.giocatore)
    }

    // ---------------------------------------------------------- il ghiaccio

    @Test
    fun `sul ghiaccio non ci si ferma fino a sbattere`() {
        // Il corridoio non e' una fila di caselle: e' una freccia.
        val m = Motore.muovi(campo("@____#"), Direzione.DESTRA)
        assertEquals(Punto(4, 0), m.stato.giocatore)
    }

    @Test
    fun `si esce dal ghiaccio appena il suolo torna normale`() {
        val m = Motore.muovi(campo("@__..#"), Direzione.DESTRA)
        assertEquals("doveva fermarsi sulla prima casella normale", Punto(3, 0), m.stato.giocatore)
    }

    @Test
    fun `si scivola fino al bordo del campo`() {
        val m = Motore.muovi(campo("@____"), Direzione.DESTRA)
        assertEquals(Punto(4, 0), m.stato.giocatore)
    }

    @Test
    fun `anche i blocchi spinti scivolano`() {
        // E' il cuore del bioma: non si posiziona un blocco, si posiziona
        // quello che lo fermera'.
        val m = Motore.muovi(campo("@g___#"), Direzione.DESTRA)
        assertEquals(Esito.MOSSO, m.esito)
        assertEquals(Blocco.GHIAIA, m.stato.bloccoIn(Punto(4, 0)))
        assertNull(m.stato.bloccoIn(Punto(1, 0)))
        assertEquals("il giocatore entra dove stava il blocco", Punto(1, 0), m.stato.giocatore)
    }

    @Test
    fun `un blocco si ferma contro un altro blocco`() {
        val m = Motore.muovi(campo("@g__g#"), Direzione.DESTRA)
        assertEquals(Blocco.GHIAIA, m.stato.bloccoIn(Punto(3, 0)))
    }

    @Test
    fun `un blocco con un muro subito dietro non si muove`() {
        val m = Motore.muovi(campo("@g#"), Direzione.DESTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertEquals(Blocco.GHIAIA, m.stato.bloccoIn(Punto(1, 0)))
        assertEquals(Punto(0, 0), m.stato.giocatore)
    }

    @Test
    fun `due specchi di ghiaccio non fanno girare a vuoto`() {
        // Un campo tutto ghiaccio senza ostacoli: senza un tetto ai passi, il
        // ciclo non finirebbe mai e il gioco si pianterebbe.
        val m = Motore.muovi(campo("@____"), Direzione.DESTRA)
        assertTrue(m.stato.dentro(m.stato.giocatore))
    }

    // ------------------------------------------------------------- lo scavo

    @Test
    fun `la terra si scava a mani nude e non costa niente`() {
        val m = Motore.muovi(campo("@t."), Direzione.DESTRA)
        assertEquals(Esito.SCAVATO, m.esito)
        assertNull(m.stato.bloccoIn(Punto(1, 0)))
        assertEquals("scavare non fa avanzare", Punto(0, 0), m.stato.giocatore)
    }

    @Test
    fun `la pietra senza piccone non si tocca`() {
        val m = Motore.muovi(campo("@p."), Direzione.DESTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertEquals(Blocco.PIETRA, m.stato.bloccoIn(Punto(1, 0)))
    }

    @Test
    fun `il piccone di legno non apre il ferro`() {
        // E' il lucchetto: il livello diventa «quale piccone mi serve e dov'e'».
        val m = Motore.muovi(campo("@f.", inMano = Piccone.LEGNO), Direzione.DESTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertEquals(Blocco.FERRO, m.stato.bloccoIn(Punto(1, 0)))
    }

    @Test
    fun `il piccone giusto apre e consuma un colpo`() {
        val prima = campo("@p.", inMano = Piccone.LEGNO)
        val m = Motore.muovi(prima, Direzione.DESTRA)
        assertEquals(Esito.SCAVATO, m.esito)
        assertNull(m.stato.bloccoIn(Punto(1, 0)))
        assertEquals(Piccone.LEGNO.durabilita - 1, m.stato.inMano!!.colpiRimasti)
    }

    @Test
    fun `un piccone finito sparisce dalle mani`() {
        // Tenerlo scarico in mano sarebbe come non averlo, detto peggio.
        //
        // Un colpo per blocco, e fra un colpo e l'altro si avanza di una
        // casella: per consumare dodici colpi servono dodici blocchi, non
        // dodici mosse.
        val quanti = Piccone.LEGNO.durabilita
        var stato = campo("@" + "p".repeat(quanti + 1), inMano = Piccone.LEGNO)
        var scavi = 0
        repeat(quanti * 3) {
            val m = Motore.muovi(stato, Direzione.DESTRA)
            if (m.esito == Esito.SCAVATO) scavi++
            stato = m.stato
        }
        assertEquals("ha scavato piu' colpi di quanti ne aveva", quanti, scavi)
        assertNull("il piccone doveva essere finito", stato.inMano)
        assertEquals(
            "senza piccone non si scava piu'",
            Esito.NULLA, Motore.muovi(stato, Direzione.DESTRA).esito
        )
        assertEquals(
            "l'ultimo blocco doveva restare in piedi",
            Blocco.PIETRA, stato.bloccoIn(Punto(quanti + 1, 0))
        )
    }

    @Test
    fun `l'ossidiana cede solo al diamante`() {
        assertEquals(
            Esito.NULLA,
            Motore.muovi(campo("@#.", inMano = Piccone.FERRO), Direzione.DESTRA).esito
        )
        assertEquals(
            Esito.SCAVATO,
            Motore.muovi(campo("@#.", inMano = Piccone.DIAMANTE), Direzione.DESTRA).esito
        )
    }

    // ---------------------------------------------------------- i picconi

    @Test
    fun `raccogliere un piccone fa cadere quello che si aveva`() {
        // E' la regola su cui si regge tutto: i picconi sono oggetti da
        // posizionare, non un inventario che cresce.
        val m = Motore.muovi(campo("@3.", inMano = Piccone.LEGNO), Direzione.DESTRA)
        assertEquals(Piccone.FERRO, m.stato.inMano!!.piccone)
        assertEquals(
            "il vecchio doveva restare dove l'ho raccolto",
            Piccone.LEGNO, m.stato.picconi[Punto(1, 0)]
        )
    }

    @Test
    fun `chi non aveva niente in mano non lascia niente per terra`() {
        val m = Motore.muovi(campo("@3."), Direzione.DESTRA)
        assertEquals(Piccone.FERRO, m.stato.inMano!!.piccone)
        assertTrue(m.stato.picconi.isEmpty())
    }

    @Test
    fun `il piccone raccolto e' pieno`() {
        val m = Motore.muovi(campo("@2."), Direzione.DESTRA)
        assertEquals(Piccone.PIETRA.durabilita, m.stato.inMano!!.colpiRimasti)
    }

    @Test
    fun `sul ghiaccio il piccone cade dove ci si ferma, non dove si e' entrati`() {
        // Se cadesse alle spalle finirebbe a mezzo corridoio di distanza e non
        // lo ritroverebbe piu' nessuno.
        val m = Motore.muovi(campo("@___3#", inMano = Piccone.LEGNO), Direzione.DESTRA)
        assertEquals(Punto(4, 0), m.stato.giocatore)
        assertEquals(Piccone.LEGNO, m.stato.picconi[Punto(4, 0)])
        assertEquals(Piccone.FERRO, m.stato.inMano!!.piccone)
    }

    // ------------------------------------------------------------ l'uscita

    @Test
    fun `arrivare all'uscita vince`() {
        val m = Motore.muovi(campo("@U"), Direzione.DESTRA)
        assertTrue(m.stato.vinto)
    }

    @Test
    fun `si vince anche arrivandoci scivolando`() {
        val m = Motore.muovi(campo("@___U"), Direzione.DESTRA)
        assertTrue("scivolare fin dentro l'uscita conta", m.stato.vinto)
    }

    @Test
    fun `a partita vinta non si muove piu' niente`() {
        val vinta = Motore.muovi(campo("@U."), Direzione.DESTRA).stato
        val dopo = Motore.muovi(vinta, Direzione.DESTRA)
        assertEquals(Esito.NULLA, dopo.esito)
        assertEquals(vinta.giocatore, dopo.stato.giocatore)
    }

    // ------------------------------------------------------- determinismo

    @Test
    fun `le stesse mosse danno sempre lo stesso risultato`() {
        // Senza questo non esiste ne' un «annulla», ne' un risolutore, ne' una
        // classifica che voglia dire qualcosa.
        val partenza = campo(
            "@..g_#",
            ".t.p.#",
            "..3..U",
            inMano = Piccone.LEGNO
        )
        val mosse = listOf(
            Direzione.GIU, Direzione.DESTRA, Direzione.DESTRA,
            Direzione.SU, Direzione.DESTRA, Direzione.GIU, Direzione.GIU
        )
        fun gioca(): Stato {
            var s = partenza
            mosse.forEach { s = Motore.muovi(s, it).stato }
            return s
        }
        assertEquals(gioca(), gioca())
    }

    @Test
    fun `lo stato di partenza non viene toccato da una mossa`() {
        // Serve all'«annulla» e al risolutore: tenere il vecchio stato deve
        // bastare a tornare indietro.
        val prima = campo("@t.", inMano = Piccone.LEGNO)
        Motore.muovi(prima, Direzione.DESTRA)
        assertNotNull("il blocco e' sparito dallo stato originale", prima.bloccoIn(Punto(1, 0)))
        assertEquals(Punto(0, 0), prima.giocatore)
    }
}
