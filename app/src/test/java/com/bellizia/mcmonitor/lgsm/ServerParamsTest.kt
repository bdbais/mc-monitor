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
        val proponibili = ServerParams.addable(presenti).map { it.key }
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
        ServerParams.catalogue.forEach { info ->
            assertTrue(info.key, ServerParams.isValidKey(info.key))
            assertTrue(info.key, info.what.length > 20)
        }
    }

    @Test
    fun `il commento in coda non entra nel valore`() {
        // LinuxGSM dice di copiare le righe dal suo _default.cfg, e quelle righe
        // sono scritte cosi'. Prima il valore diventava tutta la riga, commento
        // compreso, e bastava riaprirla e salvarla per rompere l'avvio.
        val righe = ServerParams.parse("""
            javaram="1024" # -Xmx${'$'}1024M
            maxbackups="4"
            logdays=7 # giorni
            startparameters="-jar server.jar nogui"
        """.trimIndent())
        assertEquals("1024", righe.first { it.key == "javaram" }.value)
        assertEquals("4", righe.first { it.key == "maxbackups" }.value)
        assertEquals("7", righe.first { it.key == "logdays" }.value)
        assertEquals("-jar server.jar nogui", righe.first { it.key == "startparameters" }.value)
    }

    @Test
    fun `il cancelletto dentro il valore resta`() {
        val righe = ServerParams.parse("""servername="Casa#1"""")
        assertEquals("Casa#1", righe.first().value)
    }

    @Test
    fun `se la chiave c'e' due volte comanda l'ultima`() {
        // Il file viene eseguito dall'alto in basso: l'ultima assegnazione vince.
        // Mostrando la prima si diceva un valore che il server non usa.
        val righe = ServerParams.parse("""
            javaram="1024"
            maxbackups="4"
            javaram="4096"
        """.trimIndent())
        assertEquals(1, righe.count { it.key == "javaram" })
        assertEquals("4096", righe.first { it.key == "javaram" }.value)
    }

    @Test
    fun `nel catalogo non ci sono piu' le chiavi che LinuxGSM non legge`() {
        // Verificate sul _default.cfg di mcserver: non esistono. Porta, indirizzo
        // e nome, per Minecraft, LinuxGSM li prende da server.properties.
        val chiavi = ServerParams.catalogue.map { it.key }
        listOf("port", "queryport", "ip", "servername", "javaparms", "mcbranch").forEach {
            assertFalse(it, chiavi.contains(it))
        }
        assertEquals(16, chiavi.size)
        assertEquals(chiavi.size, chiavi.distinct().size)
    }

    @Test
    fun `il riavvio si propone solo a chi serve`() {
        assertTrue(ServerParams.needsRestart(listOf("javaram")))
        assertTrue(ServerParams.needsRestart(listOf("maxbackups", "startparameters")))
        // Riavviare non cambia la versione: quella vuole un aggiornamento.
        assertFalse(ServerParams.needsRestart(listOf("mcversion")))
        // Questi LinuxGSM li rilegge da solo a ogni esecuzione.
        assertFalse(ServerParams.needsRestart(listOf("maxbackups", "logdays", "discordalert")))
        assertFalse(ServerParams.needsRestart(emptyList()))
    }

    @Test
    fun `ogni voce dice quando avra' effetto`() {
        ServerParams.catalogue.forEach { info ->
            assertTrue(info.key, ServerParams.effectLabel(info.key).isNotBlank())
        }
        assertEquals("", ServerParams.effectLabel("inventato"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un valore vuoto viene rifiutato`() {
        // `javaram=""` diventa `java -XmxM -jar`: il server non parte piu'.
        ServerParams.set(cfg, "javaram", "   ")
    }
}
