package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dentro apici singoli la shell non espande la tilde: `cd '~/server1'` cercherebbe
 * una cartella chiamata davvero "~". Questi test bloccano quella regressione.
 */
class PathTest {

    @Test
    fun `la tilde diventa HOME fuori dagli apici`() {
        assertEquals("\"\$HOME\"/'server1'", Lgsm.path("~/server1"))
        assertEquals("\"\$HOME\"/'server1/serverfiles/mods'", Lgsm.path("~/server1/serverfiles/mods"))
        assertEquals("\"\$HOME\"", Lgsm.path("~"))
    }

    @Test
    fun `i percorsi assoluti restano quotati come prima`() {
        assertEquals("'/home/mcserver'", Lgsm.path("/home/mcserver"))
        assertEquals("'/mnt/10g/minecraft'", Lgsm.path("/mnt/10g/minecraft"))
    }

    @Test
    fun `gli apostrofi nel percorso non rompono il comando`() {
        assertEquals("'/srv/l'\\''istanza'", Lgsm.path("/srv/l'istanza"))
    }

    @Test
    fun `i comandi costruiti con la tilde restano validi`() {
        val cfg = ServerConfig(lgsmDir = "~/server2", script = "mcserver")

        val details = Lgsm.details(cfg)
        assertTrue(details.contains("cd \"\$HOME\"/'server2'"))
        // La tilde non deve sopravvivere dentro gli apici da nessuna parte.
        assertFalse(details.contains("'~"))

        assertFalse(Lgsm.tailLog(cfg).contains("'~"))
        assertFalse(Mods.listMods(cfg).contains("'~"))
        assertFalse(GameVersion.readConfig(cfg).contains("'~"))
        assertFalse(Provision.inspect(cfg).contains("'~"))
        assertTrue(GameVersion.configPath(cfg).startsWith("~/server2/lgsm/config-lgsm"))
    }

    @Test
    fun `serverfiles segue la cartella del server`() {
        val cfg = ServerConfig(lgsmDir = "~/server3")
        assertEquals("~/server3/serverfiles", cfg.serverFiles)
    }
}
