package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le picconate: quante ne servono, e la crepa che le mostra.
 *
 * È la regola che rende il piccone importante *sempre* e non solo come chiave:
 * con l'attrezzo appena sufficiente lo stesso blocco si porta via tre volte
 * tanti turni. Se questo conto sbagliasse, un livello risolvibile in venti
 * mosse ne chiederebbe quaranta e nessuno saprebbe perché.
 */
class PicconateTest {

    private fun campo(vararg righe: String, inMano: Piccone? = null) =
        Livelli.disegna(*righe, inMano = inMano)

    // ------------------------------------------------------- quanti colpi

    @Test
    fun `col piccone appena sufficiente ci vuole piu' tempo`() {
        assertEquals(2, Motore.colpiNecessari(Blocco.PIETRA, Piccone.LEGNO))
        assertEquals(3, Motore.colpiNecessari(Blocco.FERRO, Piccone.PIETRA))
        assertEquals(5, Motore.colpiNecessari(Blocco.OSSIDIANA, Piccone.DIAMANTE))
    }

    @Test
    fun `ogni livello in piu' e' una picconata in meno`() {
        assertEquals(2, Motore.colpiNecessari(Blocco.PIETRA, Piccone.LEGNO))
        assertEquals(1, Motore.colpiNecessari(Blocco.PIETRA, Piccone.PIETRA))
        assertEquals(1, Motore.colpiNecessari(Blocco.PIETRA, Piccone.DIAMANTE))
        assertEquals(3, Motore.colpiNecessari(Blocco.FERRO, Piccone.PIETRA))
        assertEquals(2, Motore.colpiNecessari(Blocco.FERRO, Piccone.FERRO))
        assertEquals(1, Motore.colpiNecessari(Blocco.FERRO, Piccone.DIAMANTE))
    }

    @Test
    fun `non si scende mai sotto una picconata`() {
        Blocco.entries.filter { it.durezza != null }.forEach { b ->
            Piccone.entries.forEach { p ->
                val n = Motore.colpiNecessari(b, p)
                if (n != Int.MAX_VALUE) assertTrue("$b con $p: $n colpi", n >= 1)
            }
        }
    }

    @Test
    fun `la terra si toglie in un colpo, anche a mani nude`() {
        assertEquals(1, Motore.colpiNecessari(Blocco.TERRA, null))
    }

    @Test
    fun `col piccone insufficiente non si rompe mai`() {
        assertEquals(Int.MAX_VALUE, Motore.colpiNecessari(Blocco.FERRO, Piccone.LEGNO))
        assertEquals(Int.MAX_VALUE, Motore.colpiNecessari(Blocco.PIETRA, null))
        assertEquals(Int.MAX_VALUE, Motore.colpiNecessari(Blocco.GHIAIA, Piccone.DIAMANTE))
    }

    // -------------------------------------------------------- come si rompe

    @Test
    fun `la pietra col legno regge una picconata e cade alla seconda`() {
        var stato = campo("@p.", inMano = Piccone.LEGNO)

        val primo = Motore.muovi(stato, Direzione.DESTRA)
        assertEquals(Esito.CREPATO, primo.esito)
        assertNotNull("non doveva cadere al primo colpo", primo.stato.bloccoIn(Punto(1, 0)))
        assertEquals(1, primo.stato.danni[Punto(1, 0)])
        stato = primo.stato

        val secondo = Motore.muovi(stato, Direzione.DESTRA)
        assertEquals(Esito.SCAVATO, secondo.esito)
        assertNull(secondo.stato.bloccoIn(Punto(1, 0)))
    }

    @Test
    fun `ogni picconata costa un colpo di piccone, anche quelle che non rompono`() {
        // E' il motivo per cui l'attrezzo giusto vale: con quello sbagliato ma
        // sufficiente si consuma il triplo per lo stesso blocco.
        val prima = campo("@f.", inMano = Piccone.PIETRA)
        var stato = prima
        repeat(3) { stato = Motore.muovi(stato, Direzione.DESTRA).stato }
        assertNull("dopo tre colpi il ferro doveva cadere", stato.bloccoIn(Punto(1, 0)))
        assertEquals(Piccone.PIETRA.durabilita - 3, stato.inMano!!.colpiRimasti)
    }

    @Test
    fun `col piccone migliore lo stesso blocco costa meno`() {
        val conFerro = Motore.muovi(campo("@f.", inMano = Piccone.DIAMANTE), Direzione.DESTRA)
        assertEquals("col diamante il ferro cade subito", Esito.SCAVATO, conFerro.esito)
        assertEquals(Piccone.DIAMANTE.durabilita - 1, conFerro.stato.inMano!!.colpiRimasti)
    }

