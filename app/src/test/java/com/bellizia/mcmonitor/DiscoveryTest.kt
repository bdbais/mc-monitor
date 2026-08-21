package com.bellizia.mcmonitor

import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.lgsm.Discovery
import com.bellizia.mcmonitor.lgsm.LgsmUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La scansione della home è il primo contatto con un computer sconosciuto:
 * quello che torna va letto anche quando è sporco o incompleto.
 */
class DiscoveryTest {

    private val output = """
        --- istanza
        script=mcserver
        dir=/home/mcserver/mondo-di-vale
        lgsm=v23.5.3
        versione=1.20.4
        ramo=
        serverfiles=si
        porta=25565
        motd=Il mondo di Vale
        loader=Fabric
        mod=sodium-1.20.4
        mod=lithium
        attivo=si
        spazio=4210
        --- istanza
        script=mcserver
        dir=/home/mcserver/prove
        lgsm=v24.1.0
        versione=1.21.1
        serverfiles=no
        attivo=no
        spazio=12
        --- fine
    """.trimIndent()

    @Test
    fun leggeLeIstanzeTrovate() {
        val found = Discovery.parse(output)
        assertEquals(2, found.size)

        // Ordinate per nome: "mondo-di-vale" viene prima di "prove".
        val primo = found[0]
        assertEquals("mondo-di-vale", primo.displayName)
        assertEquals("/home/mcserver/mondo-di-vale", primo.directory)
        assertEquals("1.20.4", primo.minecraftVersion)
        assertEquals("25565", primo.gamePort)
        assertEquals("v23.5.3", primo.lgsmVersion)
        assertTrue(primo.hasServerFiles)
        assertTrue(primo.running)
        assertEquals(4210L, primo.sizeMb)
    }

    @Test
    fun campiVuotiRestanoNulli() {
        val secondo = Discovery.parse(output)[1]
        assertEquals("prove", secondo.displayName)
        assertEquals(null, secondo.branch)
        assertEquals(null, secondo.gamePort)
        assertFalse(secondo.hasServerFiles)
        assertFalse(secondo.running)
    }

    @Test
    fun ilRiepilogoDiceLeCoseImportanti() {
        val found = Discovery.parse(output)
        assertTrue(found[0].summary.contains("in esecuzione"))
        assertTrue(found[0].summary.contains("Minecraft 1.20.4"))
        assertTrue(found[0].summary.contains("porta 25565"))
        assertTrue(found[1].summary.contains("fermo"))
        assertTrue(found[1].summary.contains("non ancora installato"))
    }

    @Test
    fun raccoglieLeModEIlLoader() {
        val found = Discovery.parse(output)
        val moddato = found[0]
        assertTrue(moddato.modded)
        assertEquals(listOf("sodium-1.20.4", "lithium"), moddato.mods)
        assertEquals("Fabric", moddato.loader)
        assertEquals("2 mod · Fabric", moddato.modsLabel)

        val vanilla = found[1]
        assertFalse(vanilla.modded)
        assertEquals("", vanilla.modsLabel)
    }

    @Test
    fun ignoraBlocchiSenzaScriptODirectory() {
        val rotto = """
            --- istanza
            versione=1.20.4
            --- istanza
            script=mcserver
            dir=/home/mcserver/buono
            --- fine
        """.trimIndent()
        val found = Discovery.parse(rotto)
        assertEquals(1, found.size)
        assertEquals("buono", found[0].displayName)
    }

    @Test
    fun nonRipeteLaStessaIstanza() {
        val doppio = output + "\n" + output
        assertEquals(2, Discovery.parse(doppio).size)
    }

