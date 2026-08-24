package com.bellizia.mcmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Il nome tecnico non può essere di due server sullo stesso computer.
 *
 * Finisce nei marcatori del crontab e nei percorsi della posta, e ci finisce
 * ripulito: due nomi che dopo la ripulitura diventano uguali producono lo stesso
 * marcatore, e programmare il backup su uno cancella quello dell'altro senza
 * dire niente.
 */
class SlugTest {

    private fun srv(id: String, slug: String, host: String = "casa", user: String = "mc") =
        ServerConfig(id = id, slug = slug, host = host, user = user)

    @Test
    fun `un nome libero resta com'e'`() {
        val nuovo = srv("1", "mondo")
        assertEquals("mondo", slugLibero(nuovo, listOf()))
    }

    @Test
    fun `un nome gia' preso sullo stesso computer viene cambiato`() {
        val esistente = srv("1", "mondo")
        val nuovo = srv("2", "mondo")
        assertEquals("mondo-2", slugLibero(nuovo, listOf(esistente)))
    }

    @Test
    fun `due nomi che dopo la ripulitura sono uguali contano come uguali`() {
        val esistente = srv("1", "abcdef")
        val nuovo = srv("2", "abc/def")
        assertNotEquals("abcdef", slugLibero(nuovo, listOf(esistente)))
    }

    @Test
    fun `su computer diversi lo stesso nome va bene`() {
        val altrove = srv("1", "mondo", host = "altrove")
        val qui = srv("2", "mondo", host = "casa")
        assertEquals("mondo", slugLibero(qui, listOf(altrove)))
    }

    @Test
    fun `riaprire un server non ne cambia il nome`() {
        // markUsed() chiama save() a ogni apertura: rinominare li' orfanerebbe
        // in silenzio il blocco di cron gia' installato con il nome vecchio.
        val esistente = srv("1", "mondo")
        assertEquals("mondo", slugLibero(esistente, listOf(esistente)))
    }

    @Test
    fun `spostare un server su un computer dove il nome e' gia' preso lo fa cambiare`() {
        // La regressione trovata dalla revisione: l'uscita anticipata guardava
        // solo il nome, non il computer. Spostando il profilo su una macchina
        // dove un nome equivalente c'era gia', i due finivano con lo stesso
        // marcatore, e il backup programmato di uno spariva.
        val gia = srv("1", "abcdef", host = "casa")
        val prima = srv("2", "abc/def", host = "altrove")
        val dopo = prima.copy(host = "casa")
        assertNotEquals("abc/def", slugLibero(dopo, listOf(gia, prima)))
        assertNotEquals("abcdef", slugLibero(dopo, listOf(gia, prima)))
    }

    @Test
    fun `cambiare utente sullo stesso computer viene ricontrollato`() {
        val gia = srv("1", "mondo", user = "mc")
        val prima = srv("2", "mondo", user = "altro")
        val dopo = prima.copy(user = "mc")
        assertNotEquals("mondo", slugLibero(dopo, listOf(gia, prima)))
    }

    @Test
    fun `il nome resta corto abbastanza per i marcatori`() {
        val lungo = srv("2", "a".repeat(40))
        val gia = srv("1", "a".repeat(24))
        val esito = slugLibero(lungo, listOf(gia))
        assertEquals(esito, com.bellizia.mcmonitor.lgsm.Cron.normalizzaSlug(esito))
    }

    @Test
    fun `un nome vuoto o fatto di soli simboli diventa qualcosa di valido`() {
        assertEquals("server", slugLibero(srv("1", "!!!"), listOf()))
    }
}
