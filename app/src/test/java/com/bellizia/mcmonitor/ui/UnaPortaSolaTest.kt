package com.bellizia.mcmonitor.ui

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I comandi su un giocatore passano da una porta sola.
 *
 * Questa regola si è rotta due volte nella stessa giornata: prima i due campi in
 * cima alla scheda Giocatori, poi i pulsanti delle liste. Ogni volta il
 * risultato era lo stesso — il gesto più corto per bannare qualcuno non chiedeva
 * se ripeterlo sugli altri mondi, e il ban restava su un server solo — e ogni
 * volta il commento nel codice diceva "l'unica porta".
 *
 * Un test che legge il sorgente è insolito, e non è il modo più elegante di
 * dirlo. Ma questa è una regola sul CABLAGGIO di un Fragment, non sul
 * comportamento di una funzione: senza Android non si può esercitare, e a
 * lasciarla scritta solo in un commento si è già rotta due volte.
 */
class UnaPortaSolaTest {

    private val comandiSuUnGiocatore =
        listOf("\"ban ", "\"whitelist add ", "\"whitelist remove ", "\"pardon ")

    private fun sorgente(): String? {
        // I test girano con la cartella del modulo come radice; se un domani
        // non fosse più così, il test si toglie di mezzo invece di fallire a
        // vuoto e far perdere tempo a chi non c'entra.
        val f = listOf(
            File("src/main/java/com/bellizia/mcmonitor/ui/PlayersFragment.kt"),
            File("app/src/main/java/com/bellizia/mcmonitor/ui/PlayersFragment.kt")
        ).firstOrNull { it.isFile }
        return f?.readText()
    }

    @Test
    fun `nessun comando su un giocatore scavalca esegui`() {
        val testo = sorgente()
        assumeTrue("PlayersFragment.kt non trovato dal test", testo != null)

        val colpevoli = testo!!.lines()
            .map { it.trim() }
            .filter { riga ->
                // Le due funzioni che parlano al solo server attivo.
                (riga.contains("command(") || riga.contains("commandWithNote(")) &&
                        comandiSuUnGiocatore.any { riga.contains(it) }
            }

        // L'unico posto dove è giusto che compaiano è soloQui(), che ci si
        // arriva solo dopo aver chiesto dove applicare il provvedimento.
        val dentroSoloQui = testo.substringAfter("private fun soloQui(", "")
            .substringBefore("\n    }", "")

        val fuoriPosto = colpevoli.filterNot { dentroSoloQui.contains(it) }
        assertTrue(
            "Questi comandi su un giocatore non passano da esegui(), quindi non " +
                    "chiedono se ripeterli sugli altri server:\n" + fuoriPosto.joinToString("\n"),
            fuoriPosto.isEmpty()
        )
    }

    @Test
    fun `esegui resta il punto in cui si decide dove applicare`() {
        val testo = sorgente()
        assumeTrue("PlayersFragment.kt non trovato dal test", testo != null)
        val esegui = testo!!.substringAfter("private fun esegui(", "")
            .substringBefore("\n    }", "")
        assertTrue("esegui() non chiede piu' il tipo", esegui.contains("Provvedimenti.tipoDi"))
        assertTrue("esegui() non chiede piu' dove", esegui.contains("chiediDoveApplicare"))
    }
}
