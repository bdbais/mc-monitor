package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Perché il server non è partito: i log letti e tradotti. */
class AvvioTest {

    private val cfg = ServerConfig(lgsmDir = "~/server1", script = "mcserver")

    private fun raccolta(
        lock: String,
        console: String,
        avvio: String = "",
        stato: String = ""
    ) = """
        ### lock
        $lock
        ### script
        [ FAIL ] Starting mcserver
        ### console
        $console
        ### gioco
        [12:00:00] [Server thread/INFO]: Done (5.2s)!
        ### stato
        $stato
        ### avvio
        Command-line Parameters
        $avvio
        ### fine
    """.trimIndent()

    // ------------------------------------------- il lock che diceva bugie

    @Test
    fun `un lock rimasto li non fa dire che sta girando`() {
        // Il caso vero, visto su un server acceso davvero: LinuxGSM scrive
        // "-started.lock" quando lancia il server e non lo toglie se Java muore
        // subito dopo. La schermata diceva "il server sta girando" proprio a chi
        // l'aveva aperta perche' non partiva.
        val d = Avvio.leggi(
            raccolta("mcserver-started.lock", "qualcosa", stato = "Status:\tSTOPPED")
        )
        assertEquals(Stato.PARTITO_E_MORTO, d.stato)
    }

    @Test
    fun `se LinuxGSM dice che gira, gira`() {
        assertEquals(
            Stato.IN_ESECUZIONE,
            Avvio.leggi(raccolta("(nessun lock)", "", stato = "Status: STARTED")).stato
        )
    }

    @Test
    fun `fermo e senza lock vuol dire mai partito`() {
        assertEquals(
            Stato.MAI_PARTITO,
            Avvio.leggi(raccolta("(nessun lock)", "", stato = "Status: STOPPED")).stato
        )
    }

    @Test
    fun `lo stato si legge anche quando LinuxGSM lo colora`() {
        // Il primo tentativo di riparazione non funzionava proprio per questo: il
        // filtro girava sul testo ancora colorato, dove la riga non comincia con
        // "Status" ma con un codice di colore, e non trovava niente.
        val colorato = "[0;37mStatus:[0m\t[0;31mSTOPPED[0m"
        assertEquals(
            Stato.PARTITO_E_MORTO,
            Avvio.leggi(raccolta("mcserver-started.lock", "", stato = colorato)).stato
        )
    }

    @Test
    fun `se lo stato non si legge si torna a fidarsi del lock`() {
        assertEquals(
            Stato.IN_ESECUZIONE,
            Avvio.leggi(
                raccolta("mcserver-started.lock", "", stato = "(stato non leggibile)")
            ).stato
        )
    }

    @Test
    fun `il rumore delle chiamate dell'app non copre l'avvio andato male`() {
        // L'app chiama details di continuo e ogni chiamata lascia cinque righe:
        // con una coda corta l'errore di ieri e' gia' fuori dalla finestra.
        val cmd = Avvio.raccogli(cfg)
        assertTrue(cmd.contains("tail -n 600"))
        assertTrue(cmd.contains("Using cached IP"))
        assertTrue(cmd.contains("core_exit"))
        assertTrue(cmd, cmd.contains("### stato"))
    }

    @Test
    fun `details viene chiesto una volta sola`() {
        // Ci mette qualche secondo e sporca il log: chiederlo due volte per avere
        // stato e riga di avvio sarebbe il doppio del disturbo.
        val cmd = Avvio.raccogli(cfg)
        assertEquals(1, Regex("details").findAll(cmd).count())
    }

    @Test
    fun `capisce che non e' mai partito`() {
        assertEquals(Stato.MAI_PARTITO, Avvio.leggi(raccolta("(nessun lock)", "qualcosa")).stato)
    }

    @Test
    fun `capisce che sta girando`() {
        assertEquals(
            Stato.IN_ESECUZIONE,
            Avvio.leggi(raccolta("mcserver-started.lock", "qualcosa")).stato
        )
    }

    @Test
    fun `riconosce la riga di avvio rotta`() {
        // Il caso vero: due -jar, e il server esce subito.
        val d = Avvio.leggi(
            raccolta(
                "(nessun lock)",
                "Failed to start the minecraft server",
                "java -Xmx1024M -jar ./minecraft_server.jar -Xmx1024M -jar fabric-server-launch.jar nogui"
            )
        )
        assertTrue(d.haUnMotivo)
        assertTrue(d.motivo, d.motivo.contains("non capisce"))
        assertEquals("ripristino", d.dove)
        assertTrue(d.rigaDiAvvio!!.contains("fabric"))
    }

    @Test
    fun `riconosce il jar mancante`() {
        val d = Avvio.leggi(raccolta("(nessun lock)", "Error: Unable to access jarfile ./x.jar"))
        assertTrue(d.motivo, d.motivo.contains("non trova"))
        assertEquals("ripristino", d.dove)
    }

    @Test
    fun `riconosce la memoria finita`() {
        val d = Avvio.leggi(raccolta("(nessun lock)", "java.lang.OutOfMemoryError: Java heap space"))
        assertTrue(d.motivo, d.motivo.contains("memoria"))
        assertEquals("parametri", d.dove)
    }

    @Test
    fun `riconosce i mod incompatibili`() {
        val d = Avvio.leggi(raccolta("(nessun lock)", "Mod resolution failed: requires fabric-api"))
        assertEquals("mod", d.dove)
    }

    @Test
    fun `riconosce java troppo vecchio`() {
        val d = Avvio.leggi(
            raccolta("(nessun lock)", "UnsupportedClassVersionError: class file version 65.0")
        )
        assertTrue(d.motivo, d.motivo.contains("Java"))
    }

    @Test
    fun `quando non riconosce niente lo dice`() {
        val d = Avvio.leggi(raccolta("(nessun lock)", "una riga che non vuol dire niente"))
        assertFalse(d.haUnMotivo)
        assertEquals("", d.dove)
    }

    @Test
    fun `raccoglie tutti i pezzi in un giro solo`() {
        val cmd = Avvio.raccogli(cfg)
        listOf("### lock", "### script", "### console", "### gioco", "### avvio").forEach {
            assertTrue(it, cmd.contains(it))
        }
        // Con tail e non con cat: un log intero su rete mobile sono megabyte.
        assertTrue(cmd.contains("tail -n"))
    }

    @Test
    fun `il testo da incollare contiene tutto`() {
        val d = Avvio.leggi(raccolta("(nessun lock)", "Unable to access jarfile", "java -jar x.jar"))
        val testo = Avvio.perCopiare(d)
        assertTrue(testo.contains("Riga di avvio"))
        assertTrue(testo.contains("log di console"))
    }
}
