package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trovare la porta su cui si è messa la mappa.
 *
 * I tre programmi scrivono la configurazione in tre formati diversi: leggerla
 * vorrebbe dire conoscerli tutti e tre per sempre. Si guardano invece le porte
 * in ascolto, che sono vere comunque sia scritta la configurazione — ma allora
 * bisogna saper togliere quelle che sono di altri, o si finisce per aprire SSH
 * in un browser.
 */
class MappaWebTest {

    /** Come risponde `ss -ltn` su una macchina vera. */
    private val ascolto = """
        State  Recv-Q Send-Q Local Address:Port Peer Address:Port
        LISTEN 0      128          0.0.0.0:22
        LISTEN 0      50              *:25565
        LISTEN 0      50              *:25575
        LISTEN 0      128             *:8100
    """.trimIndent()

    @Test
    fun `resta la porta della mappa e non quelle che sappiamo gia'`() {
        val candidate = MappaWeb.porteCandidate(ascolto, gioco = 25565, rcon = 25575)
        assertEquals(listOf(8100), candidate)
    }

    @Test
    fun `la porta di SSH non finisce mai fra le candidate`() {
        // Aprire la 22 in un browser non mostra una mappa: mostra niente, e chi
        // ha premuto pensa che l'installazione sia fallita.
        assertFalse(22 in MappaWeb.porteCandidate(ascolto, 25565, 25575))
    }

    @Test
    fun `le porte del gioco e di RCON dipendono dal server, non sono fisse`() {
        // Chi ha spostato il gioco sulla 25566 non deve vederselo proposto come
        // se fosse una mappa.
        val altro = ascolto.replace("25565", "25566").replace("25575", "25576")
        assertEquals(listOf(8100), MappaWeb.porteCandidate(altro, gioco = 25566, rcon = 25576))
    }

    @Test
    fun `un sito sulla 80 non e' la mappa di Minecraft`() {
        // Su una macchina che fa anche altro c'e' quasi sempre un nginx. Il
        // server di Minecraft non gira da amministratore e una porta sotto la
        // 1024 non la puo' aprire: quindi quelle non sono sue, e proporle
        // vorrebbe dire aprire il sito di qualcun altro credendo di aprire la
        // mappa.
        val conNginx = ascolto + """

            LISTEN 0      511          0.0.0.0:80
            LISTEN 0      511          0.0.0.0:443
        """.trimIndent()
        assertEquals(listOf(8100), MappaWeb.porteCandidate(conNginx, 25565, 25575))
    }

    @Test
    fun `senza niente in ascolto non si inventa una porta`() {
        assertTrue(MappaWeb.porteCandidate("NESSUNA", 25565, 25575).isEmpty())
        assertTrue(MappaWeb.porteCandidate("", 25565, 25575).isEmpty())
    }

    // ------------------------------------------------------- quale proporre

    private val bluemap = MappaWeb.perSlug("bluemap")!!

    @Test
    fun `la porta predefinita vince quando c'e'`() {
        assertEquals(8100, MappaWeb.portaDellaMappa(bluemap, listOf(8100, 9000)))
    }

    @Test
    fun `se ne resta una sola e' quella, anche se non e' la predefinita`() {
        // Chi ha spostato la mappa su un'altra porta non deve rifare tutto a mano.
        assertEquals(9000, MappaWeb.portaDellaMappa(bluemap, listOf(9000)))
    }

    @Test
    fun `con piu' porte e nessuna predefinita non si tira a indovinare`() {
        // Sbagliare porta mostra una pagina bianca, che si legge come
        // «l'installazione e' fallita» anche quando e' andata bene.
        assertNull(MappaWeb.portaDellaMappa(bluemap, listOf(9000, 9001)))
        assertNull(MappaWeb.portaDellaMappa(bluemap, emptyList()))
    }

    // ------------------------------------------------------ cosa c'e' gia'

    @Test
    fun `riconosce una mappa gia' installata dal nome del file`() {
        val mod = listOf("fabric-api-0.159.0.jar", "BlueMap-5.4-fabric.jar", "jei.jar")
        assertEquals("BlueMap", MappaWeb.installata(mod)?.nome)
    }

    @Test
    fun `la riconosce anche spenta`() {
        // LinuxGSM e le mod si spengono rinominando il file: «ce l'hai gia', e'
        // solo spenta» e' una risposta diversa da «non ce l'hai».
        assertEquals("Dynmap", MappaWeb.installata(listOf("Dynmap-3.7-fabric.jar.disabled"))?.nome)
    }

    @Test
    fun `senza nessuna mappa non ne inventa una`() {
        assertNull(MappaWeb.installata(listOf("fabric-api.jar", "journeymap-26.2.jar")))
    }

    @Test
    fun `journeymap non e' una mappa web`() {
        // Sta sul server ma disegna sul client: proporre di aprirla in un
        // browser sarebbe una promessa che nessuno puo' mantenere.
        assertNull(MappaWeb.installata(listOf("journeymap-26.2-6.0.7.jar")))
    }

    // --------------------------------------------------------- il tunnel

    @Test
    fun `l'indirizzo del tunnel non si salva`() {
        // La porta locale la sceglie il tunnel ogni volta: salvarla vorrebbe
        // dire ritrovarsi domani un indirizzo che non porta da nessuna parte.
        assertTrue(MappaWeb.daNonSalvare(MappaWeb.indirizzoNelTunnel(41337)))
        assertTrue(MappaWeb.daNonSalvare("http://localhost:8100"))
        assertFalse(MappaWeb.daNonSalvare("https://mappa.example.com"))
    }

    @Test
    fun `il catalogo non ha due mappe sulla stessa porta`() {
        val porte = MappaWeb.CATALOGO.map { it.portaPredefinita }
        assertEquals(porte.size, porte.toSet().size)
    }

    @Test
    fun `ogni mappa e' spiegata a chi non ne conosce nessuna`() {
        MappaWeb.CATALOGO.forEach {
            assertTrue("${it.nome} non e' spiegata", it.comeE.length > 40)
            assertNull("${it.nome}: due mappe con lo stesso slug",
                MappaWeb.CATALOGO.firstOrNull { altra -> altra !== it && altra.slug == it.slug })
        }
    }
}
