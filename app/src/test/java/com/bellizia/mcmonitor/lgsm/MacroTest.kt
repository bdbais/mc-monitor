package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le macro: cosa viene chiesto, cosa viene sostituito, cosa viene rifiutato. */
class MacroTest {

    @Test
    fun `i buchi si trovano nell'ordine e senza doppioni`() {
        val comandi = listOf(
            "give <giocatore> minecraft:bread 8",
            "tp <giocatore> <x> <y> <z>",
            "say ciao <giocatore>"
        )
        assertEquals(listOf("giocatore", "x", "y", "z"), Macros.placeholders(comandi))
    }

    @Test
    fun `senza buchi non chiede niente`() {
        assertTrue(Macros.placeholders(listOf("save-all flush", "say fatto")).isEmpty())
    }

    @Test
    fun `i buchi vengono riempiti`() {
        val riempiti = Macros.fill(
            listOf("give <giocatore> minecraft:bread 8", "tell <giocatore> ecco"),
            mapOf("giocatore" to "Vale")
        )
        assertEquals(listOf("give Vale minecraft:bread 8", "tell Vale ecco"), riempiti)
    }

    @Test
    fun `un valore con un a capo non diventa due comandi`() {
        // tmux send-keys -l manda il testo letterale: un a capo confermerebbe una
        // riga e ne comincerebbe un'altra, che non ha scritto nessuno.
        val riempiti = Macros.fill(
            listOf("say <messaggio>"),
            mapOf("messaggio" to "ciao\nstop")
        )
        assertEquals(listOf("say ciao stop"), riempiti)
        assertFalse(riempiti.first().contains('\n'))
    }

    @Test
    fun `il valore viene accorciato`() {
        val riempiti = Macros.fill(listOf("say <m>"), mapOf("m" to "x".repeat(500)))
        assertEquals(4 + 100, riempiti.first().length)
    }

    @Test
    fun `la pausa si riconosce e ha un tetto`() {
        assertEquals(30, Macros.waitSeconds("${Macros.WAIT} 30"))
        assertEquals(Macros.MAX_WAIT_SECONDS, Macros.waitSeconds("${Macros.WAIT} 99999"))
        assertEquals(0, Macros.waitSeconds("${Macros.WAIT} ciao"))
        assertNull(Macros.waitSeconds("say ciao"))
        assertNull(Macros.waitSeconds("attendi 30"))
    }

    @Test
    fun `le righe vuote non diventano comandi`() {
        val comandi = Macros.parseCommands("say uno\n\n   \nsay due\n")
        assertEquals(listOf("say uno", "say due"), comandi)
    }

    @Test
    fun `un comando lunghissimo viene rifiutato`() {
        assertFalse(Macros.isValidCommand("x".repeat(300)))
        assertFalse(Macros.isValidCommand("   "))
        assertTrue(Macros.isValidCommand("save-all flush"))
    }

    @Test
    fun `le macro pronte stanno in piedi da sole`() {
        assertTrue(Macros.catalogue.size >= 15)
        Macros.catalogue.forEach { macro ->
            assertTrue(macro.name, macro.id.startsWith("b-"))
            assertTrue(macro.name, macro.builtin)
            assertTrue(macro.name, macro.commands.isNotEmpty())
            assertTrue(macro.name, macro.description.length > 20)
            macro.commands.forEach { riga ->
                assertTrue("$macro: $riga", Macros.isValidCommand(riga))
                // Le barre iniziali sono un'abitudine da chat: in console il
                // comando va scritto senza.
                assertFalse("$macro: $riga", riga.startsWith("/"))
            }
        }
    }

    @Test
    fun `nessuna macro pronta ferma il server per conto suo`() {
        // "stop" spegnerebbe il server dentro una sequenza, e chi la lancia se ne
        // accorgerebbe dopo: fermare il server resta un pulsante suo.
        Macros.catalogue.forEach { macro ->
            macro.commands.forEach { riga ->
                assertFalse(macro.name, riga.trim() == "stop")
                assertFalse(macro.name, riga.trim().startsWith("ban "))
                assertFalse(macro.name, riga.trim().startsWith("op "))
            }
        }
    }

    @Test
    fun `gli identificativi delle macro pronte sono unici`() {
        val id = Macros.catalogue.map { it.id }
        assertEquals(id.size, id.distinct().size)
    }

    @Test
    fun `una macro fa il giro da e verso il json`() {
        val macro = Macro(
            id = "m-123",
            name = "La mia",
            description = "prova",
            commands = listOf("say uno", "${Macros.WAIT} 5", "say due"),
            risky = true
        )
        val tornata = Macro.fromJson(macro.toJson())
        assertEquals(macro.copy(builtin = false), tornata)
    }

    @Test
    fun `una macro senza comandi non torna indietro`() {
        val vuota = Macro("m-1", "vuota", "", emptyList()).toJson()
        assertNull(Macro.fromJson(vuota))
    }
}
