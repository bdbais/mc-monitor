package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cambiare una riga nei file delle tre mappe.
 *
 * I pezzi di file qui sotto sono quelli veri, presi dai sorgenti dei tre
 * programmi: e' li' che stanno le trappole, e inventarli vorrebbe dire
 * inventarsi anche quelle. Dynmap tiene una porta di database commentata sopra
 * la porta del sito, BlueMap mette righe fra virgolette dentro un blocco, e
 * squaremap ha tre chiavi che si chiamano allo stesso modo a rientri diversi.
 */
class ConfigTestoTest {

    private val blueCore = """
        ## ...
        accept-download: false

        data: "bluemap"
        render-thread-count: 3
        update-cooldown: 60
        full-update-interval: 1440
        scan-for-mod-resources: true

        log: {
          file: "bluemap/logs/debug.log"
          append: false
        }
    """.trimIndent()

    private val blueWeb = """
        enabled: true
        webroot: "bluemap/web"
        port: 8100
        sse-enabled: true

        log: {
          file: "bluemap/logs/webserver.log"
          append: false
        }

        additional-headers: {
          "Cache-Control": "no-cache"
          "X-Frame-Options": "SAMEORIGIN"
        }
    """.trimIndent()

    private val dynmap = """
        storage:
          type: sqlite
          #hostname: localhost
          #port: 3306
          #database: dynmap

        # The network-interface the webserver will bind to (0.0.0.0 for all interfaces).
        #webserver-bindaddress: 0.0.0.0

        # The TCP-port the webserver will listen on.
        webserver-port: 8123

        # Maximum concurrent session on internal web server
        max-sessions: 30

        # Disables Webserver portion of Dynmap (Advanced users only)
        disable-webserver: false
    """.trimIndent()

    private val squaremap = """
        config-version: 1

        settings:

          language-file: lang-en.yml
          debug-mode: false
          web-address: http://localhost:8080

          web-directory:
            path: web
            auto-update: true

          internal-webserver:
            enabled: true
            bind: 0.0.0.0
            port: 8080
            flush-json-immediately: false
    """.trimIndent()

    /** Quante righe sono diverse fra prima e dopo. */
    private fun differenze(prima: String, dopo: String): List<Pair<String, String>> {
        val a = prima.split("\n")
        val b = dopo.split("\n")
        assertEquals("il numero di righe non deve cambiare", a.size, b.size)
        return a.zip(b).filter { it.first != it.second }
    }

    // ------------------------------------------------------------- BlueMap

    @Test
    fun `l'interruttore che fa partire BlueMap`() {
        // Senza `accept-download: true` BlueMap non parte affatto: niente mappa,
        // nessuna porta aperta. E' la riga piu' importante di tutte e tre.
        assertEquals("false", ConfigTesto.leggi(blueCore, listOf("accept-download")))
        val dopo = ConfigTesto.scrivi(blueCore, listOf("accept-download"), "true")
        assertEquals("true", ConfigTesto.leggi(dopo, listOf("accept-download")))
        assertEquals(
            listOf("accept-download: false" to "accept-download: true"),
            differenze(blueCore, dopo)
        )
    }

    @Test
    fun `la porta di BlueMap, e il resto del file intatto`() {
        assertEquals("8100", ConfigTesto.leggi(blueWeb, listOf("port")))
        val dopo = ConfigTesto.scrivi(blueWeb, listOf("port"), "8123")
        assertEquals(listOf("port: 8100" to "port: 8123"), differenze(blueWeb, dopo))
    }

    @Test
    fun `le righe fra virgolette non sono chiavi`() {
        // In `additional-headers` ci sono righe come `"Cache-Control": "no-cache"`.
        // Hanno i due punti come tutte le altre, ma una chiave e' un nome
        // semplice: se passassero per chiavi, una ricerca ingenua le
        // scambierebbe per impostazioni.
        assertNull(ConfigTesto.leggi(blueWeb, listOf("Cache-Control")))
        assertNull(ConfigTesto.leggi(blueWeb, listOf("additional-headers", "Cache-Control")))
    }

    @Test
    fun `l'indirizzo di BlueMap non c'e' nel file e si aggiunge`() {
        // `ip` esiste nel programma ma non nel file scritto di fabbrica. Senza
        // il caso «non c'e', si aggiunge» sarebbe l'unica impostazione
        // impossibile da mettere da qui.
        assertNull(ConfigTesto.leggi(blueWeb, listOf("ip")))
        val dopo = ConfigTesto.scrivi(blueWeb, listOf("ip"), "\"127.0.0.1\"")
        assertEquals("127.0.0.1", ConfigTesto.leggi(dopo, listOf("ip")))
        // In fondo al file, non dentro l'ultimo blocco: li' non conterebbe niente.
        assertTrue(dopo.trimEnd().endsWith("ip: \"127.0.0.1\""))
        assertTrue(dopo.contains("\"X-Frame-Options\": \"SAMEORIGIN\""))
    }

    @Test
    fun `il separatore resta quello che era`() {
        // HOCON accetta sia i due punti che l'uguale. Cambiarlo di nascosto
        // sarebbe una modifica in piu' che nessuno ha chiesto.
        val conUguale = "accept-download = false"
        assertEquals("accept-download = true", ConfigTesto.scrivi(conUguale, listOf("accept-download"), "true"))
    }

    // -------------------------------------------------------------- Dynmap

