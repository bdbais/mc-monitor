package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo stesso provvedimento su più server.
 *
 * Chi ha due mondi sulla stessa macchina non ha due comunità: bannare un
 * vandalo di là e non di qua vuol dire che fra dieci minuti è di qua.
 */
class ProvvedimentiTest {

    private fun srv(id: String, nome: String, host: String = "casa", user: String = "mc") =
        ServerConfig(
            id = id, name = nome, host = host, user = user, password = "x",
            lgsmDir = "~/$id", script = "mcserver"
        )

    private val qui = srv("1", "Il mio mondo")

    // ------------------------------------------------ quali gesti si ripetono

    @Test
    fun `riconosce i quattro gesti che ha senso ripetere`() {
        assertEquals(Provvedimenti.Tipo.BAN, Provvedimenti.tipoDi("ban Pippo"))
        assertEquals(Provvedimenti.Tipo.PARDON, Provvedimenti.tipoDi("pardon Pippo"))
        assertEquals(Provvedimenti.Tipo.AMMETTI, Provvedimenti.tipoDi("whitelist add Pippo"))
        assertEquals(Provvedimenti.Tipo.TOGLI, Provvedimenti.tipoDi("whitelist remove Pippo"))
        // Anche scritti con la barra davanti, come li scriverebbe in chat.
        assertEquals(Provvedimenti.Tipo.BAN, Provvedimenti.tipoDi("/ban Pippo"))
    }

    @Test
    fun `il ban di un indirizzo non si propaga`() {
        // Colpisce un indirizzo, che su una rete di casa e' di tutta la
        // famiglia: ripeterlo altrove moltiplica il danno di uno sbaglio.
        assertNull(Provvedimenti.tipoDi("ban-ip 1.2.3.4"))
    }

    @Test
    fun `gli altri comandi restano dove sono`() {
        listOf("kick Pippo", "op Pippo", "deop Pippo", "say ciao", "stop", "tp a b").forEach {
            assertNull(it, Provvedimenti.tipoDi(it))
        }
    }

    // ------------------------------------------------------- dove proporlo

    @Test
    fun `il server aperto adesso non compare fra gli altri`() {
        val altri = Provvedimenti.altriServer(listOf(qui, srv("2", "Creativa")), qui)
        assertEquals(listOf("Creativa"), altri.map { it.displayName })
    }

    @Test
    fun `un profilo senza credenziali non viene proposto`() {
        // Comparirebbe solo per fallire: l'app non ha la sua password.
        val monco = ServerConfig(id = "3", name = "Mai finito")
        assertTrue(Provvedimenti.altriServer(listOf(qui, monco), qui).isEmpty())
    }

    @Test
    fun `quelli sullo stesso computer arrivano gia' spuntati`() {
        val stessoPc = srv("2", "Creativa")
        val altrove = srv("3", "Da un amico", host = "altrove")
        val altri = Provvedimenti.altriServer(listOf(qui, stessoPc, altrove), qui)
        val spunte = Provvedimenti.daSpuntare(altri, qui)
        val perNome = altri.map { it.displayName }.zip(spunte.toList()).toMap()
        assertTrue(perNome["Creativa"]!!)
        assertFalse(perNome["Da un amico"]!!)
    }

    @Test
    fun `un utente diverso sullo stesso computer non e' lo stesso posto`() {
        val altroUtente = srv("2", "Di un altro", user = "tizio")
        val altri = Provvedimenti.altriServer(listOf(qui, altroUtente), qui)
        assertFalse(Provvedimenti.daSpuntare(altri, qui)[0])
    }

    // ------------------------------------------------------ tornare indietro

    @Test
    fun `ogni gesto sa come si disfa`() {
        assertEquals("pardon Pippo", Provvedimenti.perDisfare(Provvedimenti.Tipo.BAN, "Pippo"))
        assertEquals("ban Pippo", Provvedimenti.perDisfare(Provvedimenti.Tipo.PARDON, "Pippo"))
        assertEquals(
            "whitelist remove Pippo",
            Provvedimenti.perDisfare(Provvedimenti.Tipo.AMMETTI, "Pippo")
        )
        assertEquals(
            "whitelist add Pippo",
            Provvedimenti.perDisfare(Provvedimenti.Tipo.TOGLI, "Pippo")
        )
    }

    @Test
    fun `disfare e rifare torna al punto di partenza`() {
        Provvedimenti.Tipo.values().forEach { t ->
            val disfa = Provvedimenti.perDisfare(t, "Pippo")
            val tornaIndietro = Provvedimenti.tipoDi(disfa)
            assertEquals(t.name, "Pippo", Provvedimenti.perDisfare(tornaIndietro!!, "Pippo").substringAfterLast(' '))
        }
    }

    // ---------------------------------------------------------- il riassunto

    @Test
    fun `quando va tutto bene lo dice in una riga`() {
        val esiti = listOf(
            EsitoSuServer(qui, true, "ok"),
            EsitoSuServer(srv("2", "Creativa"), true, "ok")
        )
        val testo = Provvedimenti.riassunto(Provvedimenti.Tipo.BAN, "Pippo", esiti)
        assertTrue(testo, testo.contains("su tutti e 2"))
        assertFalse(testo, testo.contains("Non fatto"))
    }

    @Test
    fun `quando ne fallisce uno si vede quale e perche'`() {
        // Riuscire a meta' e' il caso normale, non l'eccezione: un server e'
        // spento, un altro e' su una macchina che in quel momento non risponde.
        val esiti = listOf(
            EsitoSuServer(qui, true, "ok"),
            EsitoSuServer(srv("2", "Creativa"), false, "Connessione rifiutata")
        )
        val testo = Provvedimenti.riassunto(Provvedimenti.Tipo.BAN, "Pippo", esiti)
        assertTrue(testo, testo.contains("1 server su 2"))
        assertTrue(testo, testo.contains("Creativa"))
        assertTrue(testo, testo.contains("Connessione rifiutata"))
        assertTrue(testo, testo.contains("restano come prima"))
    }

    @Test
    fun `quando non riesce da nessuna parte non si dice mezza verita'`() {
        val esiti = listOf(EsitoSuServer(qui, false, "spento"))
        val testo = Provvedimenti.riassunto(Provvedimenti.Tipo.AMMETTI, "Pippo", esiti)
        assertTrue(testo, testo.contains("da nessuna parte"))
        assertFalse(testo, testo.contains("ammesso su"))
    }
}
