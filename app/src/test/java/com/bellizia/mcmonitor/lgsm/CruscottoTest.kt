package com.bellizia.mcmonitor.lgsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il cruscotto legge risposte a percorso singolo, che non sono gruppi: non c'e'
 * nessuna graffa da cui partire e il prefisso cambia con la lingua del server.
 * Se questa lettura sbaglia, il cruscotto mostra numeri finti — che e' peggio
 * che non mostrare niente, perche' uno ci crede.
 */
class CruscottoTest {

    private fun risposte(vararg coppie: Pair<String, String>): Map<String, String> =
        coppie.associate { (campo, valore) ->
            campo to "Baisso has the following entity data: $valore"
        }

    @Test
    fun `i cinque campi si leggono da risposte a valore singolo`() {
        val v = Cruscotto.vitali(
            "Baisso",
            risposte(
                "Health" to "14.0f",
                "foodLevel" to "17",
                "XpLevel" to "22",
                "Pos" to "[1489.47d, 66.0d, -489.41d]",
                "Dimension" to "\"minecraft:the_nether\"",
            )
        )
        assertEquals(14.0, v.vita!!, 0.001)
        assertEquals(17, v.fame)
        assertEquals(22, v.livelli)
        assertEquals("minecraft:the_nether", v.dimensione)
        assertEquals(-489.41, v.posizione!!.third, 0.01)
        assertEquals("7.0", v.cuori)
    }

    @Test
    fun `un prefisso in un'altra lingua non impedisce la lettura`() {
        // Il messaggio del server e' tradotto: cercarlo vorrebbe dire conoscere
        // tutte le lingue. Il valore pero' resta in fondo.
        val v = Cruscotto.vitali(
            "Baisso",
            mapOf(
                "Health" to "Baisso ha i seguenti dati entita: 6.0f",
                "Dimension" to "Baisso ha i seguenti dati entita: \"minecraft:overworld\"",
            )
        )
        assertEquals(6.0, v.vita!!, 0.001)
        assertEquals("minecraft:overworld", v.dimensione)
    }

    @Test
    fun `un errore del server non diventa un numero`() {
        // "No entity was found" finisce con una parola: se passasse per un
        // valore, la scheda direbbe zero cuori, cioe' morto.
        val v = Cruscotto.vitali(
            "Baisso",
            mapOf(
                "Health" to "No entity was found",
                "foodLevel" to "No entity was found",
                "Pos" to "No entity was found",
                "Dimension" to "No entity was found",
            )
        )
        assertNull(v.vita)
        assertNull(v.fame)
        assertNull(v.posizione)
        assertNull(v.dimensione)
        assertTrue(v.vuoto)
        assertEquals(Cruscotto.Allarme.NESSUNO, Cruscotto.allarme(v))
    }

    @Test
    fun `una dimensione deve avere la forma di una dimensione`() {
        // Una parola qualsiasi presa dalla coda di un errore non e' un mondo.
        val v = Cruscotto.vitali("Baisso", mapOf("Dimension" to "qualcosa e andato storto"))
        assertNull(v.dimensione)
    }

    @Test
    fun `un campo che manca resta vuoto e non zero`() {
        val v = Cruscotto.vitali("Baisso", emptyMap())
        assertNull(v.vita)
        assertNull(v.cuori)
    }

    @Test
    fun `la vita a zero e' morte, non solo vita bassa`() {
        assertEquals(
            Cruscotto.Allarme.MORTE,
            Cruscotto.allarme(Cruscotto.Vitali("a", vita = 0.0, fame = 20))
        )
    }

    @Test
    fun `la vita conta piu' della fame`() {
        // Chi ha due cuori e la pancia vuota ha un problema solo, ed e' i cuori.
        assertEquals(
            Cruscotto.Allarme.VITA,
            Cruscotto.allarme(Cruscotto.Vitali("a", vita = 4.0, fame = 2))
        )
        assertEquals(
            Cruscotto.Allarme.FAME,
            Cruscotto.allarme(Cruscotto.Vitali("a", vita = 20.0, fame = 2))
        )
        assertEquals(
            Cruscotto.Allarme.NESSUNO,
            Cruscotto.allarme(Cruscotto.Vitali("a", vita = 20.0, fame = 20))
        )
    }

    @Test
    fun `senza vita nota non si suona nessun allarme`() {
        assertEquals(Cruscotto.Allarme.NESSUNO, Cruscotto.allarme(null))
        assertEquals(
            Cruscotto.Allarme.NESSUNO,
            Cruscotto.allarme(Cruscotto.Vitali("a", vita = null, fame = null))
        )
    }

    // ------------------------------------------------------------- le domande

    @Test
    fun `non si chiede mai l'entita' intera`() {
        // Dentro c'e' recipeBook: decine di migliaia di byte di ricette
        // imparate, che da soli farebbero durare un giro piu' dell'intervallo
        // fra un giro e l'altro.
        val tutte = Cruscotto.domandeVitali("Baisso") + Cruscotto.domandaInventario("Baisso")
        tutte.forEach { domanda ->
            assertTrue(
                "«$domanda» chiede tutta l'entita'",
                domanda.trim().removePrefix("data get entity Baisso").isNotBlank()
            )
        }
        assertEquals(5, Cruscotto.domandeVitali("Baisso").size)
    }

    @Test
    fun `l'inventario da RCON e quello dal file danno lo stesso risultato`() {
        val inventario = "[{Slot: 0b, id: \"minecraft:diamond_sword\", Count: 1b}, " +
                "{Slot: 9b, id: \"minecraft:cobblestone\", Count: 32b}, " +
                "{Slot: 103b, id: \"minecraft:diamond_helmet\", Count: 1b}]"
        val g = Cruscotto.inventario("Baisso has the following entity data: $inventario")!!
        assertEquals("minecraft:diamond_sword", g.cintura[0]?.id)
        assertEquals(32, g.zaino[0]?.quantita)
        assertEquals("minecraft:diamond_helmet", g.equipaggiamento.testa?.id)
    }

    @Test
    fun `un inventario illeggibile non diventa un inventario vuoto`() {
        // Vuoto vorrebbe dire "gli hanno portato via tutto", che e' una notizia.
        assertNull(Cruscotto.inventario("No entity was found"))
        assertNull(Cruscotto.inventario(""))
    }

    // ------------------------------------------------------------ chi mostrare

    @Test
    fun `chi e' in gioco passa davanti a chi non c'e'`() {
        val scelti = listOf("Anna", "Bruno", "Carla", "Dario", "Elena", "Fabio", "Gino")
        val mostrati = Cruscotto.daMostrare(scelti, listOf("gino", "fabio", "carla"))
        assertEquals(Cruscotto.MASSIMO, mostrati.size)
        assertEquals(listOf("Carla", "Fabio", "Gino"), mostrati.take(3))
        // Chi resta fuori e' uno scollegato, non uno che sta giocando.
        assertTrue(listOf("Carla", "Fabio", "Gino").all { it in mostrati })
    }

    @Test
    fun `fra gli scollegati l'ordine scelto resta quello`() {
        val scelti = listOf("Anna", "Bruno", "Carla")
        assertEquals(scelti, Cruscotto.daMostrare(scelti, emptyList()))
    }
}
