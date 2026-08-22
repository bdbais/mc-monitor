package com.bellizia.mcmonitor.ai

import com.bellizia.mcmonitor.lgsm.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il setaccio sulle macro scritte da un servizio esterno.
 *
 * Non è una regola per chi usa l'app: dalla Console quei comandi si scrivono
 * quando si vuole. È una regola per quello che scrive qualcun altro al posto
 * suo, e che nessuno ha riletto prima che finisse in un pulsante da premere.
 */
class MacroGeneratorTest {

    @Test
    fun `riconosce il verbo anche con la barra`() {
        assertEquals("say", MacroGenerator.verb("say ciao"))
        assertEquals("say", MacroGenerator.verb("/say ciao"))
        assertEquals("save-all", MacroGenerator.verb("  save-all flush  "))
        assertEquals("time", MacroGenerator.verb("TIME set day"))
    }

    @Test
    fun `i comandi che servono passano`() {
        listOf(
            "say Manutenzione fra 5 minuti",
            "time set night",
            "weather clear",
            "gamerule doDaylightCycle false",
            "give <giocatore> minecraft:bread 8",
            "effect give <giocatore> minecraft:regeneration 30 2",
            "execute at <giocatore> run summon minecraft:firework_rocket ~ ~2 ~",
            "save-all flush",
            "tp @a <x> <y> <z>",
            "${Macros.WAIT} 30"
        ).forEach { assertTrue(it, MacroGenerator.safe(it)) }
    }

    @Test
    fun `quelli che spengono il server non passano`() {
        listOf("stop", "/stop", "restart", "reload").forEach {
            assertFalse(it, MacroGenerator.safe(it))
        }
    }

    @Test
    fun `quelli che decidono chi entra non passano`() {
        listOf(
            "op Ciccio",
            "deop Vale",
            "ban Ciccio per sempre",
            "ban-ip 1.2.3.4",
            "pardon Ciccio",
            "kick Vale",
            "whitelist off",
            "whitelist add Ciccio"
        ).forEach { assertFalse(it, MacroGenerator.safe(it)) }
    }

    @Test
    fun `un verbo che non conosciamo non passa`() {
        // Meglio scartare un comando buono che lasciar passare uno che non
        // abbiamo mai visto.
        assertFalse(MacroGenerator.safe("inventato qualcosa"))
        assertFalse(MacroGenerator.safe(""))
        assertFalse(MacroGenerator.safe("   "))
    }

    @Test
    fun `una pausa storta non passa`() {
        assertTrue(MacroGenerator.safe("${Macros.WAIT} 10"))
        // waitSeconds non torna mai null su una riga che comincia con !attendi:
        // quello che conta è che una pausa non diventi un comando.
        assertFalse(MacroGenerator.safe("attendi 10"))
    }

    @Test
    fun `legge la risposta e tiene solo il buono`() {
        val generata = MacroGenerator.parse(
            """
            {
              "nome": "Gara di costruzione",
              "descrizione": "Prepara il server per una gara: giorno, sereno, tutti in creativa.",
              "comandi": [
                "say Fra poco si comincia",
                "time set day",
                "weather clear",
                "op <giocatore>",
                "gamemode creative @a",
                "stop"
              ]
            }
            """.trimIndent()
        )
        assertEquals("Gara di costruzione", generata.macro.name)
        assertEquals(
            listOf("say Fra poco si comincia", "time set day", "weather clear", "gamemode creative @a"),
            generata.macro.commands
        )
        assertEquals(listOf("op <giocatore>", "stop"), generata.rejected)
    }

    @Test
    fun `una macro generata nasce delicata`() {
        // Nessuno l'ha riletta: la conferma prima di lanciarla deve dirlo.
        val generata = MacroGenerator.parse("""{"nome":"x","descrizione":"y","comandi":["say ciao"]}""")
        assertTrue(generata.macro.risky)
        assertFalse(generata.macro.builtin)
        assertEquals("", generata.macro.id)
    }

    @Test
    fun `la barra iniziale viene tolta`() {
        val generata = MacroGenerator.parse("""{"nome":"x","descrizione":"y","comandi":["/say ciao"]}""")
        assertEquals(listOf("say ciao"), generata.macro.commands)
    }

    @Test
    fun `la cornice markdown non da fastidio`() {
        val generata = MacroGenerator.parse(
            "```json\n{\"nome\":\"x\",\"descrizione\":\"y\",\"comandi\":[\"say ciao\"]}\n```"
        )
        assertEquals(listOf("say ciao"), generata.macro.commands)
    }

    @Test
    fun `non piu di quindici comandi`() {
        val comandi = (1..40).joinToString(",") { "\"say riga $it\"" }
        val generata = MacroGenerator.parse("""{"nome":"x","descrizione":"y","comandi":[$comandi]}""")
        assertEquals(15, generata.macro.commands.size)
    }

    @Test(expected = MacroAiException::class)
    fun `se non resta niente e' un errore`() {
        MacroGenerator.parse("""{"nome":"x","descrizione":"y","comandi":["stop","op Ciccio"]}""")
    }

    @Test(expected = MacroAiException::class)
    fun `una risposta illeggibile e' un errore`() {
        MacroGenerator.parse("mi dispiace, non posso aiutarti")
    }

    @Test
    fun `nel prompt c'e' quello che serve al servizio`() {
        val p = MacroGenerator.prompt("prepara una gara", "1.20.4", "fabric")
        assertTrue(p.contains("prepara una gara"))
        assertTrue(p.contains("1.20.4"))
        assertTrue(p.contains("fabric"))
        assertTrue(p.contains(Macros.WAIT))
        assertTrue(p.contains("<giocatore>"))
        // Il divieto è scritto anche nel prompt: il setaccio dopo resta,
        // ma chiedere bene fa risparmiare un giro.
        assertTrue(p.contains("stop"))
    }

    @Test
    fun `una chiave storta lo dice in italiano`() {
        // Google risponde 400, non 401: senza guardare nel corpo, a chi usa l'app
        // arriverebbe una pagina di JSON al posto di "controlla la chiave".
        val google = MacroGenerator.spiega(
            400,
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        )
        assertTrue(google, google.startsWith("Chiave rifiutata"))
        assertTrue(MacroGenerator.spiega(401, "{}").startsWith("Chiave rifiutata"))
        assertTrue(MacroGenerator.spiega(403, "{}").startsWith("Chiave rifiutata"))
        assertTrue(
            MacroGenerator.spiega(400, """{"error":{"message":"Invalid API Key"}}""")
                .startsWith("Chiave rifiutata")
        )
    }

    @Test
    fun `le altre risposte restano leggibili`() {
        assertTrue(MacroGenerator.spiega(500, "boom").contains("500"))
        assertTrue(MacroGenerator.spiega(400, "quota exceeded").contains("gratuite"))
        // Il corpo non arriva intero: sono pagine di JSON.
        assertTrue(MacroGenerator.spiega(500, "x".repeat(1000)).length < 400)
    }

    @Test
    fun `i servizi hanno un indirizzo dove prendere la chiave`() {
        AiProvider.entries.forEach { p ->
            assertTrue(p.label, p.where.startsWith("https://"))
            assertTrue(p.label, p.model.isNotBlank())
        }
        assertEquals(AiProvider.GROQ, AiProvider.byId("groq"))
        assertEquals(AiProvider.GOOGLE, AiProvider.byId("inventato"))
    }
}
