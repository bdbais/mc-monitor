package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NBT in forma di testo: quello che risponde `data get entity`.
 *
 * Serve perché per questa via non occorre nessun accesso ai file, e quindi
 * l'inventario si vede anche dove l'app entra col solo RCON. Il risultato deve
 * essere identico a quello del lettore binario, o le due strade smetterebbero di
 * essere intercambiabili.
 */
class SnbtTest {

    @Test
    fun `un gruppo con i tipi che usa Minecraft`() {
        val g = Snbt.leggi(
            """{Health: 16.0f, foodLevel: 18, XpLevel: 30, SelectedItemSlot: 2,
               Dimension: "minecraft:the_nether", Invulnerable: 0b, Score: 1234L}"""
        )
        assertEquals(16.0, g.decimale("Health")!!, 0.001)
        assertEquals(18L, g.numero("foodLevel"))
        assertEquals(30L, g.numero("XpLevel"))
        assertEquals(2L, g.numero("SelectedItemSlot"))
        assertEquals("minecraft:the_nether", g.testo("Dimension"))
        assertEquals(0L, g.numero("Invulnerable"))
        assertEquals(1234L, g.numero("Score"))
    }

    @Test
    fun `la lettera in fondo dice il tipo e non finisce nel numero`() {
        val g = Snbt.leggi("{a: 64b, b: 5s, c: 7L, d: 1.5f, e: 2.5d, f: 42, g: 3.25}")
        assertEquals(64L, g.numero("a"))
        assertEquals(5L, g.numero("b"))
        assertEquals(7L, g.numero("c"))
        assertEquals(1.5, g.decimale("d")!!, 0.0001)
        assertEquals(2.5, g.decimale("e")!!, 0.0001)
        assertEquals(42L, g.numero("f"))
        assertEquals(3.25, g.decimale("g")!!, 0.0001)
    }

    @Test
    fun `i numeri negativi restano negativi`() {
        // Lo slot della mano secondaria e' -106: se il segno si perdesse,
        // finirebbe fra le caselle dello zaino.
        val g = Snbt.leggi("{Slot: -106b, x: -1489.5d}")
        assertEquals(-106L, g.numero("Slot"))
        assertEquals(-1489.5, g.decimale("x")!!, 0.001)
    }

    @Test
    fun `true e false sono byte`() {
        val g = Snbt.leggi("{OnGround: true, Sleeping: false}")
        assertEquals(1L, g.numero("OnGround"))
        assertEquals(0L, g.numero("Sleeping"))
    }

    @Test
    fun `le parole senza apici restano testo`() {
        // `minecraft:stone` arriva spesso nudo, senza virgolette.
        assertEquals("minecraft:stone", Snbt.leggi("{id: minecraft:stone}").testo("id"))
    }

    @Test
    fun `le stringhe con apostrofi e accenti non si rompono`() {
        val g = Snbt.leggi("""{a: "Spada di Anna", b: 'con "virgolette" dentro', c: "un\"apice"}""")
        assertEquals("Spada di Anna", g.testo("a"))
        assertEquals("con \"virgolette\" dentro", g.testo("b"))
        assertEquals("un\"apice", g.testo("c"))
    }

    @Test
    fun `le file di numeri restano file di numeri`() {
        // [B;…] e [I;…] non sono elenchi: se lo diventassero, i tipi non
        // tornerebbero piu' uguali a quelli del lettore binario.
        val g = Snbt.leggi("{b: [B; 1b, 2b, 3b], i: [I; 10, 20], l: [L; 5L], vuoto: [I;]}")
        assertTrue(g.campi["b"] is Tag.Byte)
        assertEquals(3, (g.campi["b"] as Tag.Byte).valori.size)
        assertTrue(g.campi["i"] is Tag.Numeri)
        assertEquals(20L, (g.campi["i"] as Tag.Numeri).valori[1])
        assertTrue(g.campi["l"] is Tag.Numeri)
        assertEquals(0, (g.campi["vuoto"] as Tag.Numeri).valori.size)
    }

