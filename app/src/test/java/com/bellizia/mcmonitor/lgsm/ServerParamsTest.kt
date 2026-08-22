package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I comandi che modificano la configurazione del server.
 *
 * Le prove sul valore non sono pignoleria: provati in una shell vera, i comandi
 * generati prima di queste correzioni scrivevano una riga che spezzava il file di
 * LinuxGSM in due, e la seconda metà diventava un comando.
 */
class ServerParamsTest {

    private val cfg = ServerConfig(lgsmDir = "server", script = "mcserver")

    @Test
    fun `la virgoletta viene protetta`() {
        // Senza protezione l'assegnazione si chiuderebbe su "ciao" e il resto
        // della riga finirebbe alla shell.
        assertEquals("""echo \"ciao\"""", ServerParams.quoteValue("""echo "ciao""""))
    }

    @Test
    fun `barra e apice inverso vengono protetti`() {
        assertEquals("""a\\b""", ServerParams.quoteValue("""a\b"""))
        assertEquals("""\`whoami\`""", ServerParams.quoteValue("`whoami`"))
    }

    @Test
    fun `il dollaro resta com'e`() {
        // Le configurazioni di LinuxGSM usano ${'$'}{serverfiles}: proteggerlo
        // arriverebbe al server scritto alla lettera invece che espanso.
        val v = "-Dpath=\${serverfiles}/mods"
        assertEquals(v, ServerParams.quoteValue(v))
    }

    @Test
    fun `l'a capo non spezza la riga`() {
        assertEquals("uno due", ServerParams.quoteValue("uno\ndue"))
        assertEquals("uno due", ServerParams.quoteValue("uno\r\ndue"))
    }

    @Test
    fun `il valore viene troncato`() {
        assertEquals(300, ServerParams.quoteValue("x".repeat(500)).length)
    }

    @Test
    fun `la riga passa ad awk dall'ambiente`() {
        // `awk -v` interpreta a sua volta le sequenze di escape e disferebbe
        // quoteValue: il valore deve arrivare dall'ambiente.
        val cmd = ServerParams.set(cfg, "javaram", "4G")
        assertTrue(cmd.contains("MCM_RIGA="))
        assertTrue(cmd.contains("""ENVIRON["MCM_RIGA"]"""))
        assertFalse(cmd.contains("awk -v"))
    }

    @Test
    fun `prima di scrivere fa una copia`() {
        val cmd = ServerParams.set(cfg, "javaram", "4G")
        assertTrue(cmd.contains("cp \"\$f\" \"\$f.mcmonitor.bak."))
    }

    @Test
    fun `senza configurazione esce con il suo codice`() {
        assertTrue(ServerParams.set(cfg, "javaram", "4G").contains("exit ${ServerParams.EXIT_NO_CONFIG}"))
        assertTrue(ServerParams.disable(cfg, "javaram").contains("exit ${ServerParams.EXIT_NO_CONFIG}"))
        assertTrue(ServerParams.readAll(cfg).contains("exit ${ServerParams.EXIT_NO_CONFIG}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una chiave inventata viene rifiutata`() {
        ServerParams.set(cfg, "javaram; rm -rf ~", "4G")
    }

    @Test
    fun `le chiavi buone passano`() {
        assertTrue(ServerParams.isValidKey("mcversion"))
        assertTrue(ServerParams.isValidKey("_x1"))
        assertFalse(ServerParams.isValidKey("1x"))
        assertFalse(ServerParams.isValidKey("con spazio"))
        assertFalse(ServerParams.isValidKey(""))
    }

    @Test
    fun `legge il file di configurazione`() {
        val righe = ServerParams.parse(
            """
            ## Impostazioni
            mcversion="1.20.1"
            #javaram="2G"
            port=25565
            non e' una assegnazione
            """.trimIndent()
        )
        assertEquals(3, righe.size)
        assertEquals(ServerParam("mcversion", "1.20.1", false), righe[0])
        assertEquals(ServerParam("javaram", "2G", true), righe[1])
        assertEquals(ServerParam("port", "25565", false), righe[2])
    }

    @Test
    fun `i parametri gia' presenti non vengono riproposti`() {
        val presenti = listOf(
            ServerParam("mcversion", "1.20.1", false),
            ServerParam("javaram", "2G", true)
        )
        val proponibili = ServerParams.addable(presenti).map { it.first }
        assertFalse(proponibili.contains("mcversion"))
        // Commentato vuol dire spento: si può riaccendere.
        assertTrue(proponibili.contains("javaram"))
    }

    @Test
    fun `riconosce l'esito`() {
        assertTrue(ServerParams.written("SCRITTO\n5:javaram=\"4G\""))
        assertTrue(ServerParams.written("DISATTIVATO"))
        assertFalse(ServerParams.written("CONFIG NON TROVATA"))
    }

    @Test
    fun `ogni parametro del catalogo e' spiegato`() {
        ServerParams.catalogue.forEach { (chiave, testo) ->
            assertTrue(chiave, ServerParams.isValidKey(chiave))
            assertTrue(chiave, testo.length > 20)
        }
    }
}
