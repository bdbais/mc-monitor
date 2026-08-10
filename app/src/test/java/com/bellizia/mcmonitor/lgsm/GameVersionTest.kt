package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il file di configurazione reale contiene `mcversion="1.20.1"` e, a seconda
 * dell'installazione, `mcbranch` oppure `mcbranc`.
 */
class GameVersionTest {

    private val cfg = ServerConfig(lgsmDir = "/mnt/10g/minecraft", script = "mcserver")

    @Test
    fun `percorso della configurazione LinuxGSM`() {
        assertEquals(
            "/mnt/10g/minecraft/lgsm/config-lgsm/mcserver/mcserver.cfg",
            GameVersion.configPath(cfg)
        )
    }

    @Test
    fun `legge versione e ramo dal file`() {
        val raw = """
            ## Game Server Settings
            mcversion="1.20.1"
            mcbranch="release"
        """.trimIndent()
        val parsed = GameVersion.parseConfig(raw)
        assertEquals("1.20.1", parsed.version)
        assertEquals("release", parsed.branch)
        assertEquals("mcbranch", parsed.branchKey)
    }

    @Test
    fun `riconosce anche la variante mcbranc`() {
        val parsed = GameVersion.parseConfig("mcversion=\"1.21.10\"\nmcbranc=\"release\"\n")
        assertEquals("1.21.10", parsed.version)
        assertEquals("release", parsed.branch)
        // La chiave si conserva com'è scritta, per non aggiungerne una seconda.
        assertEquals("mcbranc", parsed.branchKey)
    }

    @Test
    fun `il comando di scrittura fa una copia e sostituisce la riga giusta`() {
        val command = GameVersion.setVersion(cfg, "1.21.4", "release", "mcbranch")
        assertTrue(command.contains("mcmonitor.bak"))
        assertTrue(command.contains("mcversion=\\\"1.21.4\\\"") || command.contains("mcversion=\"1.21.4\""))
        assertTrue(command.contains("sed -i"))
        // Se la riga non c'è viene aggiunta invece di lasciare il file invariato.
        assertTrue(command.contains(">>"))
    }

    @Test
    fun `rifiuta versioni che potrebbero alterare il comando`() {
        assertTrue(GameVersion.isValidVersion("1.21.10"))
        assertTrue(GameVersion.isValidVersion("23w13a_or_b"))
        assertFalse(GameVersion.isValidVersion("1.21\"; rm -rf /"))
        assertFalse(GameVersion.isValidVersion("\$(whoami)"))
        assertFalse(GameVersion.isValidVersion(""))
    }
}
