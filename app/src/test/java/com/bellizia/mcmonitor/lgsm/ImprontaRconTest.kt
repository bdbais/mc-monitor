package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il confronto fra la password RCON che ha l'app e quella scritta sul server.
 *
 * Serve a rispondere all'unica domanda che conta quando l'autenticazione
 * fallisce: c'è da correggere qualcosa o c'è solo da aspettare. Il log di un
 * server vero mostrava il collegamento aperto e chiuso nello stesso secondo,
 * cinque secondi dopo che il server aveva finito di avviarsi — quindi non era
 * l'avvio, ma dal messaggio non si poteva sapere.
 */
class ImprontaRconTest {

    private val cfg = ServerConfig(host = "h", user = "u", lgsmDir = "~/server2")

    @Test
    fun `l'impronta e' otto caratteri esadecimali`() {
        val i = Lgsm.impronta("segretissima123")
        assertEquals(8, i.length)
        assertTrue("non e' esadecimale: $i", i.all { it in "0123456789abcdef" })
    }

    @Test
    fun `password diverse danno impronte diverse`() {
        assertNotEquals(Lgsm.impronta("giusta12345"), Lgsm.impronta("sbagliata12345"))
        // Anche per un carattere solo: e' il caso di un errore di battitura.
        assertNotEquals(Lgsm.impronta("segreta1234"), Lgsm.impronta("segreta1235"))
    }

    @Test
    fun `la stessa password da' sempre la stessa impronta`() {
        assertEquals(Lgsm.impronta("segreta1234"), Lgsm.impronta("segreta1234"))
    }

    @Test
    fun `l'impronta e' quella di sha256, non un'invenzione`() {
        // Deve combaciare con quella che calcola `sha256sum` sul server, o il
        // confronto direbbe «diverse» ogni volta e l'app manderebbe a
        // riscrivere una password che era gia' giusta.
        // sha256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ba7816bf", Lgsm.impronta("abc"))
    }

    // ------------------------------------------------------ il comando remoto

    @Test
    fun `il comando non passa mai la password a un programma`() {
        // Un argomento finisce nell'elenco dei processi, e li' lo legge
        // chiunque abbia un'utenza su quella macchina.
        val comando = Lgsm.rconPasswordFingerprint(cfg)
        assertTrue("non usa un condotto: $comando", comando.contains("| sha256sum"))
        assertFalse(comando.contains("sha256sum \""))
        assertFalse(comando.contains("echo \$"))
    }

    @Test
    fun `il comando legge il file giusto e taglia il valore, non la riga`() {
        val comando = Lgsm.rconPasswordFingerprint(cfg)
        assertTrue(comando.contains("server.properties"))
        // Con `grep` l'impronta sarebbe quella di "rcon.password=segreta" e non
        // di "segreta": diversa da quella che calcola l'app, sempre.
        assertTrue("non toglie la chiave: $comando", comando.contains("s/^rcon\\.password=//p"))
    }

    @Test
    fun `l'a capo non entra nell'impronta`() {
        // `sed` stampa il valore con l'a capo in fondo: se finisse dentro,
        // l'impronta del server non combacerebbe mai con quella dell'app.
        assertTrue(Lgsm.rconPasswordFingerprint(cfg).contains("tr -d"))
    }

    @Test
    fun `se non si puo' calcolare lo dice invece di dare una stringa vuota`() {
        // Una risposta vuota confrontata con un'impronta vera darebbe «diverse»,
        // e manderebbe a correggere una password che poteva essere giusta.
        assertTrue(Lgsm.rconPasswordFingerprint(cfg).contains("IMPRONTA_NON_CALCOLABILE"))
    }

    @Test
    fun `la tilde della cartella viene espansa`() {
        // Dentro apici singoli `~` resta una tilde e il file non si trova mai.
        val comando = Lgsm.rconPasswordFingerprint(cfg)
        assertFalse("la tilde e' rimasta cruda: $comando", comando.contains("'~/"))
        assertTrue(comando.contains("\$HOME"))
    }
}
