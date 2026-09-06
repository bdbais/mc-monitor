package com.bellizia.mcmonitor.rete

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Cercare un server nella rete di casa: quali indirizzi provare, e come si
 * chiede a un server chi è senza entrarci.
 */
class ReteTest {

    // ------------------------------------------------------- quali indirizzi

    @Test
    fun `solo le reti private`() {
        listOf("192.168.1.10", "10.0.0.5", "172.16.3.9", "172.31.255.1", "169.254.1.1")
            .forEach { assertTrue("«$it» doveva essere privato", Sottorete.privato(it)) }
        // Fuori da casa propria bussare a ogni indirizzo non e' cercare, e'
        // scansionare la rete di qualcun altro.
        listOf("8.8.8.8", "213.0.113.9", "172.32.0.1", "172.15.0.1", "1.2.3.4")
            .forEach { assertFalse("«$it» non doveva passare", Sottorete.privato(it)) }
    }

    @Test
    fun `un indirizzo storto non passa`() {
        listOf("", "casa", "192.168.1", "192.168.1.1.1", "192.168.1.999", "192.168.-1.1")
            .forEach { assertFalse("«$it»", Sottorete.privato(it)) }
    }

    @Test
    fun `una rete di casa da' gli indirizzi giusti`() {
        val lista = Sottorete.indirizzi("192.168.1.37", 24)
        assertEquals(253, lista.size)               // 254 utili, meno il telefono
        assertTrue(lista.contains("192.168.1.1"))
        assertTrue(lista.contains("192.168.1.254"))
        assertFalse("l'indirizzo di rete non e' di nessuno", lista.contains("192.168.1.0"))
        assertFalse("il broadcast nemmeno", lista.contains("192.168.1.255"))
        assertFalse("il telefono lo sappiamo gia'", lista.contains("192.168.1.37"))
    }

    @Test
    fun `una rete piccola resta piccola`() {
        val lista = Sottorete.indirizzi("10.0.0.2", 29)   // otto indirizzi
        assertEquals(listOf("10.0.0.1", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.6"), lista)
    }

    @Test
    fun `una rete troppo grande non si setaccia`() {
        // Certi hotspot e certe VPN dichiarano /16: sono 65 000 indirizzi, e
        // provarli a uno a uno vorrebbe dire un'app ferma per mezz'ora.
        assertTrue(Sottorete.troppoGrande(16))
        assertEquals(emptyList<String>(), Sottorete.indirizzi("10.4.0.9", 16))
        assertFalse(Sottorete.troppoGrande(24))
    }

    @Test
    fun `da una rete pubblica non si cerca niente`() {
        assertEquals(emptyList<String>(), Sottorete.indirizzi("213.0.113.9", 24))
    }

    // ------------------------------------------------------- il protocollo

    @Test
    fun `i numeri a lunghezza variabile`() {
        // I valori del protocollo Minecraft, dalla documentazione.
        assertArrayEquals(byteArrayOf(0), PingServer.varInt(0))
        assertArrayEquals(byteArrayOf(1), PingServer.varInt(1))
        assertArrayEquals(byteArrayOf(127), PingServer.varInt(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 1), PingServer.varInt(128))
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 1), PingServer.varInt(255))
        assertArrayEquals(
            byteArrayOf(0xDD.toByte(), 0xC7.toByte(), 1), PingServer.varInt(25565)
        )
    }

    @Test
    fun `scritti e riletti tornano uguali`() {
        listOf(0, 1, 127, 128, 255, 25565, 767, 2097151, Int.MAX_VALUE).forEach { n ->
            val letto = PingServer.leggiVarInt(ByteArrayInputStream(PingServer.varInt(n)))
            assertEquals("andata e ritorno di $n", n, letto)
        }
    }

    @Test
    fun `un numero infinito non fa girare a vuoto`() {
        // Un byte con il bit di continuazione sempre acceso terrebbe il ciclo
        // aperto per sempre: e' il modo piu' economico per bloccare l'app.
        val cattivo = ByteArray(64) { 0x80.toByte() }
        runCatching { PingServer.leggiVarInt(ByteArrayInputStream(cattivo)) }.fold(
            onSuccess = { throw AssertionError("ha letto un numero senza fine") },
            onFailure = { assertTrue(it is PingServer.Troncato) }
        )
    }

