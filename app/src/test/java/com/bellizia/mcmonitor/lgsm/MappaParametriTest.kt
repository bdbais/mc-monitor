package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Le poche impostazioni delle mappe che si cambiano dall'app. */
class MappaParametriTest {

    private val cfg = ServerConfig(serverFilesDir = "/home/mc/serverfiles")
    private val bluemap = MappaWeb.perSlug("bluemap")!!
    private val dynmap = MappaWeb.perSlug("dynmap")!!
    private val squaremap = MappaWeb.perSlug("squaremap")!!

    @Test
    fun `ogni mappa ha il permesso o l'interruttore che la fa partire`() {
        MappaWeb.CATALOGO.forEach { mappa ->
            val p = MappaParametri.per(mappa)
            assertTrue("${mappa.nome} non ha parametri", p.isNotEmpty())
            assertNotNull("${mappa.nome} senza porta", p.firstOrNull { it.id == "porta" })
            assertNotNull("${mappa.nome} senza interruttore", p.firstOrNull { it.id == "enabled" })
        }
    }

    @Test
    fun `i percorsi non sono quelli che verrebbe da indovinare`() {
        // Solo BlueMap sta sotto config/. Le altre due hanno una cartella loro
        // nella radice del server, e cercarle sotto config/ vuol dire non
        // trovare niente e dire che la mappa non e' installata.
        assertTrue(MappaParametri.file(bluemap).all { it.startsWith("config/bluemap/") })
        assertEquals(listOf("dynmap/configuration.txt"), MappaParametri.file(dynmap))
        assertEquals(listOf("squaremap/config.yml"), MappaParametri.file(squaremap))
    }

    @Test
    fun `l'interruttore di Dynmap e' scritto al contrario nel file`() {
        // `disable-webserver: false` vuol dire acceso. Se l'app lo mostrasse
        // com'e' scritto, chi lo accende lo spegnerebbe.
        val acceso = MappaParametri.per(dynmap).first { it.id == "enabled" }
        assertTrue(acceso.invertito)
        assertEquals("true", MappaParametri.aSchermo(acceso, "false"))
        assertEquals("false", MappaParametri.aSchermo(acceso, "true"))
        assertEquals("false", MappaParametri.nelFile(acceso, "true"))
        assertEquals("true", MappaParametri.nelFile(acceso, "false"))
    }

    @Test
    fun `le virgolette si mettono solo dove il file le vuole`() {
        val blu = MappaParametri.per(bluemap).first { it.id == "indirizzo" }
        val quadra = MappaParametri.per(squaremap).first { it.id == "indirizzo" }
        assertEquals("\"127.0.0.1\"", MappaParametri.nelFile(blu, "127.0.0.1"))
        assertEquals("127.0.0.1", MappaParametri.nelFile(quadra, "127.0.0.1"))
    }

    @Test
    fun `senza il permesso BlueMap non parte, e si vede`() {
        val permesso = MappaParametri.per(bluemap).first { it.id == "accept-download" }
        assertNotNull(permesso.avviso?.invoke("false"))
        assertNull(permesso.avviso?.invoke("true"))
    }

    @Test
    fun `una porta sotto la 1024 si rifiuta dicendo perche'`() {
        val porta = MappaParametri.per(bluemap).first { it.id == "porta" }
        assertNotNull(MappaParametri.controlla(porta, "80"))
        assertNotNull(MappaParametri.controlla(porta, "ottantuno"))
        assertNotNull(MappaParametri.controlla(porta, ""))
        assertNull(MappaParametri.controlla(porta, "8100"))
    }

    @Test
    fun `true e false non si dicono a voce`() {
        val permesso = MappaParametri.per(bluemap).first { it.id == "accept-download" }
        val porta = MappaParametri.per(bluemap).first { it.id == "porta" }
        assertEquals("acceso", MappaParametri.etichetta(permesso, "true"))
        assertEquals("spento", MappaParametri.etichetta(permesso, "false"))
        assertEquals("8100", MappaParametri.etichetta(porta, "8100"))
    }

    // -------------------------------------------------------- server e ritorno

    @Test
    fun `i file si leggono in un giro solo, con i loro nomi`() {
        val comando = MappaParametri.comandoLeggi(cfg, bluemap)
        assertTrue(comando.contains("=== FILE config/bluemap/core.conf"))
        assertTrue(comando.contains("=== FILE config/bluemap/webserver.conf"))
        assertTrue(comando.contains("/home/mc/serverfiles/config/bluemap/core.conf"))
        assertEquals(2, Regex("=== FILE").findAll(comando).count())
    }

    @Test
    fun `la risposta si divide file per file`() {
        val risposta = "=== FILE config/bluemap/core.conf\n" +
                "accept-download: false\n" +
                "=== FILE config/bluemap/webserver.conf\n" +
                "port: 8100\n"
        val letti = MappaParametri.leggiFile(risposta)
        assertEquals(2, letti.size)
        assertEquals("accept-download: false", letti["config/bluemap/core.conf"])
        assertEquals("port: 8100", letti["config/bluemap/webserver.conf"])
    }

    @Test
    fun `un file che non c'e' non compare`() {
        // Prima del primo avvio con la mod dentro i file non esistono: `cat` non
        // stampa niente. Un file vuoto e un file assente qui sono la stessa
        // cosa -- non c'e' niente da mostrare e niente da cambiare -- e
        // mostrarne uno vuoto farebbe credere che le impostazioni siano sparite.
        val risposta = "=== FILE dynmap/configuration.txt\n"
        assertTrue(MappaParametri.leggiFile(risposta).isEmpty())
    }

    @Test
    fun `il file si manda in base64, con la copia di sicurezza prima`() {
        // Un file di configurazione e' pieno di apici, virgolette, dollari e
        // cancelletti: passato fra virgolette in una riga di shell, uno solo di
        // quelli al posto sbagliato non da' errore -- riscrive il file storto.
        val testo = "accept-download: true\nmotd: \"c'e' un 'apice' e un \$dollaro\"\n"
        val comando = MappaParametri.comandoScrivi(cfg, "config/bluemap/core.conf", testo)
        val atteso = Base64.getEncoder().encodeToString(testo.toByteArray(Charsets.UTF_8))
        assertTrue(comando.contains(atteso))
        assertTrue("niente apici del testo nel comando", !comando.contains("un 'apice'"))
        assertTrue(comando.contains("cp \"\$f\""))
        assertTrue(comando.contains("base64 -d"))
        // Prima la copia, poi la scrittura: al contrario, la copia sarebbe del
        // file gia' cambiato.
        assertTrue(comando.indexOf("cp \"\$f\"") < comando.indexOf("base64 -d"))
    }

    @Test
    fun `si scrive solo se il server dice che ha scritto`() {
        assertTrue(MappaParametri.scritto("SCRITTO\n"))
        assertTrue(!MappaParametri.scritto("FILE NON TROVATO"))
        assertTrue(!MappaParametri.scritto(""))
    }
}