    @Test
    fun `la porta del sito non e' quella del database`() {
        // Sopra `webserver-port` c'e' `#port: 3306`, che e' MySQL. Chi cerca
        // «port» e si ferma alla prima trovata cambia il database.
        assertEquals("8123", ConfigTesto.leggi(dynmap, listOf("webserver-port")))
        assertNull(ConfigTesto.leggi(dynmap, listOf("storage", "port")))
        assertTrue(ConfigTesto.commentata(dynmap, listOf("storage", "port")))

        val dopo = ConfigTesto.scrivi(dynmap, listOf("webserver-port"), "9000")
        assertEquals(listOf("webserver-port: 8123" to "webserver-port: 9000"), differenze(dynmap, dopo))
        assertTrue("il database non si tocca", dopo.contains("#port: 3306"))
    }

    @Test
    fun `una riga commentata vale come non scritta`() {
        // `#webserver-bindaddress: 0.0.0.0` non e' l'indirizzo scelto: e'
        // l'esempio stampato di fabbrica, e il programma non lo legge.
        assertNull(ConfigTesto.leggi(dynmap, listOf("webserver-bindaddress")))
        assertTrue(ConfigTesto.commentata(dynmap, listOf("webserver-bindaddress")))
    }

    @Test
    fun `scrivere su una riga commentata toglie il cancelletto`() {
        // Cambiare il valore lasciando il cancelletto non cambia niente e
        // sembra fatto: e' il modo peggiore in cui un'impostazione puo'
        // fallire.
        val dopo = ConfigTesto.scrivi(dynmap, listOf("webserver-bindaddress"), "127.0.0.1")
        assertEquals("127.0.0.1", ConfigTesto.leggi(dopo, listOf("webserver-bindaddress")))
        assertFalse(ConfigTesto.commentata(dopo, listOf("webserver-bindaddress")))
        assertEquals(
            listOf("#webserver-bindaddress: 0.0.0.0" to "webserver-bindaddress: 127.0.0.1"),
            differenze(dynmap, dopo)
        )
    }

    @Test
    fun `l'interruttore di Dynmap e' scritto al contrario`() {
        // `disable-webserver: false` vuol dire che il sito e' acceso. Chi lo
        // legge come «acceso: no» lo spegne credendo di accenderlo.
        assertEquals("false", ConfigTesto.leggi(dynmap, listOf("disable-webserver")))
    }

    // ----------------------------------------------------------- squaremap

    @Test
    fun `la chiave si indica per intero, non per nome`() {
        assertEquals(
            "8080",
            ConfigTesto.leggi(squaremap, listOf("settings", "internal-webserver", "port"))
        )
        assertEquals(
            "true",
            ConfigTesto.leggi(squaremap, listOf("settings", "internal-webserver", "enabled"))
        )
        assertEquals(
            "0.0.0.0",
            ConfigTesto.leggi(squaremap, listOf("settings", "internal-webserver", "bind"))
        )
    }

    @Test
    fun `un figlio non si cerca fuori dal blocco di suo padre`() {
        // `auto-update` sta sotto `web-directory`, non sotto `internal-webserver`.
        assertNull(ConfigTesto.leggi(squaremap, listOf("settings", "internal-webserver", "auto-update")))
        assertEquals("true", ConfigTesto.leggi(squaremap, listOf("settings", "web-directory", "auto-update")))
        // E `port` di primo livello non esiste: esiste solo dentro il webserver.
        assertNull(ConfigTesto.leggi(squaremap, listOf("port")))
    }

    @Test
    fun `il rientro di squaremap si mantiene`() {
        val dopo = ConfigTesto.scrivi(
            squaremap, listOf("settings", "internal-webserver", "port"), "8123"
        )
        assertEquals(listOf("    port: 8080" to "    port: 8123"), differenze(squaremap, dopo))
    }

    @Test
    fun `l'indirizzo che i giocatori vedono sta da un'altra parte`() {
        // Chi cambia la porta e non cambia questo lascia i giocatori a puntare
        // alla porta di ieri.
        assertEquals(
            "http://localhost:8080",
            ConfigTesto.leggi(squaremap, listOf("settings", "web-address"))
        )
        val dopo = ConfigTesto.scrivi(
            squaremap, listOf("settings", "web-address"), "http://localhost:8123"
        )
        assertEquals(
            listOf("  web-address: http://localhost:8080" to "  web-address: http://localhost:8123"),
            differenze(squaremap, dopo)
        )
    }

    @Test
    fun `una chiave nuova si mette dentro il blocco giusto`() {
        val dopo = ConfigTesto.scrivi(
            squaremap, listOf("settings", "internal-webserver", "flush-json-immediately"), "true"
        )
        assertEquals("true", ConfigTesto.leggi(dopo, listOf("settings", "internal-webserver", "flush-json-immediately")))

        val nuova = ConfigTesto.scrivi(squaremap, listOf("settings", "internal-webserver", "ip"), "1.2.3.4")
        assertEquals("1.2.3.4", ConfigTesto.leggi(nuova, listOf("settings", "internal-webserver", "ip")))
        assertTrue("va rientrata come le sue sorelle", nuova.contains("\n    ip: 1.2.3.4"))
    }

    @Test
    fun `senza il blocco che la contiene non si scrive niente`() {
        // Meglio non fare niente che inventarsi dove va messa: un file di cui
        // non si riconosce la forma non e' il file che si crede.
        val altro = "tutt'altro file\nsenza niente di utile\n"
        assertEquals(altro, ConfigTesto.scrivi(altro, listOf("settings", "internal-webserver", "port"), "8123"))
    }
}