    @Test
    fun `un flusso che finisce a meta' non fa esplodere niente`() {
        runCatching { PingServer.leggiVarInt(ByteArrayInputStream(byteArrayOf(0x80.toByte()))) }
            .fold(
                onSuccess = { throw AssertionError("ha letto da un flusso vuoto") },
                onFailure = { assertTrue(it is PingServer.Troncato) }
            )
    }

    @Test
    fun `la stretta di mano dice che stiamo solo guardando`() {
        val s = PingServer.stretta("192.168.1.5", 25565)
        // lunghezza, id 0, protocollo, stringa, porta, e in fondo lo stato 1
        assertEquals(s.size - 1, s[0].toInt())
        assertEquals(0, s[1].toInt())
        assertEquals("l'ultimo byte deve dire «sto guardando», non «entro»", 1, s.last().toInt())
        assertTrue(String(s, Charsets.UTF_8).contains("192.168.1.5"))
    }

    @Test
    fun `la porta viaggia su due byte`() {
        val s = PingServer.stretta("x", 25565)
        val i = s.size - 3                                   // due della porta, uno dello stato
        assertEquals(25565, ((s[i].toInt() and 0xFF) shl 8) or (s[i + 1].toInt() and 0xFF))
    }

    // ----------------------------------------------------------- il cartello

    private fun rispostaCon(json: String): ByteArrayInputStream {
        val corpo = PingServer.varInt(0x00) + PingServer.varInt(json.toByteArray().size) +
                json.toByteArray(Charsets.UTF_8)
        return ByteArrayInputStream(PingServer.varInt(corpo.size) + corpo)
    }

    @Test
    fun `legge la risposta di un server vero`() {
        val json = """{"version":{"name":"Paper 1.20.1","protocol":763},
            "players":{"max":20,"online":2},"description":{"text":"Il mondo di casa"}}"""
        assertEquals(json, PingServer.leggiRisposta(rispostaCon(json)))
    }

    @Test
    fun `dal cartello tira fuori quello che serve`() {
        val i = PingServer.insegna(
            "192.168.1.5", 25565,
            """{"version":{"name":"Paper 1.20.1","protocol":763},
               "players":{"max":20,"online":2},"description":{"text":"Il mondo di casa"}}"""
        )!!
        assertEquals("Paper 1.20.1", i.versione)
        assertEquals(763, i.protocollo)
        assertEquals(2, i.online)
        assertEquals(20, i.massimo)
        assertEquals("Il mondo di casa", i.motd)
    }

    @Test
    fun `la descrizione ha tre forme e vanno bene tutte`() {
        fun motd(descr: String) =
            PingServer.insegna("x", 1, """{"description":$descr}""")?.motd

        assertEquals("secca", motd(""""secca""""))
        assertEquals("con text", motd("""{"text":"con text"}"""))
        // La forma a pezzi: la usano quasi tutti i server con il MOTD colorato,
        // e leggendo solo "text" verrebbe fuori vuota.
        assertEquals(
            "Il mondo di casa",
            motd("""{"text":"Il ","extra":[{"text":"mondo "},{"text":"di casa"}]}""")
        )
    }

    @Test
    fun `i codici colore non finiscono a schermo`() {
        val i = PingServer.insegna("x", 1, """{"description":{"text":"§aCasa§r §7aperta"}}""")!!
        assertEquals("Casa aperta", i.motd)
    }

    @Test
    fun `una risposta che non e' JSON non produce un'insegna`() {
        assertNull(PingServer.insegna("x", 1, "questo non e' json"))
    }

    @Test
    fun `un cartello senza campi non inventa numeri`() {
        val i = PingServer.insegna("x", 1, "{}")!!
        assertNull(i.versione)
        assertNull(i.online)
        assertNull(i.motd)
    }

    @Test
    fun `una risposta assurdamente lunga viene rifiutata`() {
        // Un finto server che dichiara due miliardi di byte farebbe allocare
        // finche' l'app muore.
        val cattivo = PingServer.varInt(Int.MAX_VALUE) + PingServer.varInt(0)
        runCatching { PingServer.leggiRisposta(ByteArrayInputStream(cattivo)) }.fold(
            onSuccess = { throw AssertionError("ha accettato una lunghezza assurda") },
            onFailure = { assertTrue(it is PingServer.Troncato) }
        )
    }
}
