package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il cartellino di RCON sulla prima pagina.
 *
 * L'errore da non rifare è quello che è costato due giorni: il file diceva
 * `enable-rcon=true`, la porta era in ascolto, e i comandi non arrivavano
 * lo stesso. «Acceso» non vuol dire «funziona», e il cartellino non deve
 * poterlo far credere.
 */
class SegnaleRconTest {

    @Test
    fun `senza aver provato non si dice mai che funziona`() {
        // Questo e' il test che tiene ferma tutta la faccenda: nessuna
        // combinazione di cose lette dal file puo' produrre un verde.
        listOf(true, false).forEach { acceso ->
            listOf(true, false).forEach { password ->
                val stato = SegnaleRcon.iniziale(acceso, password)
                assertNotEquals(
                    "acceso=$acceso password=$password da' un verde non verificato",
                    SegnaleRcon.Stato.RISPONDE,
                    stato
                )
            }
        }
    }

    @Test
    fun `verde solo dopo una risposta vera`() {
        assertEquals(SegnaleRcon.Stato.RISPONDE, SegnaleRcon.dopoLaProva(true))
        assertEquals(SegnaleRcon.Stato.NON_RISPONDE, SegnaleRcon.dopoLaProva(false))
        assertEquals(
            SegnaleRcon.colore(SegnaleRcon.Stato.RISPONDE),
            0xFF5FBF6B.toInt()
        )
    }

    @Test
    fun `acceso ma non provato non e' verde`() {
        assertNotEquals(
            SegnaleRcon.colore(SegnaleRcon.Stato.RISPONDE),
            SegnaleRcon.colore(SegnaleRcon.Stato.DA_PROVARE)
        )
    }

    @Test
    fun `RCON spento si vede senza bussare`() {
        assertEquals(SegnaleRcon.Stato.SPENTO, SegnaleRcon.iniziale(false, conPassword = true))
        assertEquals(SegnaleRcon.Stato.SPENTO, SegnaleRcon.iniziale(false, conPassword = false))
        assertFalse(SegnaleRcon.daProvare(SegnaleRcon.Stato.SPENTO))
    }

    @Test
    fun `senza password non si bussa e si dice perche'`() {
        // Bussare senza password vorrebbe dire un tentativo che fallisce sempre,
        // e un rosso che accusa il server di un problema che sta nell'app.
        val stato = SegnaleRcon.iniziale(accesoNelFile = true, conPassword = false)
        assertEquals(SegnaleRcon.Stato.SENZA_PASSWORD, stato)
        assertFalse(SegnaleRcon.daProvare(stato))
        assertTrue(SegnaleRcon.etichetta(stato).contains("password"))
    }

    @Test
    fun `si bussa solo quando ha senso`() {
        assertTrue(SegnaleRcon.daProvare(SegnaleRcon.iniziale(true, conPassword = true)))
    }

    @Test
    fun `le etichette dicono cosa cambia, non come si chiama la cosa`() {
        // Chi non sa cos'e' RCON deve capire lo stesso se i comandi funzionano.
        assertTrue(SegnaleRcon.etichetta(SegnaleRcon.Stato.SPENTO).contains("comandi"))
        assertTrue(SegnaleRcon.etichetta(SegnaleRcon.Stato.RISPONDE).contains("risponde"))
    }

    @Test
    fun `ogni stato ha la sua etichetta e nessuna e' vuota`() {
        SegnaleRcon.Stato.entries.forEach {
            assertTrue("$it senza etichetta", SegnaleRcon.etichetta(it).isNotBlank())
        }
    }
}
