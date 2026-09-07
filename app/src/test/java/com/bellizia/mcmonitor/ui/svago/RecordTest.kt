package com.bellizia.mcmonitor.ui.svago

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tabella dei record.
 *
 * Sono poche regole, ma sono quelle che si notano subito quando sono
 * sbagliate: un record che sparisce, un pareggio che scavalca chi c'era, tre
 * lettere che diventano due.
 */
class RecordTest {

    private fun r(iniziali: String, punti: Int) = Record.Riga(iniziali, punti)

    @Test
    fun `tre lettere, sempre`() {
        assertEquals("ABC", Record.iniziali("abc"))
        assertEquals("AB-", Record.iniziali("ab"))
        assertEquals("A--", Record.iniziali("a"))
        assertEquals("---", Record.iniziali(""))
        assertEquals("ABC", Record.iniziali("abcdefgh"))
    }

    @Test
    fun `la punteggiatura non entra nelle iniziali`() {
        // Vanno a finire in un formato con i due punti e la barra: un nome con
        // dentro quei segni si porterebbe via la riga accanto.
        assertEquals("AB-", Record.iniziali("a:b"))
        assertEquals("AB-", Record.iniziali("a|b"))
        assertEquals("---", Record.iniziali("!!!"))
    }

    @Test
    fun `chi non scrive niente tiene lo stesso il suo posto`() {
        val dopo = Record.inserisci(emptyList(), r(Record.iniziali(""), 500))
        assertEquals(1, dopo.size)
        assertEquals("---", dopo[0].iniziali)
    }

    // ------------------------------------------------------------ chi entra

    @Test
    fun `con la tabella vuota entra chiunque abbia fatto punti`() {
        assertTrue(Record.entra(emptyList(), 10))
    }

    @Test
    fun `lo zero non entra mai`() {
        // Chiedere le iniziali a chi ha perso subito non e' un premio.
        assertFalse(Record.entra(emptyList(), 0))
        assertFalse(Record.entra(emptyList(), -5))
    }

    @Test
    fun `con la tabella piena entra solo chi supera l'ultimo`() {
        val piena = (1..Record.QUANTI).map { r("A$it", it * 100) }
        assertFalse("100 e' esattamente l'ultimo, non lo supera", Record.entra(piena, 100))
        assertTrue(Record.entra(piena, 101))
    }

    // ------------------------------------------------------------- l'ordine

    @Test
    fun `la tabella resta in ordine e lunga cinque`() {
        var t = emptyList<Record.Riga>()
        listOf(300, 900, 100, 700, 500, 800).forEach { t = Record.inserisci(t, r("XXX", it)) }
        assertEquals(Record.QUANTI, t.size)
        assertEquals(listOf(900, 800, 700, 500, 300), t.map { it.punti })
    }

    @Test
    fun `a parita' di punti il record resta a chi l'ha fatto prima`() {
        // E' l'unica regola che non fa arrabbiare nessuno.
        val prima = r("AAA", 500)
        val dopo = Record.inserisci(listOf(prima), r("BBB", 500))
        assertEquals(listOf("AAA", "BBB"), dopo.map { it.iniziali })
    }

    @Test
    fun `si sa in che posizione si e' finiti`() {
        val nuovo = r("ZZZ", 600)
        val t = Record.inserisci(listOf(r("AAA", 900), r("BBB", 300)), nuovo)
        assertEquals(2, Record.posizione(t, nuovo))
    }

    // --------------------------------------------------- come si tiene da parte

    @Test
    fun `scritta e riletta e' la stessa tabella`() {
        val t = listOf(r("ABC", 1200), r("DEF", 900), r("---", 100))
        assertEquals(t, Record.leggi(Record.scrivi(t)))
    }

    @Test
    fun `una riga rotta non porta via le altre`() {
        // Una tabella che sparisce perche' una riga era storta e' un danno vero
        // per chi ci teneva.
        val letta = Record.leggi("ABC:1200|questa e' rotta|DEF:900|GHI:non un numero")
        assertEquals(listOf("ABC", "DEF"), letta.map { it.iniziali })
    }

    @Test
    fun `da niente esce niente, senza esplodere`() {
        assertTrue(Record.leggi("").isEmpty())
        assertTrue(Record.leggi("||||").isEmpty())
    }

    @Test
    fun `una tabella salvata troppo lunga viene tagliata`() {
        val lunga = (1..20).joinToString("|") { "A%02d:%d".format(it, it * 10) }
        assertEquals(Record.QUANTI, Record.leggi(lunga).size)
    }

    @Test
    fun `quello che si legge e' comunque in ordine`() {
        // Il file puo' venire da una versione che ordinava diversamente.
        assertEquals(
            listOf(900, 500, 100),
            Record.leggi("AAA:100|BBB:900|CCC:500").map { it.punti }
        )
    }

    // ------------------------------------------------------- come si mostra

    @Test
    fun `il tabellone segna la riga appena fatta`() {
        val nuovo = r("ZZZ", 600)
        val t = Record.inserisci(listOf(r("AAA", 900)), nuovo)
        val testo = Record.tabellone(t, nuovo)
        assertTrue("non evidenzia la riga nuova:\n$testo", testo.contains("▶"))
        assertEquals("evidenzia piu' di una riga", 1, testo.count { it == '▶' })
    }

    @Test
    fun `una tabella vuota dice qualcosa invece di restare bianca`() {
        assertTrue(Record.tabellone(emptyList()).isNotBlank())
    }
}
