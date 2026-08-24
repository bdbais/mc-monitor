package com.bellizia.mcmonitor.ui

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Un dialogo non può avere insieme un messaggio e una lista.
 *
 * AlertDialog ne mostra uno solo, e l'altro sparisce **senza dire niente**: non
 * un errore, non un avviso, semplicemente non c'è. È già successo — il dialogo
 * "su quali altri server?" compariva senza nessuna casella, e il pulsante
 * "Applica" non applicava niente. Si vedeva solo aprendolo davvero.
 *
 * Qui si controlla la forma dei dialoghi che ne hanno bisogno: le caselle vanno
 * in una vista costruita a mano, e la lista di scelta va da sola.
 */
class DialoghiTest {

    private fun sorgente(nome: String): String? = listOf(
        File("src/main/java/com/bellizia/mcmonitor/ui/$nome"),
        File("app/src/main/java/com/bellizia/mcmonitor/ui/$nome")
    ).firstOrNull { it.isFile }?.readText()

    /** Il corpo di una funzione, fino alla sua parentesi di chiusura. */
    private fun corpo(testo: String, firma: String): String =
        testo.substringAfter(firma, "").substringBefore("\n    }", "")

    @Test
    fun `il dialogo delle caselle non usa setMessage`() {
        val testo = sorgente("PlayersFragment.kt")
        assumeTrue("PlayersFragment.kt non trovato", testo != null)
        val f = corpo(testo!!, "private fun chiediDoveApplicare(")
        assertTrue("chiediDoveApplicare non trovata", f.isNotBlank())
        assertTrue(
            "Le caselle e un messaggio insieme: una delle due sparisce in silenzio",
            !(f.contains(".setMessage(") &&
                    (f.contains("setMultiChoiceItems") || f.contains("setItems(")))
        )
        // Le caselle devono essere viste vere, non voci di un elenco.
        assertTrue("le caselle non sono piu' una vista", f.contains("CheckBox("))
    }

    @Test
    fun `la scelta del server non usa setMessage`() {
        val testo = sorgente("PlayersFragment.kt")
        assumeTrue("PlayersFragment.kt non trovato", testo != null)
        val f = corpo(testo!!, "private fun scegliDoveAllineare(")
        assertTrue("scegliDoveAllineare non trovata", f.isNotBlank())
        assertTrue(
            "Un elenco e un messaggio insieme: uno dei due sparisce in silenzio",
            !(f.contains(".setMessage(") && f.contains("setItems("))
        )
    }
}
