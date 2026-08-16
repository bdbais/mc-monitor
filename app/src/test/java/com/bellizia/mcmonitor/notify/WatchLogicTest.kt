package com.bellizia.mcmonitor.notify

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchLogicTest {

    private val server = ServerConfig(
        name = "Casa",
        host = "mc.esempio.it",
        password = "x",
        notifyEnabled = true,
        notifyOffline = true,
        notifyOnline = true,
        notifyJoin = true,
        notifyLeave = true
    )

    private fun run(cfg: ServerConfig, prev: WatchState, now: Observation) =
        WatchLogic.evaluate(cfg, prev, now)

    @Test
    fun `il primo giro non annuncia nulla`() {
        val (state, events) = run(
            server,
            WatchState(),
            Observation(reachable = true, running = true, players = setOf("Fede", "Luca"))
        )
        assertTrue("nessun evento al primo giro", events.isEmpty())
        assertEquals(setOf("Fede", "Luca"), state.players)
    }

    @Test
    fun `avvisa quando entra ed esce qualcuno`() {
        val prev = WatchState(reachable = true, running = true, players = setOf("Fede"))
        val (_, events) = run(
            server, prev,
            Observation(reachable = true, running = true, players = setOf("Fede", "Luca"))
        )
        assertEquals(1, events.size)
        assertEquals(Notifications.CHANNEL_PLAYERS, events[0].channel)
        assertTrue(events[0].title.contains("Luca"))
        assertTrue(events[0].title.contains("entrato"))

        val (_, usciti) = run(
            server,
            WatchState(true, true, setOf("Fede", "Luca")),
            Observation(reachable = true, running = true, players = setOf("Fede"))
        )
        assertTrue(usciti[0].title.contains("Luca"))
        assertTrue(usciti[0].title.contains("uscito"))
    }

    @Test
    fun `gli eventi disattivati restano zitti`() {
        val soloUscite = server.copy(notifyJoin = false)
        val (_, events) = run(
            soloUscite,
            WatchState(true, true, setOf("Fede")),
            Observation(reachable = true, running = true, players = setOf("Fede", "Luca"))
        )
        assertTrue("l'entrata era disattivata", events.isEmpty())
    }

    @Test
    fun `avvisa quando il server si ferma e quando riparte`() {
        val (_, fermo) = run(
            server,
            WatchState(reachable = true, running = true, players = setOf("Fede")),
            Observation(reachable = true, running = false)
        )
        assertEquals(1, fermo.size)
        assertTrue(fermo[0].title.contains("offline"))

        val (_, ripartito) = run(
            server,
            WatchState(reachable = true, running = false),
            Observation(reachable = true, running = true, players = setOf("Fede"))
        )
        assertTrue(ripartito[0].title.contains("online"))
    }

    @Test
    fun `server irraggiungibile avvisa una volta sola`() {
        val (statoDopo, primo) = run(
            server,
            WatchState(reachable = true, running = true, players = setOf("Fede")),
            Observation(reachable = false)
        )
        assertEquals(1, primo.size)
        assertTrue(primo[0].title.contains("irraggiungibile"))

        // Al giro successivo e' ancora giu': niente secondo avviso.
        val (_, secondo) = run(server, statoDopo, Observation(reachable = false))
        assertTrue("nessun avviso ripetuto", secondo.isEmpty())
    }

    @Test
    fun `al ritorno non annuncia come nuovi i giocatori gia' presenti`() {
        val caduto = WatchState(reachable = false)
        val (stato, events) = run(
            server, caduto,
            Observation(reachable = true, running = true, players = setOf("Fede", "Luca"))
        )
        // Un solo evento: il ritorno online. Nessuna finta entrata dei due giocatori.
        assertEquals(1, events.size)
        assertTrue(events[0].title.contains("raggiungibile"))
        assertEquals(setOf("Fede", "Luca"), stato.players)
    }

    @Test
    fun `a server fermo non si guardano i giocatori`() {
        val (stato, _) = run(
            server,
            WatchState(reachable = true, running = true, players = setOf("Fede")),
            Observation(reachable = true, running = false)
        )
        assertEquals(null, stato.players)
    }
}
