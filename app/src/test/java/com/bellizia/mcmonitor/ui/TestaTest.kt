package com.bellizia.mcmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le facce disegnate dall'app.
 *
 * Servono a una cosa sola: che sei facce affiancate si distinguano da lontano
 * senza leggere i nomi. Tutto quello che si prova qui e' quella cosa — che siano
 * stabili, che siano diverse fra loro, e che nessuna venga fuori come una
 * macchia di un colore solo.
 */
class TestaTest {

    private fun stringa(griglia: Array<IntArray>) =
        griglia.joinToString("/") { riga -> riga.joinToString(",") { it.toString(16) } }

    @Test
    fun `la faccia di un giocatore non cambia mai`() {
        // Se cambiasse, ogni aggiornamento del cruscotto rimescolerebbe le
        // facce e non servirebbero piu' a riconoscere nessuno.
        assertEquals(stringa(Testa.disegna("Baisso")), stringa(Testa.disegna("Baisso")))
    }

    @Test
    fun `il server puo' scrivere il nome come vuole`() {
        // `list` e i log non sono sempre d'accordo sulle maiuscole: se contassero,
        // lo stesso giocatore avrebbe due facce.
        assertEquals(stringa(Testa.disegna("Baisso")), stringa(Testa.disegna("BAISSO")))
        assertEquals(stringa(Testa.disegna("Baisso")), stringa(Testa.disegna("baisso")))
    }

    @Test
    fun `nomi diversi danno facce diverse`() {
        val nomi = listOf(
            "Anna", "Bruno", "Carla", "Dario", "Elena", "Fabio",
            "Gino", "Ilaria", "Luca", "Marta", "Nico", "Olga",
            "Baisso", "Baissa", "Baisso1", "notursosa", "Steve", "Alex"
        )
        val facce = nomi.map { stringa(Testa.disegna(it)) }
        assertEquals("due nomi hanno la stessa faccia", nomi.size, facce.toSet().size)
    }

    @Test
    fun `due nomi che differiscono di una lettera non si somigliano`() {
        // E' il caso che capita davvero: Anna e Anno nella stessa schermata.
        val a = Testa.disegna("Anna").flatMap { it.toList() }
        val b = Testa.disegna("Anno").flatMap { it.toList() }
        val ugualiA = a.zip(b).count { (x, y) -> x == y }
        assertTrue("si somigliano su $ugualiA caselle su 64", ugualiA < 48)
    }

    @Test
    fun `nessuna faccia e' una macchia di un colore solo`() {
        // Capelli castani su pelle castana: da lontano non e' una faccia. E' il
        // motivo per cui il colore dei capelli non si prende dove capita.
        (0 until 3000).forEach { i ->
            val griglia = Testa.disegna("prova$i")
            val capelli = griglia[0][0]
            val pelle = griglia[4][3]
            assertTrue(
                "prova$i: capelli e pelle troppo simili",
                Testa.distanza(capelli, pelle) >= 30_000
            )
        }
    }

    @Test
    fun `gli occhi si vedono sempre`() {
        (0 until 3000).forEach { i ->
            val griglia = Testa.disegna("prova$i")
            val pelle = griglia[4][3]
            assertNotEquals("prova$i: il bianco dell'occhio sparisce", pelle, griglia[3][1])
            assertTrue(
                "prova$i: l'iride si confonde con la pelle",
                Testa.distanza(griglia[3][2], pelle) >= 10_000
            )
        }
    }

    @Test
    fun `la griglia e' otto per otto e tutta opaca`() {
        val griglia = Testa.disegna("Baisso")
        assertEquals(Testa.LATO, griglia.size)
        griglia.forEach { riga ->
            assertEquals(Testa.LATO, riga.size)
            riga.forEach { colore ->
                assertEquals("un pixel trasparente", 0xFF, (colore ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `un nome vuoto non fa saltare niente`() {
        assertEquals(Testa.LATO, Testa.disegna("").size)
    }
}
