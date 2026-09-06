package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I gesti da moderatore. Meta' di questi comandi in Minecraft non esistono, e un
 * comando che non esiste non fallisce in modo rumoroso: il server risponde
 * «Unknown command» e chi ha premuto crede di aver fermato un vandalo.
 */
class ModerazioneTest {

    private val prigione = Moderazione.Prigione(100, 64, -200, "minecraft:overworld")

    @Test
    fun `prima si toglie il piccone, poi si sposta`() {
        // Fra un comando e l'altro passano dei decimi di secondo: in
        // sopravvivenza ci sta una picconata.
        val c = Moderazione.incarcera("Baisso", prigione)
        assertEquals(2, c.size)
        assertTrue("il primo comando non mette in avventura: ${c[0]}", c[0].startsWith("gamemode adventure"))
        assertTrue("il secondo comando non teletrasporta: ${c[1]}", c[1].contains("tp Baisso"))
    }

    @Test
    fun `il teletrasporto dice in quale mondo`() {
        // `tp` da solo resta nel mondo di chi lo subisce: chi sta nel Nether
        // finirebbe alle stesse coordinate ma nel Nether.
        val tp = Moderazione.incarcera("Baisso", prigione).last()
        assertTrue("manca il mondo: $tp", tp.startsWith("execute in minecraft:overworld run tp "))
        assertTrue(tp.endsWith(" 100 64 -200"))
    }

    @Test
    fun `la prigione nel Nether e' nel Nether`() {
        val tp = Moderazione.incarcera("Baisso", prigione.copy(dimensione = "minecraft:the_nether")).last()
        assertTrue(tp.startsWith("execute in minecraft:the_nether run tp "))
    }

    @Test
    fun `chi esce torna dov'era`() {
        val dove = Moderazione.Dove(10, 70, -30, "minecraft:the_end")
        val c = Moderazione.libera("Baisso", dove)
        assertEquals("gamemode survival Baisso", c[0])
        assertEquals("execute in minecraft:the_end run tp Baisso 10 70 -30", c[1])
    }

    @Test
    fun `se non si sa dov'era non lo si sposta a caso`() {
        // Rimetterlo allo spawn «perche' bisogna metterlo da qualche parte»
        // vorrebbe dire spostarlo di migliaia di blocchi senza averlo promesso.
        val c = Moderazione.libera("Baisso", null)
        assertEquals(listOf("gamemode survival Baisso"), c)
    }

    @Test
    fun `il posto dove sta si prende dal cruscotto e si arrotonda`() {
        val v = Cruscotto.Vitali(
            "Baisso",
            posizione = Triple(1489.6, 66.4, -489.5),
            dimensione = "minecraft:the_nether"
        )
        val d = Moderazione.dove(v)!!
        assertEquals(1490, d.x)
        assertEquals(66, d.y)
        assertEquals(-489, d.z)
        assertEquals("minecraft:the_nether", d.dimensione)
    }

    @Test
    fun `senza posizione non si inventa un posto`() {
        assertNull(Moderazione.dove(Cruscotto.Vitali("Baisso")))
    }

    // ------------------------------------------------------ quello che non c'e'

    @Test
    fun `il rifiuto del server si riconosce`() {
        listOf(
            "Unknown or incomplete command, see below for error",
            "Unknown command. Try /help",
            "Comando sconosciuto"
        ).forEach {
            assertTrue("non riconosciuto: $it", Moderazione.comandoAssente(it))
        }
    }

    @Test
    fun `una risposta normale non viene scambiata per un rifiuto`() {
        // Un server che il comando ce l'ha risponde, e se rispondesse "assente"
        // l'app direbbe che non si puo' azzittire mentre lo ha appena fatto.
        listOf(
            "Baisso is now muted for 10 minutes",
            "Muted Baisso",
            "That player does not exist"
        ).forEach {
            assertFalse("scambiata per rifiuto: $it", Moderazione.comandoAssente(it))
        }
    }

    @Test
    fun `il messaggio di ripiego propone solo cose che funzionano`() {
        // Non deve rimandare a un altro comando che non c'e'.
        assertFalse(Moderazione.SENZA_SILENZIO.contains("/mute"))
        assertTrue(Moderazione.SENZA_SILENZIO.contains("prigione"))
    }

    @Test
    fun `il ban e' l'unico gesto che non si disfa in un tasto`() {
        val irreversibili = Moderazione.Gesto.entries.filterNot { it.reversibile }
        assertEquals(listOf(Moderazione.Gesto.BLOCCA), irreversibili)
    }

    @Test
    fun `il silenzio a tempo dice il tempo`() {
        assertEquals("mute Baisso", Moderazione.comandoSilenzio("Baisso", null))
        assertEquals("mute Baisso 10m", Moderazione.comandoSilenzio("Baisso", 10))
    }
}
