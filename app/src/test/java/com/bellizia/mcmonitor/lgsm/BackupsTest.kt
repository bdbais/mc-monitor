package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le copie di sicurezza: dove sono, quando, e quanto spazio resta. */
class BackupsTest {

    private val cfg = ServerConfig(lgsmDir = "~/server1", script = "mcserver")

    private val letto = """
        ora=1700000000
        cartella=si
        1699900000	1400000000	mcserver-2026-08-22-040001.tar.gz
        1699700000	1390000000	mcserver-2026-08-20-040001.tar.gz
        riga che non c'entra
        usati=2900000
        liberi=5000000
    """.trimIndent()

    @Test
    fun `legge le copie e l'ora del server`() {
        val s = Backups.parse(letto)
        assertEquals(2, s.backups.size)
        assertTrue(s.hasFolder)
        assertEquals(1700000000L, s.nowEpochSeconds)
        // La piu' recente e' la prima.
        assertEquals("mcserver-2026-08-22-040001.tar.gz", s.last?.fileName)
    }

    @Test
    fun `dice da quanti giorni non si fa un backup`() {
        // L'ora e' quella del server: un telefono con l'orologio sballato direbbe
        // che l'ultimo backup e' di domani.
        assertEquals(1L, Backups.parse(letto).daysSinceLast)
    }

    @Test
    fun `senza cartella non c'e' niente`() {
        val s = Backups.parse("ora=1700000000\ncartella=no")
        assertFalse(s.hasFolder)
        assertTrue(s.backups.isEmpty())
        assertNull(s.last)
        assertNull(s.daysSinceLast)
    }

    @Test
    fun `le dimensioni si leggono`() {
        val s = Backups.parse(letto)
        assertEquals("1.3 GB", s.last?.sizeLabel)
        assertTrue(s.spaceLabel(s.freeKb).endsWith("GB"))
    }

    @Test
    fun `guarda nella cartella giusta`() {
        // Non e' "backups" sotto la cartella del server: LinuxGSM le mette in
        // lgsm/backup.
        val cmd = Backups.list(cfg)
        assertTrue(cmd.contains("server1/lgsm/backup"))
        // Il filtro e' *.tar.* come quello della pulizia di LinuxGSM: un archivio
        // rimasto a meta' conta comunque come backup, ed e' giusto vederlo.
        assertTrue(cmd.contains("*.tar.*"))
    }

    @Test
    fun `il backup si fa con lo stesso comando del terminale`() {
        assertTrue(Backups.now(cfg).contains("./'mcserver' backup"))
    }
}
