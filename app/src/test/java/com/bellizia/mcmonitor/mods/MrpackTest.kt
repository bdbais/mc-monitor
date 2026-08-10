package com.bellizia.mcmonitor.mods

import com.bellizia.mcmonitor.lgsm.Mods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il file di prova è l'indice di un modpack vero, esportato da Modrinth
 * ("zebby 1.0.0"), ridotto ai primi file per leggibilità.
 */
class MrpackTest {

    private val index: String =
        javaClass.classLoader!!.getResourceAsStream("zebby.index.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun `legge versione di gioco e loader dalle dipendenze del pacchetto`() {
        val pack = Modpack.parse(index)
        // Nell'indice nome e versione sono due campi distinti.
        assertEquals("zebby", pack.name)
        assertEquals("1.0.0", pack.versionId)
        assertEquals("zebby 1.0.0", pack.label)
        assertEquals("1.20.1", pack.minecraftVersion)
        assertEquals("fabric", pack.loader)
        assertEquals("0.19.3", pack.loaderVersion)
    }

    @Test
    fun `ogni file ha percorso, url della CDN e sha1`() {
        val pack = Modpack.parse(index)
        assertEquals(3, pack.files.size)

        val first = pack.files.first()
        assertTrue(first.path.startsWith("mods/"))
        assertTrue(Mods.isTrustedUrl(first.url))
        assertEquals(40, first.sha1.length)
        assertTrue(first.sizeBytes > 0)
        assertTrue(first.neededOnServer)
    }

    @Test
    fun `i percorsi del pacchetto passano il controllo di sicurezza`() {
        Modpack.parse(index).files.forEach {
            assertTrue("percorso rifiutato: ${it.path}", Mods.safeRelativePath(it.path))
        }
    }

    @Test
    fun `un percorso che esce da serverfiles viene rifiutato`() {
        assertFalse(Mods.safeRelativePath("../../etc/cron.d/backdoor"))
        assertFalse(Mods.safeRelativePath("/etc/passwd"))
        assertFalse(Mods.safeRelativePath("mods/../../x.jar"))
        assertFalse(Mods.safeRelativePath("mods/x.jar; rm -rf /"))
        // I nomi reali con parentesi quadre restano ammessi.
        assertTrue(Mods.safeRelativePath("mods/[fabric]ctov-3.4.14.jar"))
    }

    @Test
    fun `segnala l incompatibilita' con il server`() {
        val pack = Modpack.parse(index)
        // Server vanilla 1.21.10, come quello attuale: due problemi da segnalare.
        val mismatch = pack.mismatch("1.21.10", "vanilla")
        assertNotNull(mismatch)
        assertTrue(mismatch!!.contains("1.20.1"))
        assertTrue(mismatch.contains("fabric"))

        // Server giusto: nessun avviso.
        assertNull(pack.mismatch("1.20.1", "fabric"))
    }

    @Test
    fun `i file solo lato client restano fuori dal server`() {
        val json = """
        {"formatVersion":1,"name":"prova","versionId":"1","dependencies":{"minecraft":"1.20.1"},
         "files":[
          {"path":"mods/server.jar","downloads":["https://cdn.modrinth.com/a.jar"],
           "hashes":{"sha1":"${"a".repeat(40)}"},"fileSize":1,"env":{"server":"required","client":"required"}},
          {"path":"mods/solo-client.jar","downloads":["https://cdn.modrinth.com/b.jar"],
           "hashes":{"sha1":"${"b".repeat(40)}"},"fileSize":1,"env":{"server":"unsupported","client":"required"}}
         ]}
        """.trimIndent()

        val pack = Modpack.parse(json)
        assertEquals(2, pack.files.size)
        assertEquals(1, pack.serverFiles.size)
        assertEquals("mods/server.jar", pack.serverFiles.first().path)
    }
}
