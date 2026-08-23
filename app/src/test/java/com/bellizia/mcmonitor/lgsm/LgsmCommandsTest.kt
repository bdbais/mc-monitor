package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I comandi dello script di LinuxGSM.
 *
 * L'elenco chiuso non e' prudenza generica: console, debug e install aspettano
 * una risposta dalla tastiera, e senza terminale LinuxGSM non si ferma — ripete
 * la domanda all'infinito finche' non si stacca la connessione.
 */
class LgsmCommandsTest {

    private val cfg = ServerConfig(lgsmDir = "~/server1", script = "mcserver")

    @Test
    fun `i comandi che aspettano una risposta non ci sono`() {
        val nomi = LgsmCommands.catalogo.map { it.nome }
        listOf("console", "debug", "install", "auto-install", "send", "clear-modules", "dev-debug")
            .forEach { assertFalse(it, nomi.contains(it)) }
    }

    @Test
    fun `ogni comando si spiega e ha un tetto di tempo`() {
        LgsmCommands.catalogo.forEach { c ->
            assertTrue(c.nome, c.titolo.isNotBlank())
            assertTrue(c.nome, c.cosaFa.length > 20)
            assertTrue(c.nome, c.secondi >= 60)
            // Quello che ferma il server o lo cambia deve dire cosa succede.
            if (c.pesante) assertTrue(c.nome, !c.conseguenza.isNullOrBlank())
        }
        val nomi = LgsmCommands.catalogo.map { it.nome }
        assertEquals(nomi.size, nomi.distinct().size)
    }

    @Test
    fun `quelli che spengono il server chiedono conferma`() {
        listOf("backup", "update", "update-lgsm", "monitor", "check-update").forEach {
            assertTrue(it, LgsmCommands.byName(it)!!.chiedeConferma)
        }
        // Leggere i dettagli non disturba nessuno.
        assertFalse(LgsmCommands.byName("details")!!.chiedeConferma)
    }

    @Test
    fun `quello che pubblica fuori si conferma due volte`() {
        val pd = LgsmCommands.byName("postdetails")!!
        assertTrue(pd.pubblica)
        assertTrue(pd.conseguenza!!.contains("PUBBLICA"))
    }

    @Test
    fun `il tetto di tempo sta sul server`() {
        // Un tetto messo dal telefono chiuderebbe solo la connessione: il comando
        // resterebbe a girare la, e un backup interrotto lascia un blocco che per
        // un'ora impedisce di rifarlo.
        val cmd = LgsmCommands.run(cfg, LgsmCommands.byName("backup")!!)
        assertTrue(cmd.contains("timeout 3600"))
        assertTrue(cmd.contains("@@TEMPOSCADUTO"))
        // E se timeout non c'e', il comando parte lo stesso.
        assertTrue(cmd.contains("command -v timeout"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un comando inventato non parte`() {
        LgsmCommands.run(cfg, ComandoLgsm(nome = "console", titolo = "x", cosaFa = "y"))
    }

    @Test
    fun `l'esito si legge da cosa ha scritto, non dal codice di uscita`() {
        // Un comando che non esiste esce con zero, start su un server gia' acceso
        // esce con due: il codice non e' un verdetto.
        assertTrue(LgsmCommands.esito("[ FAIL ] Starting mcserver").contains("non e' andato") ||
                LgsmCommands.esito("[ FAIL ] Starting mcserver").contains("non è andato"))
        assertTrue(LgsmCommands.esito("[ ERROR ] qualcosa").contains("errore"))
        assertTrue(LgsmCommands.esito("@@TEMPOSCADUTO").contains("troppo"))
        assertEquals("Fatto.", LgsmCommands.esito("[ OK ] Starting mcserver"))
    }

    @Test
    fun `byName trova solo quelli in elenco`() {
        assertNull(LgsmCommands.byName("console"))
        assertNull(LgsmCommands.byName("inventato"))
        assertEquals("backup", LgsmCommands.byName("backup")?.nome)
    }
}
