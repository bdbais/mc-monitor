package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Due amministratori, una password sola.
 *
 * Il difetto che queste prove tengono chiuso non era un caso limite: due
 * persone premono «genera», ognuna scrive la sua nel file, e da quel momento
 * funziona solo l'ultima che ha premuto. Appena l'altra prova a sistemare si
 * invertono, e si rompono a vicenda per sempre vedendo solo «password
 * rifiutata».
 */
class RconCondivisaTest {

    @Test
    fun `se il server ne ha gia' una, si prende quella`() {
        // E' la regola che chiude il difetto: il file del server comanda.
        val scelta = RconCondivisa.decidi(nostra = "laMia123", sulServer = "quellaDiLa456")
        assertEquals(RconCondivisa.Scelta.Adotta("quellaDiLa456"), scelta)
    }

    @Test
    fun `se il server non ne ha, si scrive la nostra`() {
        assertEquals(
            RconCondivisa.Scelta.Scrivi("laMia123"),
            RconCondivisa.decidi(nostra = "laMia123", sulServer = "")
        )
        assertEquals(
            RconCondivisa.Scelta.Scrivi("laMia123"),
            RconCondivisa.decidi(nostra = "laMia123", sulServer = "   ")
        )
    }

    @Test
    fun `se sono uguali non si tocca niente`() {
        assertEquals(
            RconCondivisa.Scelta.Uguali,
            RconCondivisa.decidi(nostra = "uguale123", sulServer = "uguale123")
        )
    }

    @Test
    fun `senza niente da nessuna parte si genera`() {
        assertEquals(RconCondivisa.Scelta.DaGenerare, RconCondivisa.decidi("", ""))
    }

    @Test
    fun `il telefono senza password prende quella del server`() {
        // Un secondo amministratore che apre l'app per la prima volta non deve
        // generare niente: la password esiste gia', basta leggerla.
        assertEquals(
            RconCondivisa.Scelta.Adotta("quellaDelServer"),
            RconCondivisa.decidi(nostra = "", sulServer = "quellaDelServer")
        )
    }

    @Test
    fun `due telefoni con password diverse convergono, non si alternano`() {
        // La prova del difetto vero: si simulano i due amministratori, e alla
        // fine devono usare la stessa password. Con la regola di prima -- ognuno
        // scrive la sua -- questa non passerebbe mai.
        val sulServer = "primaScritta"
        val a = RconCondivisa.decidi(nostra = "quellaDiAnna", sulServer = sulServer)
        val b = RconCondivisa.decidi(nostra = "quellaDiBruno", sulServer = sulServer)
        assertEquals(RconCondivisa.Scelta.Adotta(sulServer), a)
        assertEquals(RconCondivisa.Scelta.Adotta(sulServer), b)
    }

    // ------------------------------------------------- cosa si accetta di leggere

    @Test
    fun `una password vera si accetta`() {
        assertTrue(RconCondivisa.utilizzabile("aB3-x_9.Kq"))
        // Anche con caratteri che l'app non scriverebbe mai: e' di qualcun
        // altro, e non essere della nostra forma non la rende sbagliata.
        assertTrue(RconCondivisa.utilizzabile("mia#password!2026"))
    }

    @Test
    fun `una riga vuota o con spazi non e' una password`() {
        // Una riga di errore finita dentro per sbaglio ha quasi sempre spazi:
        // adottarla vorrebbe dire rompere RCON credendo di ripararlo.
        assertFalse(RconCondivisa.utilizzabile(""))
        assertFalse(RconCondivisa.utilizzabile("   "))
        assertFalse(RconCondivisa.utilizzabile("No such file or directory"))
    }

    @Test
    fun `una riga assurdamente lunga non e' una password`() {
        assertFalse(RconCondivisa.utilizzabile("x".repeat(500)))
    }

    // ------------------------------------------------------------- gli avvisi

    @Test
    fun `l'avviso della password dice anche cosa fare`() {
        // Un avviso che dice solo «ho cambiato una cosa» lascia l'altro con
        // l'app rotta e nessuna istruzione: quasi peggio del silenzio.
        val testo = RconCondivisa.avvisoCambioPassword()
        assertTrue(testo.contains("RCON"))
        assertTrue("non dice cosa fare: $testo", testo.contains("cerca il guasto"))
    }

    @Test
    fun `nessun avviso contiene la password`() {
        // Sarebbe il modo piu' rapido di mettere la password del server nel
        // registro di un altro telefono.
        val avvisi = listOf(
            RconCondivisa.avvisoCambioPassword(),
            RconCondivisa.avvisoRiavvio(),
            RconCondivisa.avvisoMappa("BlueMap"),
            RconCondivisa.avvisoMod(3),
            RconCondivisa.avvisoBackup(),
        )
        avvisi.forEach { assertFalse("un avviso contiene una password", it.contains("password:")) }
        assertTrue(avvisi.all { it.isNotBlank() })
    }

    @Test
    fun `gli avvisi dicono che il server e' stato riavviato quando lo e' stato`() {
        // E' la cosa che sorprende di piu' chi sta giocando.
        assertTrue(RconCondivisa.avvisoRiavvio().contains("riavviato"))
        assertTrue(RconCondivisa.avvisoMappa("Dynmap").contains("riavviato"))
        assertTrue(RconCondivisa.avvisoMappa("Dynmap").contains("Dynmap"))
    }

    @Test
    fun `un'impostazione cambiata dice quale, da cosa e a cosa`() {
        // «Ho cambiato le impostazioni» non permette a chi legge di capire se
        // lo riguarda. Il valore vecchio conta quanto quello nuovo: e' l'unico
        // modo per accorgersi che e' stato toccato qualcosa messo apposta.
        val a = RconCondivisa.avvisoImpostazione("online-mode", "true", "false")
        assertTrue(a.contains("online-mode"))
        assertTrue("manca il valore di prima: $a", a.contains("true"))
        assertTrue("manca il valore nuovo: $a", a.contains("false"))
    }

    @Test
    fun `gli avvisi che rompono il gioco agli altri lo dicono`() {
        // Sono i tre casi in cui un altro amministratore si ritrova fuori senza
        // capire perche'.
        assertTrue(RconCondivisa.avvisoVersione("26.2", "26.3").contains("non entrerà"))
        assertTrue(RconCondivisa.avvisoModRimossa("Sodium").contains("riavvio"))
        assertTrue(RconCondivisa.avvisoRipristino("3 settembre").contains("non c'è più"))
    }

    @Test
    fun `spegnere e riaccendere una mod si dicono in modo diverso`() {
        assertTrue(RconCondivisa.avvisoModSpenta("JEI", accesa = false).contains("spento"))
        assertTrue(RconCondivisa.avvisoModSpenta("JEI", accesa = true).contains("riacceso"))
    }

    @Test
    fun `una mod sola non diventa «1 mod»`() {
        assertTrue(RconCondivisa.avvisoMod(1).contains("una mod"))
        assertTrue(RconCondivisa.avvisoMod(4).contains("4 mod"))
    }
}
