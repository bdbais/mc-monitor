package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceso o fermo, quando le due fonti non sono d'accordo.
 *
 * Il difetto era visibile in uno screenshot: la riga diceva «fermo» e il
 * cartellino sotto diceva «il server risponde». Non possono essere vere tutte e
 * due, e quella sbagliata era la prima — lo stato acceso/spento lo legge dalla
 * sessione tmux di LinuxGSM, e un server avviato a mano fuori da LinuxGSM
 * risulta fermo anche mentre gira.
 */
class AccesoTest {

    private fun server(running: Boolean) = DiscoveredServer(
        directory = "/mnt/dati/minecraft/mondo",
        script = "mcserver",
        running = running,
        minecraftVersion = "26.2",
        gamePort = "25565",
        rconPort = "25575",
        rconEnabled = true,
        hasServerFiles = true,
        branch = null,
        motd = null,
        sizeMb = null,
    )

    @Test
    fun `se RCON risponde il server e' acceso, comunque`() {
        // Una risposta da RCON e' una prova diretta: quel processo esiste e
        // sta ascoltando. Non c'e' niente da mediare con quello che dice tmux.
        assertTrue(server(running = false).acceso(rconHaRisposto = true))
    }

    @Test
    fun `senza risposta si crede a LinuxGSM`() {
        assertFalse(server(running = false).acceso(rconHaRisposto = false))
        assertTrue(server(running = true).acceso(rconHaRisposto = false))
    }

    @Test
    fun `il riassunto non dice piu' fermo quando risponde`() {
        val s = server(running = false)
        assertTrue("senza risposta doveva dire fermo", s.sommario().startsWith("fermo"))
        assertTrue(
            "con la risposta doveva dire in esecuzione",
            s.sommario(rconHaRisposto = true).startsWith("in esecuzione")
        )
    }

    @Test
    fun `il resto del riassunto non cambia`() {
        // Cambia una parola sola: versione, porte e mod restano dov'erano.
        val s = server(running = false)
        val fermo = s.sommario().removePrefix("fermo")
        val acceso = s.sommario(rconHaRisposto = true).removePrefix("in esecuzione")
        assertEquals(fermo, acceso)
        assertTrue(acceso.contains("Minecraft 26.2"))
        assertTrue(acceso.contains("RCON 25575"))
    }

    @Test
    fun `il vecchio riassunto resta quello di prima`() {
        // `summary` lo usano altre schermate che non hanno provato RCON: deve
        // continuare a comportarsi come sempre.
        assertEquals(server(running = false).summary, server(running = false).sommario())
    }
}
