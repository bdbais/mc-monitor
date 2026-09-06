package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il filtro degli errori, provato su un registro come quelli veri.
 *
 * Il caso da cui nasce: un comando manda in errore il server, e la console si
 * riempie di righe `at ...` in cui la riga che dice cosa è successo sparisce.
 */
class RegistroTest {

    private val log = """
        [12:00:01] [Server thread/INFO]: Starting minecraft server version 1.20.1
        [12:00:02] [Server thread/INFO]: Preparing level "world"
        [12:00:09] [Server thread/INFO]: Done (7.1s)! For help, type "help"
        [12:03:11] [User Authenticator #1/WARN]: Failed to verify authentication
        [12:03:12] [Server thread/ERROR]: Could not resolve profile for Baisso
        java.io.IOException: Server returned HTTP response code: 429
        	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1998)
        	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1583)
        	at com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.fillProfile(Yggdrasil.java:220)
        	at com.mojang.authlib.yggdrasil.YggdrasilGameProfileRepository.findProfile(Yggdrasil.java:88)
        	at net.minecraft.server.players.PlayerList.op(PlayerList.java:1102)
        	at net.minecraft.server.commands.OpCommand.opPlayers(OpCommand.java:64)
        Caused by: java.net.SocketTimeoutException: connect timed out
        	at java.base/java.net.Socket.connect(Socket.java:633)
        	... 14 more
        [12:03:13] [Server thread/INFO]: Baisso joined the game
        [12:03:20] [Server thread/INFO]: <Baisso> ciao a tutti
    """.trimIndent()

    // --------------------------------------------------------- trovare il guaio

    @Test
    fun `tiene le righe che dicono cosa e' successo`() {
        val g = Registro.soloGuai(log)
        assertTrue(g.contains("Could not resolve profile for Baisso"))
        assertTrue(g.contains("java.io.IOException"))
        assertTrue(g.contains("Failed to verify authentication"))
        assertTrue(g.contains("Caused by: java.net.SocketTimeoutException"))
    }

    @Test
    fun `butta fuori il rumore`() {
        val g = Registro.soloGuai(log)
        assertFalse("una riga normale e' rimasta", g.contains("Starting minecraft server"))
        assertFalse(g.contains("joined the game"))
        assertFalse("la chat non c'entra niente con gli errori", g.contains("ciao a tutti"))
    }

    @Test
    fun `della traccia tiene le prime righe e conta le altre`() {
        // Le prime dicono dove. Le altre servono con il sorgente aperto, e sono
        // esattamente quelle che fanno sparire la riga che conta.
        val g = Registro.soloGuai(log)
        assertTrue(g.contains("HttpURLConnection.getInputStream0"))
        assertFalse("la traccia doveva fermarsi", g.contains("OpCommand.opPlayers"))
        assertTrue("non dice quante ne ha nascoste", g.contains("altre 3 righe di traccia"))
    }

    @Test
    fun `una riga sola nascosta si dice al singolare`() {
        val corto = """
            [12:00:00] [Server thread/ERROR]: rotto
            	at uno.Uno(Uno.java:1)
            	at due.Due(Due.java:2)
            	at tre.Tre(Tre.java:3)
            	at quattro.Quattro(Quattro.java:4)
        """.trimIndent()
        assertTrue(Registro.soloGuai(corto).contains("altre 1 riga di traccia"))
    }

    @Test
    fun `un registro senza guai non produce niente`() {
        val sereno = "[12:00:01] [Server thread/INFO]: Done\n[12:00:02] [Server thread/INFO]: ok"
        assertEquals("", Registro.soloGuai(sereno))
        assertEquals(0, Registro.quantiGuai(sereno))
    }

    @Test
    fun `le righe at fuori da un errore non vengono raccolte`() {
        // Senza il "dentro un guaio" una riga qualsiasi che comincia per at
        // finirebbe nel filtro portandosi dietro chissa' cosa.
        val strano = "[12:00:00] [Server thread/INFO]: at the spawn point\nnormale"
        assertEquals("", Registro.soloGuai(strano))
    }

    @Test
    fun `conta i guai senza contare le tracce`() {
        // Quattro: il WARN, l'ERROR, la IOException e il Caused by.
        assertEquals(4, Registro.quantiGuai(log))
    }

    @Test
    fun `il testo vuoto non fa saltare niente`() {
        assertEquals("", Registro.soloGuai(""))
        assertEquals(0, Registro.quantiGuai(""))
    }

    // ------------------------------------------------------- mandarlo fuori

    @Test
    fun `gli indirizzi dei giocatori vengono coperti`() {
        val con = "[12:00:00] [INFO]: Baisso[/192.168.1.44:51234] logged in\n" +
                "[12:00:01] [INFO]: altro[/2001:db8:85a3:0:0:8a2e:370:7334] logged in"
        val fuori = Registro.perCondivisione(con, listOf("prova"))
        assertFalse("un IPv4 e' rimasto", fuori.contains("192.168.1.44"))
        assertFalse("un IPv6 e' rimasto", fuori.contains("2001:db8:85a3"))
        assertTrue(fuori.contains("‹indirizzo›"))
        assertTrue("i nomi devono restare", fuori.contains("Baisso"))
        assertTrue(fuori.contains("indirizzi IP sono stati coperti"))
    }

    @Test
    fun `senza indirizzi lo dice comunque`() {
        val fuori = Registro.perCondivisione("[12:00:00] [INFO]: tutto bene", listOf("prova"))
        assertTrue(fuori.contains("nessun indirizzo IP da coprire"))
    }

    @Test
    fun `l'intestazione arriva prima del registro`() {
        val fuori = Registro.perCondivisione("riga", listOf("MC Monitor 1.34", "server: casa"))
        val righe = fuori.lines()
        assertTrue(righe[0].startsWith("# MC Monitor"))
        assertTrue(righe[1].startsWith("# server: casa"))
        assertTrue(fuori.trimEnd().endsWith("riga"))
    }

    @Test
    fun `il numero di porta non viene scambiato per un indirizzo`() {
        val fuori = Registro.perCondivisione("in ascolto sulla 25565", listOf("x"))
        assertTrue(fuori.contains("25565"))
    }

    @Test
    fun `una versione non viene scambiata per un indirizzo`() {
        // "1.20.1" ha tre gruppi di cifre separati da punti, ma non e' un IP:
        // ne servono quattro. Coprire la versione renderebbe il log illeggibile.
        val fuori = Registro.perCondivisione("server version 1.20.1", listOf("x"))
        assertTrue(fuori.contains("1.20.1"))
        assertTrue(fuori.contains("nessun indirizzo IP da coprire"))
    }
}
