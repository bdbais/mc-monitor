package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica il parsing sul formato reale di un server vanilla 1.21.10
 * (`[HH:MM:SS] [Server thread/INFO]: ...`), stack trace Netty compresi.
 */
class LgsmParsingTest {

    private val noise = """
        [02:34:04] [Server thread/INFO]: Starting minecraft server version 1.21.10
        [02:34:05] [Server thread/INFO]: Done (0.652s)! For help, type "help"
        [02:35:05] [Server thread/INFO]: Server empty for 60 seconds, pausing
        [05:50:57] [Netty Epoll Server IO #4/ERROR]: Error sending packet clientbound/minecraft:disconnect
        io.netty.handler.codec.EncoderException: Sending unknown packet 'clientbound/minecraft:disconnect'
        	at aad.a(SourceFile:50) ~[server-1.21.10.jar:?]
        	at io.netty.channel.AbstractChannelHandlerContext.write(AbstractChannelHandlerContext.java:984) [netty-transport-4.1.118.Final.jar:4.1.118.Final]
    """.trimIndent()

    @Test
    fun `il rumore del log non produce falsi positivi`() {
        assertNull(Lgsm.parseOnlinePlayers(noise))
        assertTrue(Lgsm.parsePositions(noise, listOf("Fede")).isEmpty())
        assertTrue(Lgsm.parseChat("latest.log:$noise", "2026-08-03").isEmpty())
    }

    @Test
    fun `legge i giocatori online dalla risposta di list`() {
        val log = """
            $noise
            [21:04:11] [Server thread/INFO]: There are 2 of a max of 20 players online: Fede, Luca
        """.trimIndent()
        assertEquals(listOf("Fede", "Luca"), Lgsm.parseOnlinePlayers(log))
        assertEquals(20, Lgsm.parseMaxPlayers(log))
    }

    @Test
    fun `server vuoto restituisce lista vuota, non null`() {
        val log = "[21:04:11] [Server thread/INFO]: There are 0 of a max of 20 players online: "
        assertEquals(emptyList<String>(), Lgsm.parseOnlinePlayers(log))
    }

    @Test
    fun `legge posizione e dimensione da data get entity`() {
        val log = """
            [21:05:02] [Server thread/INFO]: Fede has the following entity data: [123.5d, 71.0d, -48.25d]
            [21:05:02] [Server thread/INFO]: Fede has the following entity data: "minecraft:the_nether"
            [21:05:03] [Server thread/INFO]: Luca has the following entity data: [-10.0d, 64.0d, 8.0d]
        """.trimIndent()
        val positions = Lgsm.parsePositions(log, listOf("Fede", "Luca"))
        assertEquals(2, positions.size)

        val fede = positions.first { it.name == "Fede" }
        assertEquals(123.5, fede.x, 0.001)
        assertEquals(71.0, fede.y, 0.001)
        assertEquals(-48.25, fede.z, 0.001)
        assertEquals("minecraft:the_nether", fede.dimension)
        assertEquals("the nether", fede.shortDimension)

        // Senza risposta esplicita sulla dimensione si assume l'Overworld.
        assertEquals("minecraft:overworld", positions.first { it.name == "Luca" }.dimension)
    }

    @Test
    fun `estrae chat e comandi con data e ora`() {
        val raw = """
            2026-08-01-1.log.gz:[21:02:11] [Server thread/INFO]: <Fede> buonanotte a tutti
            latest.log:[08:15:00] [Server thread/INFO]: <Fede> ciao, chi c'è?
            latest.log:[08:16:42] [Server thread/INFO]: Fede issued server command: /tp 100 64 -20
            latest.log:[08:17:00] [Server thread/INFO]: <Luca> arrivo
        """.trimIndent()

        val messages = Lgsm.parseChat(raw, "2026-08-03").filter { it.player == "Fede" }
        assertEquals(3, messages.size)

        // Ordinamento cronologico: prima il log compresso di due giorni fa.
        assertEquals("2026-08-01 21:02:11", messages[0].stamp)
        assertEquals("buonanotte a tutti", messages[0].text)

        assertEquals("2026-08-03 08:15:00", messages[1].stamp)
        assertEquals("ciao, chi c'è?", messages[1].text)

        assertTrue(messages[2].isCommand)
        assertEquals("/tp 100 64 -20", messages[2].text)
    }

    @Test
    fun `riconosce i tentativi di accesso e il rifiuto della whitelist`() {
        val raw = """
            latest.log:[20:01:00] [User Authenticator #1/INFO]: UUID of player Fede is 069a79f4-44e9-4726-a5be-fca90e38aaf5
            latest.log:[20:01:01] [Server thread/INFO]: Fede joined the game
            latest.log:[20:10:30] [User Authenticator #2/INFO]: UUID of player Estraneo is 11111111-2222-3333-4444-555555555555
            latest.log:[20:10:30] [Server thread/INFO]: Disconnecting com.mojang.authlib.GameProfile@5a1b[id=11111111-2222-3333-4444-555555555555,name=Estraneo,properties={},legacy=false] (/93.45.1.2:51000): You are not white-listed on this server!
        """.trimIndent()

        val attempts = Lgsm.parseJoinAttempts(raw, "2026-08-05")
        assertEquals(2, attempts.size)

        val estraneo = attempts.first { it.name == "Estraneo" }
        assertEquals(JoinAttempt.Outcome.REJECTED_WHITELIST, estraneo.outcome)
        assertEquals("11111111-2222-3333-4444-555555555555", estraneo.uuid)
        assertEquals("2026-08-05 20:10:30", estraneo.stamp)

        val fede = attempts.first { it.name == "Fede" }
        assertEquals(JoinAttempt.Outcome.JOINED, fede.outcome)
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", fede.uuid)

        // Il più recente per primo: la lista d'attesa mostra prima chi ha appena bussato.
        assertEquals("Estraneo", attempts.first().name)
    }

    @Test
    fun `tiene solo l ultimo tentativo di ogni giocatore`() {
        val raw = """
            2026-08-01-1.log.gz:[10:00:00] [User Authenticator #1/INFO]: UUID of player Estraneo is 11111111-2222-3333-4444-555555555555
            latest.log:[21:30:00] [Server thread/INFO]: Disconnecting com.mojang.authlib.GameProfile@9f[id=11111111-2222-3333-4444-555555555555,name=Estraneo,properties={},legacy=false] (/93.45.1.2:51000): You are not white-listed on this server!
        """.trimIndent()

        val attempts = Lgsm.parseJoinAttempts(raw, "2026-08-05")
        assertEquals(1, attempts.size)
        assertEquals("2026-08-05 21:30:00", attempts[0].stamp)
        assertEquals(JoinAttempt.Outcome.REJECTED_WHITELIST, attempts[0].outcome)
        // L'UUID viene dalla riga più vecchia, in un file diverso.
        assertEquals("11111111-2222-3333-4444-555555555555", attempts[0].uuid)
    }

    @Test
    fun `i colori ANSI di LinuxGSM non sporcano il testo`() {
        val ansi = "\u001B[0;32m[21:04:11] [Server thread/INFO]: <Fede> test\u001B[0m"
        val messages = Lgsm.parseChat("latest.log:$ansi", "2026-08-03")
        assertEquals(1, messages.size)
        assertEquals("test", messages[0].text)
        assertEquals("21:04:11", messages[0].time)
    }
}