    @Test
    fun segnalaSoloIDoppioniDiPorta() {
        val tre = Discovery.parse(
            """
            --- istanza
            script=mcserver
            dir=/home/mcserver/aaa
            serverfiles=si
            porta=25565
            --- istanza
            script=mcserver
            dir=/home/mcserver/bbb
            serverfiles=si
            porta=25565
            --- istanza
            script=mcserver
            dir=/home/mcserver/ccc
            serverfiles=si
            porta=25566
            --- fine
            """.trimIndent()
        )
        // Il primo che usa una porta resta pulito: in rosso vanno quelli dopo.
        val conflitti = Discovery.portConflicts(tre)
        assertEquals(setOf(1), conflitti.keys)
        assertEquals(setOf("25565"), conflitti[1])
    }

    @Test
    fun segnalaAncheLeCollisioniDiRcon() {
        val due = Discovery.parse(
            """
            --- istanza
            script=mcserver
            dir=/home/mcserver/aaa
            serverfiles=si
            porta=25565
            rcon=25575
            rconattivo=true
            --- istanza
            script=mcserver
            dir=/home/mcserver/bbb
            serverfiles=si
            porta=25566
            rcon=25575
            rconattivo=true
            --- fine
            """.trimIndent()
        )
        assertTrue(due[0].rconEnabled)
        assertTrue(due[0].summary.contains("RCON 25575"))
        // La porta di gioco è diversa: in rosso va solo RCON.
        assertEquals(setOf("25575"), Discovery.portConflicts(due)[1])
    }

    @Test
    fun rconSpentoNonEntraNeiConflitti() {
        val due = Discovery.parse(
            """
            --- istanza
            script=mcserver
            dir=/home/mcserver/aaa
            serverfiles=si
            porta=25565
            rcon=25575
            rconattivo=false
            --- istanza
            script=mcserver
            dir=/home/mcserver/bbb
            serverfiles=si
            porta=25566
            rcon=25575
            rconattivo=false
            --- fine
            """.trimIndent()
        )
        assertFalse(due[0].rconEnabled)
        assertFalse(due[0].summary.contains("RCON"))
        assertTrue(Discovery.portConflicts(due).isEmpty())
    }

    @Test
    fun rconCheRubaLaPortaDiGiocoDiUnAltro() {
        val due = Discovery.parse(
            """
            --- istanza
            script=mcserver
            dir=/home/mcserver/aaa
            serverfiles=si
            porta=25565
            --- istanza
            script=mcserver
            dir=/home/mcserver/bbb
            serverfiles=si
            porta=25566
            rcon=25565
            rconattivo=true
            --- fine
            """.trimIndent()
        )
        assertEquals(setOf("25565"), Discovery.portConflicts(due)[1])
    }

    @Test
    fun senzaPortaNessunConflitto() {
        // Un'istanza non ancora installata non ha porte: non deve inventarsi rossi.
        assertTrue(Discovery.portConflicts(Discovery.parse(output)).isEmpty())
    }

    @Test
    fun applicaAlProfiloSenzaToccareLeCredenziali() {
        val account = ServerConfig(
            host = "casa.example.org",
            user = "mcserver",
            password = "segreta",
            rconPassword = "rcon-segreta",
            rconEnabled = true
        )
        val trovato = Discovery.parse(output)[0]
        val profilo = Discovery.applyTo(account, trovato)

        assertEquals("casa.example.org", profilo.host)
        assertEquals("segreta", profilo.password)
        assertEquals("rcon-segreta", profilo.rconPassword)
        assertTrue(profilo.rconEnabled)
        assertEquals("/home/mcserver/mondo-di-vale", profilo.lgsmDir)
        assertEquals("mcserver", profilo.script)
        assertEquals("mondo-di-vale", profilo.slug)
        // Senza indicazioni i file di gioco stanno sotto la cartella dell'istanza.
        assertEquals("/home/mcserver/mondo-di-vale/serverfiles", profilo.serverFiles)
    }

