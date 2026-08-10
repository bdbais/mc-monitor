package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisionTest {

    private val cfg = ServerConfig(lgsmDir = "/home/mcserver", script = "mcserver")

    @Test
    fun `riconosce una home vuota`() {
        val raw = """
            home=/home/mcserver
            utente=mcserver
            dir=no
            script=no
            serverfiles=no
            spazio=51200
            scrivibile=si
        """.trimIndent()
        val i = Provision.parseInspection(raw)
        assertTrue(i.isEmpty)
        assertTrue(i.enoughSpace)
        assertEquals("mcserver", i.user)
        assertEquals(51200L, i.freeMegabytes)
    }

    @Test
    fun `un server gia' installato non e' vuoto`() {
        val i = Provision.parseInspection("dir=si\nscript=si\nserverfiles=si\nspazio=9000\n")
        assertFalse(i.isEmpty)
    }

    @Test
    fun `poco spazio viene segnalato`() {
        val i = Provision.parseInspection("dir=no\nscript=no\nspazio=900\n")
        assertFalse(i.enoughSpace)
    }

    @Test
    fun `l installazione si ferma se LinuxGSM c e' gia'`() {
        val command = Provision.installLinuxGsm(cfg)
        assertTrue(command.contains("exit ${Provision.EXIT_ALREADY_THERE}"))
        // Lo script scaricato viene eseguito solo dopo aver controllato lo shebang.
        assertTrue(command.contains("head -1 linuxgsm.sh"))
        assertTrue(command.indexOf("head -1 linuxgsm.sh") < command.indexOf("./linuxgsm.sh mcserver"))
    }

    @Test
    fun `la messa in sicurezza scrive le impostazioni chiave e restringe i permessi`() {
        val command = Provision.harden(cfg)
        listOf("online-mode=true", "white-list=true", "enforce-whitelist=true",
            "enforce-secure-profile=true", "enable-command-block=false").forEach {
            assertTrue("manca $it", command.contains(it))
        }
        assertTrue(command.contains("chmod 600"))
        assertTrue(command.contains("mcmonitor.bak"))
    }

    @Test
    fun `i requisiti mancanti vengono distinti da quelli facoltativi`() {
        val raw = "java=ok\ntmux=no\ncurl=ok\nwget=no\nunzip=ok\nsha1sum=ok\nzgrep=no\n"
        val results = Lgsm.parseRequirements(raw)
        val blocking = results.filter { it.blocking }.map { it.requirement.command }
        assertEquals(listOf("tmux"), blocking)
        // zgrep manca ma non blocca nulla.
        assertTrue(results.first { it.requirement.command == "zgrep" }.let { !it.present && !it.blocking })
    }

    @Test
    fun `wget basta al posto di curl`() {
        val results = Lgsm.parseRequirements("java=ok\ntmux=ok\ncurl=no\nwget=ok\nunzip=ok\nsha1sum=ok\nzgrep=ok\n")
        assertTrue(results.none { it.blocking })
    }

    @Test
    fun `legge la versione di Java`() {
        assertEquals("21.0.3", Lgsm.parseJavaVersion("openjdk version \"21.0.3\" 2024-04-16"))
    }

    @Test
    fun `il confronto fra versioni dell app tratta i numeri come numeri`() {
        assertTrue(UpdateChecker.isNewer("1.9", "1.8"))
        assertTrue(UpdateChecker.isNewer("1.10", "1.9"))
        assertTrue(UpdateChecker.isNewer("v2.0", "1.99"))
        assertFalse(UpdateChecker.isNewer("1.8", "1.8"))
        assertFalse(UpdateChecker.isNewer("1.7", "1.8"))
    }
}
