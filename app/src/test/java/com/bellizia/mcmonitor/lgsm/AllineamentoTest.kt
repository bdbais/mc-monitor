package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Portare su un altro server chi è già in elenco.
 *
 * La regola che governa tutto: **si aggiunge soltanto**. Togliere qualcuno da un
 * elenco perché manca dall'altro sarebbe una decisione che nessuno ha preso.
 */
class AllineamentoTest {

    private fun p(vararg nomi: String) = nomi.map { PlayerEntry(name = it) }

    // -------------------------------------------------------- la differenza

    @Test
    fun `dice chi manca di la'`() {
        val qui = Liste(ammessi = p("Anna", "Bruno"), bannati = p("Vandalo"))
        val la = Liste(ammessi = p("Anna"), bannati = emptyList())
        val d = Allineamento.differenza(qui, la)
        assertEquals(listOf("Bruno"), d.daAmmettere.map { it.name })
        assertEquals(listOf("Vandalo"), d.daBannare.map { it.name })
        assertEquals(2, d.quanti)
    }

    @Test
    fun `le maiuscole non contano`() {
        // Minecraft le conserva ma non le distingue, e due elenchi scritti in
        // momenti diversi possono avere lo stesso giocatore in due modi.
        val qui = Liste(ammessi = p("Anna"), bannati = emptyList())
        val la = Liste(ammessi = p("anna"), bannati = emptyList())
        assertTrue(Allineamento.differenza(qui, la).vuota)
    }

    @Test
    fun `se di la' c'e' gia' tutto non c'e' niente da fare`() {
        val qui = Liste(ammessi = p("Anna"), bannati = p("Vandalo"))
        assertTrue(Allineamento.differenza(qui, qui).vuota)
    }

    @Test
    fun `non si toglie mai niente`() {
        // Di la' c'e' uno in piu': non compare da nessuna parte fra le cose da
        // fare, perche' toglierlo sarebbe una decisione che nessuno ha preso.
        val qui = Liste(ammessi = p("Anna"), bannati = emptyList())
        val la = Liste(ammessi = p("Anna", "Carlo"), bannati = emptyList())
        val d = Allineamento.differenza(qui, la)
        assertTrue(d.vuota)
        assertFalse(Allineamento.comandi(d).any { it.contains("Carlo") })
    }

    // ------------------------------------------------- le decisioni altrui

    @Test
    fun `chi e' ammesso di qua e bannato di la' non viene toccato`() {
        val qui = Liste(ammessi = p("Tizio"), bannati = emptyList())
        val la = Liste(ammessi = emptyList(), bannati = p("Tizio"))
        val d = Allineamento.differenza(qui, la)
        assertTrue(d.daAmmettere.isEmpty())
        assertEquals(listOf("tizio"), Allineamento.incoerenti(qui, la))
    }

    @Test
    fun `chi e' bannato di qua e ammesso di la' non viene toccato`() {
        val qui = Liste(ammessi = emptyList(), bannati = p("Tizio"))
        val la = Liste(ammessi = p("Tizio"), bannati = emptyList())
        val d = Allineamento.differenza(qui, la)
        assertTrue(d.daBannare.isEmpty())
        assertEquals(listOf("tizio"), Allineamento.incoerenti(qui, la))
    }

    @Test
    fun `chi sta nella stessa lista di qua e di la' non e' incoerente`() {
        val qui = Liste(ammessi = p("Anna"), bannati = p("Vandalo"))
        assertTrue(Allineamento.incoerenti(qui, qui).isEmpty())
    }

    // ---------------------------------------------------------- i comandi

    @Test
    fun `i comandi sono quelli che il server capisce`() {
        val qui = Liste(ammessi = p("Anna"), bannati = p("Vandalo"))
        val la = Liste(ammessi = emptyList(), bannati = emptyList())
        assertEquals(
            listOf("whitelist add Anna", "ban Vandalo"),
            Allineamento.comandi(Allineamento.differenza(qui, la))
        )
    }

    @Test
    fun `ogni comando resta riconoscibile come provvedimento`() {
        // Serve a mandaSuPiuServer e al registro: se il tipo non si riconosce,
        // un rifiuto del server non verrebbe attribuito a nessuno.
        val qui = Liste(ammessi = p("Anna"), bannati = p("Vandalo"))
        Allineamento.comandi(Allineamento.differenza(qui, Liste(emptyList(), emptyList())))
            .forEach {
                assertTrue(it, Provvedimenti.tipoDi(it) != null)
                assertTrue(it, Provvedimenti.giocatoreDi(it) != null)
            }
    }

    // -------------------------------------------------------- cosa si legge

    @Test
    fun `quando non c'e' niente da fare lo dice e basta`() {
        val qui = Liste(ammessi = p("Anna"), bannati = emptyList())
        val testo = Allineamento.descrizione("Qui", "La", Allineamento.differenza(qui, qui), emptyList())
        assertTrue(testo, testo.contains("hanno già tutti"))
        assertFalse(testo, testo.contains("mancano"))
    }

    @Test
    fun `dice quanti e chi, e che non toglie niente`() {
        val qui = Liste(ammessi = p("Anna", "Bruno"), bannati = p("Vandalo"))
        val la = Liste(ammessi = emptyList(), bannati = emptyList())
        val testo = Allineamento.descrizione("Qui", "La", Allineamento.differenza(qui, la), emptyList())
        assertTrue(testo, testo.contains("2 da ammettere"))
        assertTrue(testo, testo.contains("1 da bannare"))
        assertTrue(testo, testo.contains("Anna"))
        assertTrue(testo, testo.contains("non viene tolto nessuno"))
    }

    @Test
    fun `un elenco lunghissimo non diventa un muro`() {
        val tanti = Liste(ammessi = p(*(1..40).map { "Tizio$it" }.toTypedArray()), bannati = emptyList())
        val testo = Allineamento.descrizione(
            "Qui", "La", Allineamento.differenza(tanti, Liste(emptyList(), emptyList())), emptyList()
        )
        assertTrue(testo, testo.contains("e altri 25"))
    }

    @Test
    fun `gli incoerenti si vedono, con scritto che non si toccano`() {
        val qui = Liste(ammessi = p("Tizio"), bannati = emptyList())
        val la = Liste(ammessi = emptyList(), bannati = p("Tizio"))
        val testo = Allineamento.descrizione(
            "Qui", "La", Allineamento.differenza(qui, la), Allineamento.incoerenti(qui, la)
        )
        assertTrue(testo, testo.contains("tizio"))
        assertTrue(testo, testo.contains("Non li tocco"))
    }
}
