package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I due segni accanto ai nomi. Il punto delicato non è accendere e spegnere: è
 * che uno stesso giocatore non possa risultare segnato e non segnato a seconda
 * di come il server ha scritto il suo nome quella volta.
 */
class SegniTest {

    @Test
    fun `il preferito si accende e si spegne`() {
        val uno = Segni.cambiaPreferito(emptySet(), "Baisso")
        assertTrue(Segni.segnato(uno, "Baisso"))
        assertFalse(Segni.segnato(Segni.cambiaPreferito(uno, "Baisso"), "Baisso"))
    }

    @Test
    fun `le maiuscole non fanno due giocatori`() {
        // `list` risponde "Baisso" e il log scrive "baisso": se contassero, si
        // potrebbe segnare due volte la stessa persona e togliere il segno a
        // una sola delle due.
        val uno = Segni.cambiaPreferito(emptySet(), "Baisso")
        assertTrue(Segni.segnato(uno, "BAISSO"))
        assertEquals(emptySet<String>(), Segni.cambiaPreferito(uno, "baisso"))
    }

    @Test
    fun `i preferiti non hanno limite`() {
        var s = emptySet<String>()
        (1..20).forEach { s = Segni.cambiaPreferito(s, "giocatore$it") }
        assertEquals(20, s.size)
    }

    // -------------------------------------------------------- il cruscotto

    private fun sorvegliatiPieni(): Set<String> {
        var s = emptySet<String>()
        (1..Cruscotto.MASSIMO).forEach {
            s = (Segni.cambiaSorvegliato(s, "giocatore$it") as Segni.Esito.Fatto).insieme
        }
        return s
    }

    @Test
    fun `il cruscotto si riempie e poi dice di no`() {
        val pieno = sorvegliatiPieni()
        assertEquals(Cruscotto.MASSIMO, pieno.size)
        val esito = Segni.cambiaSorvegliato(pieno, "unoDiTroppo")
        assertTrue("ne ha accettato uno in più: $esito", esito is Segni.Esito.Pieno)
        assertEquals(Cruscotto.MASSIMO, (esito as Segni.Esito.Pieno).quanti)
    }

    @Test
    fun `pieno non vuol dire che si butta fuori il piu' vecchio`() {
        // Sostituire in silenzio vorrebbe dire smettere di guardare qualcuno
        // senza dirlo, che è l'unica cosa che il cruscotto non deve fare.
        val pieno = sorvegliatiPieni()
        Segni.cambiaSorvegliato(pieno, "unoDiTroppo")
        assertTrue("giocatore1" in pieno)
    }

    @Test
    fun `da pieno si puo' sempre togliere`() {
        val pieno = sorvegliatiPieni()
        val esito = Segni.cambiaSorvegliato(pieno, "GIOCATORE1")
        assertTrue(esito is Segni.Esito.Fatto)
        val dopo = (esito as Segni.Esito.Fatto).insieme
        assertEquals(Cruscotto.MASSIMO - 1, dopo.size)
        assertFalse(Segni.segnato(dopo, "giocatore1"))
        // E adesso c'è posto.
        assertTrue(Segni.cambiaSorvegliato(dopo, "nuovo") is Segni.Esito.Fatto)
    }

    @Test
    fun `la spiegazione dice quanti e cosa fare`() {
        val testo = Segni.spiegazionePieno(Cruscotto.MASSIMO)
        assertTrue(testo.contains(Cruscotto.MASSIMO.toString()))
        assertTrue("non dice cosa fare: $testo", testo.contains("Togli"))
    }

    // ----------------------------------------------------------- l'ordine

    @Test
    fun `i preferiti stanno in cima e gli altri restano come erano`() {
        val nomi = listOf("Anna", "Bruno", "Carla", "Dario")
        val ordinati = Segni.ordina(nomi, setOf("carla", "anna"))
        assertEquals(listOf("Anna", "Carla", "Bruno", "Dario"), ordinati)
    }

    @Test
    fun `senza preferiti l'elenco non si tocca`() {
        // Chi guarda due volte di seguito non deve trovare le righe rimescolate.
        val nomi = listOf("Zeno", "Anna", "Marco")
        assertEquals(nomi, Segni.ordina(nomi, emptySet()))
    }

    @Test
    fun `un preferito che non e' nell'elenco non aggiunge righe`() {
        val nomi = listOf("Anna", "Bruno")
        assertEquals(nomi, Segni.ordina(nomi, setOf("carla")))
    }

    // ------------------------------------------------ quello che c'era prima

    @Test
    fun `quello salvato prima si normalizza in lettura`() {
        val salvato = setOf("Baisso", "baisso", "", "ANNA")
        assertEquals(setOf("baisso", "anna"), Segni.ripulisci(salvato))
    }

    @Test
    fun `un cruscotto salvato troppo pieno viene tagliato`() {
        // Se domani il limite scendesse, o se un salvataggio vecchio ne avesse
        // di più, il cruscotto non deve provare a mostrarne otto.
        val tanti = (1..10).map { "giocatore$it" }.toSet()
        assertEquals(Cruscotto.MASSIMO, Segni.ripulisci(tanti, Cruscotto.MASSIMO).size)
    }
}