    @Test
    fun `i danni spariscono col blocco`() {
        var stato = campo("@f.", inMano = Piccone.PIETRA)
        repeat(3) { stato = Motore.muovi(stato, Direzione.DESTRA).stato }
        assertTrue("i danni di un blocco caduto restano in giro", stato.danni.isEmpty())
    }

    @Test
    fun `le crepe restano se ci si allontana`() {
        // In Minecraft la crepa si richiude appena stacchi il tasto. Qui no: il
        // tempo non scorre, e azzerarle renderebbe «vado a prendere il piccone
        // giusto e torno» una punizione invece che un piano.
        var stato = campo("@f...", inMano = Piccone.PIETRA)
        stato = Motore.muovi(stato, Direzione.DESTRA).stato   // una picconata
        assertEquals(1, stato.danni[Punto(1, 0)])
        stato = Motore.muovi(stato, Direzione.SINISTRA).stato // se ne va (non c'e' niente a sinistra)
        assertEquals("la crepa e' sparita", 1, stato.danni[Punto(1, 0)])
    }

    @Test
    fun `una picconata inutile non lascia crepe e non consuma niente`() {
        // Piccone troppo debole: la mossa non deve nemmeno contare.
        val m = Motore.muovi(campo("@f.", inMano = Piccone.LEGNO), Direzione.DESTRA)
        assertEquals(Esito.NULLA, m.esito)
        assertTrue(m.stato.danni.isEmpty())
        assertEquals(Piccone.LEGNO.durabilita, m.stato.inMano!!.colpiRimasti)
    }

    // ---------------------------------------------------------- la crepa

    @Test
    fun `un blocco intatto non ha crepe`() {
        assertEquals(0, Motore.crepa(campo("@p.", inMano = Piccone.LEGNO), Punto(1, 0)))
    }

    @Test
    fun `la crepa cresce con le picconate`() {
        var stato = campo("@f.", inMano = Piccone.PIETRA)   // tre colpi
        val stadi = mutableListOf(Motore.crepa(stato, Punto(1, 0)))
        repeat(2) {
            stato = Motore.muovi(stato, Direzione.DESTRA).stato
            stadi += Motore.crepa(stato, Punto(1, 0))
        }
        assertEquals("gli stadi devono crescere: $stadi", stadi.sorted(), stadi)
        assertTrue("dopo due colpi su tre doveva essere ben crepato", stadi.last() >= 4)
    }

    @Test
    fun `la crepa resta dentro gli stadi che esistono`() {
        var stato = campo("@" + "f".repeat(1), inMano = Piccone.PIETRA)
        repeat(5) { stato = Motore.muovi(stato, Direzione.DESTRA).stato }
        (0 until stato.larghezza).forEach { x ->
            val c = Motore.crepa(stato, Punto(x, 0))
            assertTrue("stadio fuori scala: $c", c in 0 until Motore.STADI)
        }
    }

    @Test
    fun `senza blocco non c'e' crepa da disegnare`() {
        assertEquals(0, Motore.crepa(campo("@.."), Punto(1, 0)))
    }

    // ------------------------------------------------- i livelli reggono ancora

    @Test
    fun `il primo livello si finisce lo stesso, con una picconata in piu'`() {
        // La pietra col piccone di legno adesso ne vuole due: la soluzione di
        // prima aveva una mossa in meno. Se questa prova fallisse vorrebbe dire
        // che il livello e' diventato irrisolvibile, non solo piu' lungo.
        var stato = Livelli.TUTTI[0].crea()
        val mosse = listOf(
            Direzione.DESTRA, Direzione.DESTRA, Direzione.DESTRA, Direzione.DESTRA,
            Direzione.GIU, Direzione.GIU, Direzione.SINISTRA, Direzione.DESTRA,
            Direzione.DESTRA, Direzione.DESTRA,
            Direzione.DESTRA, Direzione.DESTRA, Direzione.DESTRA, Direzione.DESTRA,
        )
        mosse.forEach { stato = Motore.muovi(stato, it).stato }
        assertTrue("il primo livello non si chiude piu'", stato.vinto)
    }

    @Test
    fun `il secondo livello non cambia, la terra e sempre un colpo`() {
        var stato = Livelli.TUTTI[1].crea()
        listOf(
            Direzione.GIU, Direzione.DESTRA, Direzione.DESTRA,
            Direzione.DESTRA, Direzione.GIU, Direzione.SINISTRA
        ).forEach { stato = Motore.muovi(stato, it).stato }
        assertTrue(stato.vinto)
    }
}
