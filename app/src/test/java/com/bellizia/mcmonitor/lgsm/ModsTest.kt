package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.mods.Modrinth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModsTest {

    private val cfg = ServerConfig(lgsmDir = "/home/mcserver", script = "mcserver")

    @Test
    fun `legge nome, versione e stato dai file della cartella mods`() {
        val raw = """
            fabric-api-0.102.0+1.21.jar	2216755	1754300000
            sodium-fabric-0.5.11.jar	908123	1754200000
            lithium-fabric-0.13.0.jar.disabled	512000	1754100000
        """.trimIndent()

        val mods = Mods.parseMods(raw)
        assertEquals(3, mods.size)

        val fabric = mods.first { it.fileName.startsWith("fabric-api") }
        assertEquals("fabric-api", fabric.name)
        assertEquals("0.102.0+1.21", fabric.version)
        assertTrue(fabric.enabled)
        assertEquals("2.1 MB", fabric.sizeLabel)

        val lithium = mods.first { !it.enabled }
        assertEquals("lithium-fabric-0.13.0.jar", lithium.jarName)
        assertEquals("0.13.0", lithium.version)
    }

    @Test
    fun `cartella mods assente non e' un errore`() {
        assertTrue(Mods.parseMods("NO_MODS_DIR").isEmpty())
    }

    @Test
    fun `rileva versione e loader dall ambiente`() {
        val env = Mods.parseEnvironment("version=1.21.10\nloader=fabric\nmodsdir=yes\n", cfg)
        assertEquals("1.21.10", env.minecraftVersion)
        assertEquals("fabric", env.loader)
        assertEquals("Fabric", env.loaderLabel)
        assertTrue(env.hasModsDir)
        assertFalse(env.isVanilla)
        assertEquals("/home/mcserver/serverfiles/mods", env.modsDir)
    }

    @Test
    fun `un server vanilla viene riconosciuto come tale`() {
        val env = Mods.parseEnvironment("version=1.21.10\nloader=vanilla\nmodsdir=no\n", cfg)
        assertTrue(env.isVanilla)
        assertFalse(env.hasModsDir)
    }

    @Test
    fun `accetta solo url della CDN di Modrinth`() {
        assertTrue(Mods.isTrustedUrl("https://cdn.modrinth.com/data/AABB/versions/x/mod.jar"))
        assertFalse(Mods.isTrustedUrl("http://cdn.modrinth.com/data/x.jar"))       // non cifrato
        assertFalse(Mods.isTrustedUrl("https://cdn.modrinth.com.evil.tld/x.jar"))  // host simile
        assertFalse(Mods.isTrustedUrl("https://esempio.it/mod.jar"))
    }

    @Test
    fun `rifiuta nomi file che potrebbero alterare il comando remoto`() {
        assertTrue(Mods.safeFileName("fabric-api-0.102.0+1.21.jar"))
        assertFalse(Mods.safeFileName("../../etc/passwd.jar"))
        assertFalse(Mods.safeFileName("mod.jar; rm -rf /"))
        assertFalse(Mods.safeFileName("mod.jar\$(whoami).jar"))
        assertFalse(Mods.safeFileName("senza-estensione"))
    }

    @Test
    fun `il comando di installazione verifica lo sha1 dichiarato`() {
        val sha = "a".repeat(40)
        val command = Mods.install(
            cfg,
            "https://cdn.modrinth.com/data/AABB/versions/1/sodium.jar",
            "sodium.jar",
            sha
        )
        assertTrue(command.contains("sha1sum"))
        assertTrue(command.contains(sha))
        assertTrue(command.contains("exit ${Mods.EXIT_HASH_MISMATCH}"))
        // Il file va a posto solo dopo il confronto.
        assertTrue(command.indexOf("sha1sum") < command.indexOf("mv \"\$tmp\""))
    }

    @Test
    fun `attiva e disattiva rinominando il file`() {
        assertTrue(Mods.toggle(cfg, "sodium.jar").contains("sodium.jar.disabled"))
        assertTrue(Mods.toggle(cfg, "sodium.jar.disabled").contains("attivato sodium.jar"))
    }

    @Test
    fun `dalle versioni di Modrinth prende il file primario e le dipendenze obbligatorie`() {
        val json = """
        [
          {
            "id": "abc123",
            "version_number": "0.5.11",
            "name": "Sodium 0.5.11",
            "date_published": "2026-07-01T10:00:00Z",
            "game_versions": ["1.21.10"],
            "loaders": ["fabric"],
            "dependencies": [
              {"project_id": "P7dR8mSH", "dependency_type": "required"},
              {"project_id": "opzionale", "dependency_type": "optional"}
            ],
            "files": [
              {"filename": "sodium-sources.jar", "url": "https://cdn.modrinth.com/a/sources.jar",
               "primary": false, "hashes": {"sha1": "1111111111111111111111111111111111111111"}},
              {"filename": "sodium-fabric-0.5.11.jar", "url": "https://cdn.modrinth.com/a/sodium.jar",
               "primary": true, "hashes": {"sha1": "2222222222222222222222222222222222222222"}}
            ]
          }
        ]
        """.trimIndent()

        val versions = Modrinth.parseVersions(json)
        assertEquals(1, versions.size)
        val v = versions[0]
        assertEquals("sodium-fabric-0.5.11.jar", v.fileName)
        assertEquals("2222222222222222222222222222222222222222", v.sha1)
        assertEquals("2026-07-01", v.dateLabel)
        assertEquals(listOf("P7dR8mSH"), v.requiredDependencies)
    }
}
