package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La catena dei cinque file di LinuxGSM.
 *
 * Fino alla 1.23 la schermata ne leggeva uno solo — proprio quello che su
 * un'installazione nuova è vuoto — e presentava quel vuoto come "nessun
 * parametro". Qui si controlla che il valore mostrato sia quello che il server
 * usa davvero, e che si sappia da dove viene.
 */
class ServerChainTest {

    private val cfg = ServerConfig(lgsmDir = "~/server1", script = "mcserver")

    /** È l'uscita vera del comando, presa da una prova in shell su un albero finto. */
    private val letto = """
        --- _default
        ## LinuxGSM _default.cfg  -- DO NOT EDIT
        javaram="1024" # -Xmx${'$'}1024M
        maxbackups="4"
        maxbackupdays="30"
        stoponbackup="on"
        logdays="7"
        mcversion="latest"
        branch="release"
        discordalert="off"
        discordwebhook=""
        --- common
        ## Impostazioni comuni
        maxbackups="10"
        postalert="on"
        --- istanza
        ## Impostazioni di questa istanza
        javaram="4096"
        discordalert="on"
        discordwebhook="https://esempio/finto"
        #logdays="14"
        --- secrets-istanza
        discordwebhook="https://vero/segreto"
        --- fine
    """.trimIndent()

    private fun chiave(k: String) = ServerParams.parseChain(letto).first { it.key == k }

    @Test
    fun `il file dell'istanza vince sul valore di fabbrica`() {
        val javaram = chiave("javaram")
        assertEquals("4096", javaram.value)
        assertEquals(ServerParams.Da.ISTANZA, javaram.da)
        assertEquals("4096", javaram.nostro)
        assertFalse(javaram.coperto)
    }

    @Test
    fun `il file comune vince sul valore di fabbrica`() {
        val m = chiave("maxbackups")
        assertEquals("10", m.value)
        assertEquals(ServerParams.Da.COMUNE, m.da)
        // Qui non c'è scritto niente: si vede il valore di un altro file.
        assertNull(m.nostro)
    }

    @Test
    fun `quello che nessuno tocca resta di fabbrica`() {
        val b = chiave("branch")
        assertEquals("release", b.value)
        assertEquals(ServerParams.Da.FABBRICA, b.da)
        assertNull(b.nostro)
        // È il caso che prima non si vedeva per niente: la schermata diceva
        // "nessun parametro" mentre il server usava questo.
        assertEquals("30", chiave("maxbackupdays").value)
        assertEquals("on", chiave("stoponbackup").value)
    }

    @Test
    fun `una riga commentata nell'istanza non conta`() {
        val l = chiave("logdays")
        assertEquals("7", l.value)
        assertEquals(ServerParams.Da.FABBRICA, l.da)
        assertNull(l.nostro)
    }

    @Test
    fun `il commento in coda non entra nel valore neanche qui`() {
        assertEquals("1024", ServerParams.parseChain("--- _default\njavaram=\"1024\" # -Xmx1024M\n--- fine").first().value)
    }

    @Test
    fun `un valore nascosto dai segreti viene segnalato`() {
        // È il caso che farebbe dire una bugia alla schermata: si scrive nel file
        // dell'istanza, si mostra il valore nuovo, e il server continua a usare
        // quello del file dei segreti, che viene caricato dopo.
        val w = chiave("discordwebhook")
        assertEquals("https://vero/segreto", w.value)
        assertEquals(ServerParams.Da.SEGRETI_ISTANZA, w.da)
        assertEquals("https://esempio/finto", w.nostro)
        assertTrue(w.coperto)
        assertFalse(w.modificabile)
    }

    @Test
    fun `tutto il resto resta modificabile`() {
        val coperti = ServerParams.parseChain(letto).filter { it.coperto }.map { it.key }
        assertEquals(listOf("discordwebhook"), coperti)
    }

    @Test
    fun `l'elenco e' completo e in ordine`() {
        val chiavi = ServerParams.parseChain(letto).map { it.key }
        assertEquals(chiavi.sorted(), chiavi)
        assertTrue(chiavi.containsAll(listOf("javaram", "maxbackups", "branch", "postalert")))
        assertEquals(chiavi.size, chiavi.distinct().size)
    }

    @Test
    fun `il comando legge tutti e cinque i file e salta quelli che non ci sono`() {
        val cmd = ServerParams.readChain(cfg)
        listOf("_default", "common", "secrets-common", "secrets-").forEach {
            assertTrue(it, cmd.contains(it))
        }
        assertTrue(cmd.contains("[ -f \"\$f\" ] || continue"))
        assertTrue(cmd.contains("exit ${ServerParams.EXIT_NO_CONFIG}"))
    }

    @Test
    fun `ogni provenienza si sa spiegare`() {
        ServerParams.Da.entries.forEach {
            assertTrue(it.name, it.etichetta.isNotBlank())
            assertTrue(it.name, it.spiegazione.length > 20)
        }
        // L'ordine è quello di caricamento, e da quello dipende chi vince.
        assertTrue(ServerParams.Da.ISTANZA.ordinal > ServerParams.Da.COMUNE.ordinal)
        assertTrue(ServerParams.Da.SEGRETI_ISTANZA.ordinal > ServerParams.Da.ISTANZA.ordinal)
    }
}
