package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il progetto del server: cosa ci finisce dentro e, soprattutto, cosa no.
 *
 * Questo file viene mandato a qualcun altro. Un errore qui non fa smettere di
 * funzionare niente: regala la password di RCON o il webhook di Discord a chi
 * riceve il progetto, e nessuno se ne accorge.
 */
class BlueprintTest {

    private val cfg = ServerConfig(
        name = "Casa",
        lgsmDir = "~/server1",
        script = "mcserver",
        serverFilesDir = "~/server1/serverfiles"
    )

    private val letto = """
        --- lgsm
        ## Impostazioni
        mcversion="1.20.4"
        javaram="4G"
        #maxbackups="5"
        discordwebhook="https://discord.com/api/webhooks/123/abcSEGRETO"
        telegramtoken="55:AAA-segreto"
        email="admin@example.com"
        --- properties
        level-seed=-4707391740600041947
        level-name=mondo
        difficulty=hard
        rcon.password=parolaSegreta
        enable-rcon=true
        motd=Benvenuti
        --- mod
        da39a3ee5e6b4b0d3255bfef95601890afd80709	fabric-api-0.102.0.jar
        356a192b7913b04c54574d18c28d46e6395428ab	lithium-0.12.1.jar
        --- fine
    """.trimIndent()

    @Test
    fun `password token e webhook non escono da qui`() {
        assertTrue(Blueprints.isSecret("rcon.password"))
        assertTrue(Blueprints.isSecret("discordwebhook"))
        assertTrue(Blueprints.isSecret("telegramtoken"))
        assertTrue(Blueprints.isSecret("telegramchatid"))
        assertTrue(Blueprints.isSecret("email"))
        assertTrue(Blueprints.isSecret("sshkey"))
        assertTrue(Blueprints.isSecret("SERVER-PASSWORD"))
    }

    @Test
    fun `il seme e il nome del mondo non sono segreti`() {
        // Sono esattamente quello che si vuole clonare: se sparissero, il
        // progetto rifarebbe un mondo diverso.
        assertFalse(Blueprints.isSecret("level-seed"))
        assertFalse(Blueprints.isSecret("level-name"))
        assertFalse(Blueprints.isSecret("difficulty"))
        assertFalse(Blueprints.isSecret("javaram"))
    }

    @Test
    fun `legge le due configurazioni senza i segreti`() {
        val p = Blueprints.parse(letto, cfg, null)

        assertEquals("1.20.4", p.lgsm["mcversion"])
        assertEquals("4G", p.lgsm["javaram"])
        // La riga commentata resta fuori: nel file non è attiva.
        assertFalse(p.lgsm.containsKey("#maxbackups"))
        assertFalse(p.lgsm.containsKey("discordwebhook"))
        assertFalse(p.lgsm.containsKey("telegramtoken"))
        assertFalse(p.lgsm.containsKey("email"))

        assertEquals("-4707391740600041947", p.properties["level-seed"])
        assertEquals("hard", p.properties["difficulty"])
        assertFalse(p.properties.containsKey("rcon.password"))
    }

    @Test
    fun `nessun segreto sopravvive da nessuna parte`() {
        val p = Blueprints.parse(letto, cfg, null)
        val tutto = (p.lgsm + p.properties).toString()
        assertFalse(tutto.contains("abcSEGRETO"))
        assertFalse(tutto.contains("AAA-segreto"))
        assertFalse(tutto.contains("parolaSegreta"))
        assertFalse(tutto.contains("example.com"))
    }

    @Test
    fun `i mod arrivano con la loro impronta`() {
        val p = Blueprints.parse(letto, cfg, null)
        assertEquals(2, p.mods.size)
        assertEquals("fabric-api-0.102.0.jar", p.mods[0].fileName)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", p.mods[0].sha1)
    }

    @Test
    fun `una riga di mod inventata viene scartata`() {
        val p = Blueprints.parse(
            """
            --- mod
            nonèunhash	cattivo.jar
            da39a3ee5e6b4b0d3255bfef95601890afd80709	; rm -rf ~ .jar
            356a192b7913b04c54574d18c28d46e6395428ab	buono.jar
            --- fine
            """.trimIndent(),
            cfg, null
        )
        assertEquals(1, p.mods.size)
        assertEquals("buono.jar", p.mods[0].fileName)
    }

    @Test
    fun `senza i giocatori il comando non li legge nemmeno`() {
        val senza = Blueprints.read(cfg, includePlayers = false)
        assertFalse(senza.contains("whitelist.json"))
        val con = Blueprints.read(cfg, includePlayers = true)
        assertTrue(con.contains("whitelist.json"))
        assertTrue(con.contains("ops.json"))
    }

    @Test
    fun `porte e indirizzi non si trasferiscono`() {
        // Sul computer di chi riceve sono diversi: riscriverli vorrebbe dire
        // spegnergli il server o accavallarlo a un altro.
        val trasferibili = Blueprints.transferable(
            mapOf(
                "javaram" to "4G",
                "port" to "25565",
                "queryport" to "25565",
                "ip" to "0.0.0.0",
                "server-port" to "25565",
                "rcon.password" to "segreta",
                "difficulty" to "hard"
            )
        )
        assertEquals(setOf("javaram", "difficulty"), trasferibili.keys)
    }

    @Test
    fun `il punto nella chiave resta un punto`() {
        // Nel modello di awk il punto vale per qualsiasi carattere: senza
        // protezione "rcon.port" pescherebbe anche "rcon-port".
        val cmd = Blueprints.setProperty(cfg, "rcon.port", "25575")
        assertTrue(cmd.contains("""rcon\.port="""))
    }

    @Test
    fun `anche qui la riga passa dall'ambiente`() {
        val cmd = Blueprints.setProperty(cfg, "motd", "Benvenuti")
        assertTrue(cmd.contains("MCM_RIGA="))
        assertTrue(cmd.contains("""ENVIRON["MCM_RIGA"]"""))
        assertFalse(cmd.contains("awk -v"))
        assertTrue(cmd.contains("cp \"\$f\" \"\$f.mcmonitor.bak."))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `una chiave inventata viene rifiutata`() {
        Blueprints.setProperty(cfg, "motd; rm -rf ~", "ciao")
    }

    @Test
    fun `le chiavi di server properties passano`() {
        assertTrue(Blueprints.isValidPropertyKey("level-name"))
        assertTrue(Blueprints.isValidPropertyKey("rcon.port"))
        assertFalse(Blueprints.isValidPropertyKey("con spazio"))
        assertFalse(Blueprints.isValidPropertyKey(""))
    }

    @Test
    fun `il valore resta su una riga sola`() {
        assertEquals("uno due", Blueprints.cleanProperty("uno\ndue"))
        assertEquals("uno due", Blueprints.cleanProperty("  uno\r\ndue  "))
    }

    @Test
    fun `dice cosa contiene`() {
        val p = Blueprints.parse(letto, cfg, null)
        assertTrue(p.summary().contains("2 mod"))
        assertFalse(p.isEmpty)
    }
}