    @Test
    fun `un elenco vuoto non e' un errore`() {
        assertTrue(Snbt.leggi("{Inventory: []}").elenco("Inventory").isEmpty())
    }

    // ------------------------------------------------- l'inventario, davvero

    private val rispostaVera =
        "Baisso has the following entity data: {Health: 14.0f, foodLevel: 17, XpLevel: 22, " +
                "SelectedItemSlot: 0, Dimension: \"minecraft:overworld\", " +
                "Pos: [1489.47d, 66.0d, -489.41d], Inventory: [" +
                "{Slot: 0b, id: \"minecraft:diamond_sword\", Count: 1b, " +
                "components: {\"minecraft:damage\": 120}}, " +
                "{Slot: 2b, id: \"minecraft:torch\", Count: 64b}, " +
                "{Slot: 9b, id: \"minecraft:cobblestone\", Count: 32b}, " +
                "{Slot: 103b, id: \"minecraft:diamond_helmet\", Count: 1b}, " +
                "{Slot: -106b, id: \"minecraft:shield\", Count: 1b}]}"

    @Test
    fun `la risposta del server si legge saltando il prefisso`() {
        // Il prefisso cambia con la lingua del server, quindi non lo si cerca:
        // si va alla prima graffa.
        val g = Snbt.leggiRisposta(rispostaVera)
        assertEquals(17L, g.numero("foodLevel"))
    }

    @Test
    fun `dallo stesso testo esce lo stesso inventario del lettore binario`() {
        val giocatore = Inventario.leggi(Snbt.leggiRisposta(rispostaVera))
        assertEquals("minecraft:diamond_sword", giocatore.cintura[0]?.id)
        assertEquals(64, giocatore.cintura[2]?.quantita)
        assertNull(giocatore.cintura[1])
        assertEquals("minecraft:cobblestone", giocatore.zaino[0]?.id)
        assertEquals("minecraft:diamond_helmet", giocatore.equipaggiamento.testa?.id)
        assertEquals("minecraft:shield", giocatore.equipaggiamento.manoSecondaria?.id)
        assertEquals(0, giocatore.inMano)
        assertEquals("7.0", giocatore.cuori)
        assertEquals(17, giocatore.fame)
        assertEquals(22, giocatore.livelli)
    }

    // -------------------------------------------------------- testi rotti

    @Test
    fun `un testo senza dati viene rifiutato`() {
        listOf("", "niente", "Baisso non ha dati").forEach {
            runCatching { Snbt.leggiRisposta(it) }.fold(
                onSuccess = { throw AssertionError("ha letto «$it»") },
                onFailure = { e -> assertTrue(e is Snbt.SnbtRotto) }
            )
        }
    }

    @Test
    fun `un gruppo che non si chiude viene rifiutato`() {
        listOf("{a: 1", "{a: ", "{a: \"senza fine", "{a: 1, }x", "[1, 2")
            .forEach { testo ->
                runCatching { Snbt.leggi(testo) }.fold(
                    onSuccess = { throw AssertionError("ha letto «$testo»") },
                    onFailure = { assertTrue(it is Snbt.SnbtRotto) }
                )
            }
    }

    @Test
    fun `un annidamento senza fine non fa esplodere niente`() {
        // Una risposta costruita apposta: senza un tetto, la pila finisce.
        val cattivo = "{a: ".repeat(500) + "1" + "}".repeat(500)
        runCatching { Snbt.leggi(cattivo) }.fold(
            onSuccess = { throw AssertionError("ha accettato 500 livelli") },
            onFailure = { assertTrue(it is Snbt.SnbtRotto) }
        )
    }

    @Test
    fun `gli spazi e gli a capo non contano`() {
        val g = Snbt.leggi("  {\n  a : 1 ,\n  b : [ 1 , 2 ]\n }  ")
        assertEquals(1L, g.numero("a"))
        assertEquals(2, g.elenco("b").size)
    }
}