    @Test
    fun leCredenzialiViaggianoDaSole() {
        val profilo = ServerConfig(
            name = "Mondo",
            host = "vecchio.example.org",
            user = "vecchio",
            password = "vecchia",
            lgsmDir = "~/mondo",
            rconPort = 25580
        )
        val account = ServerConfig(host = "nuovo.example.org", user = "nuovo", password = "nuova")

        val aggiornato = profilo.withCredentials(account)
        assertEquals("nuovo.example.org", aggiornato.host)
        assertEquals("nuova", aggiornato.password)
        // Il resto del profilo non si tocca.
        assertEquals("Mondo", aggiornato.name)
        assertEquals("~/mondo", aggiornato.lgsmDir)
        assertEquals(25580, aggiornato.rconPort)

        val sole = profilo.credentials()
        assertTrue(sole.hasCredentials)
        assertEquals("vecchio.example.org", sole.host)
        // Le credenziali da sole non sanno dove sia il server.
        assertEquals("", sole.name)
    }

    @Test
    fun laCancellazioneControllaPrimaDiCancellare() {
        val comando = Discovery.remove(Discovery.parse(output)[0])
        // Il percorso viene apostrofato, non concatenato a mano.
        assertTrue(comando.contains("dir='/home/mcserver/mondo-di-vale'"))
        // Tre reti di sicurezza prima del rm.
        assertTrue(comando.contains("RIFIUTATO: percorso non consentito"))
        assertTrue(comando.contains("""[ -f "${'$'}dir/${'$'}script" ]"""))
        assertTrue(comando.contains("""[ -d "${'$'}dir/lgsm/config-lgsm" ]"""))
        // E si spegne prima di cancellare.
        assertTrue(comando.indexOf("stop") < comando.indexOf("rm -rf"))
        assertTrue(comando.contains("""rm -rf "${'$'}dir""""))
    }

    @Test
    fun laCancellazioneValeSoloSeIlServerLoConferma() {
        assertTrue(Discovery.removed("CANCELLATO /home/mcserver/prove"))
        assertFalse(Discovery.removed("RIFIUTATO: percorso non consentito"))
        assertFalse(Discovery.removed(""))
    }

    @Test
    fun linuxgsmVecchioSoloQuandoSiSaDavvero() {
        assertTrue(LgsmUpdate.outdated("v23.5.3", "v24.1.0"))
        assertFalse(LgsmUpdate.outdated("v24.1.0", "v24.1.0"))
        assertFalse(LgsmUpdate.outdated("v24.2.0", "v24.1.0"))
        // Nel dubbio non si allarma nessuno.
        assertFalse(LgsmUpdate.outdated(null, "v24.1.0"))
        assertFalse(LgsmUpdate.outdated("v23.5.3", null))
        assertFalse(LgsmUpdate.outdated("", ""))
    }

    @Test
    fun elencaSoloLeIstanzeIndietro() {
        val found = Discovery.parse(output)
        val vecchi = LgsmUpdate.outdatedServers(found, "v24.1.0")
        assertEquals(1, vecchi.size)
        assertEquals("mondo-di-vale", vecchi[0].displayName)
        assertTrue(LgsmUpdate.banner(vecchi, "v24.1.0").contains("v23.5.3 → v24.1.0"))
        assertEquals("", LgsmUpdate.banner(emptyList(), "v24.1.0"))
    }

    @Test
    fun leggeLEtichettaDellaReleaseLinuxgsm() {
        assertEquals("v24.1.0", LgsmUpdate.parseTag("""{"tag_name":"v24.1.0","name":"LinuxGSM"}"""))
        assertEquals(null, LgsmUpdate.parseTag("non è json"))
        assertEquals(null, LgsmUpdate.parseTag("""{"altro":1}"""))
    }

    @Test
    fun ilComandoDiAggiornamentoEntraNellaCartellaGiusta() {
        val comando = LgsmUpdate.updateCommand(Discovery.parse(output)[0])
        assertTrue(comando.startsWith("cd '/home/mcserver/mondo-di-vale'"))
        assertTrue(comando.contains("./'mcserver' update-lgsm"))
        // Le installazioni vecchie non conoscono update-lgsm.
        assertTrue(comando.contains("update-functions"))
    }
}
