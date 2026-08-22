package com.bellizia.mcmonitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il blocco è l'unica cosa fra un telefono trovato sul tavolo e la cancellazione
 * di un mondo: la parte che decide chi passa va provata pezzo per pezzo.
 */
class AppLockTest {

    @Test
    fun `la password giusta apre, quella sbagliata no`() {
        val salt = AppLock.newSalt()
        val hash = AppLock.hash("piccone42", salt)
        val saltB64 = AppLock.encode(salt)

        assertTrue(AppLock.verify("piccone42", saltB64, hash))
        assertFalse(AppLock.verify("piccone43", saltB64, hash))
        assertFalse(AppLock.verify("PICCONE42", saltB64, hash))
        assertFalse(AppLock.verify("", saltB64, hash))
    }

    @Test
    fun `due sali diversi danno impronte diverse per la stessa password`() {
        val uno = AppLock.hash("stessa", AppLock.newSalt())
        val due = AppLock.hash("stessa", AppLock.newSalt())
        // Senza sale due utenti con la stessa password avrebbero la stessa impronta.
        assertNotEquals(uno, due)
    }

    @Test
    fun `un sale nuovo e' davvero nuovo`() {
        val a = AppLock.encode(AppLock.newSalt())
        val b = AppLock.encode(AppLock.newSalt())
        assertNotEquals(a, b)
        assertEquals(16, AppLock.decode(a).size)
    }

    @Test
    fun `dati mancanti non aprono niente`() {
        val salt = AppLock.newSalt()
        val hash = AppLock.hash("x", salt)
        assertFalse(AppLock.verify("x", "", hash))
        assertFalse(AppLock.verify("x", AppLock.encode(salt), ""))
        // Un sale illeggibile non deve far esplodere niente: si dice solo di no.
        assertFalse(AppLock.verify("x", "non-base64!!", hash))
    }

    @Test
    fun `l'attesa decide quando richiudere`() {
        val uscita = 1_000_000L
        val unMinuto = uscita + 60_000L
        val dieciMinuti = uscita + 600_000L

        // Due minuti di tolleranza: dopo uno si passa, dopo dieci no.
        assertFalse(AppLock.expired(uscita, unMinuto, 2))
        assertTrue(AppLock.expired(uscita, dieciMinuti, 2))

        // Zero significa ogni volta, anche tornando subito.
        assertTrue(AppLock.expired(uscita, uscita + 1, 0))

        // Negativo significa mai, finché l'app resta viva.
        assertFalse(AppLock.expired(uscita, dieciMinuti, -1))

        // Senza un passaggio in secondo piano non scade niente.
        assertFalse(AppLock.expired(0L, dieciMinuti, 0))
    }

    @Test
    fun `sbloccando si azzera il conto`() {
        AppLock.lockNow()
        assertTrue(AppLock.locked)
        AppLock.unlock()
        assertFalse(AppLock.locked)

        // Va in secondo piano e torna subito: con due minuti di tolleranza resta aperta.
        AppLock.onBackground(1_000L)
        AppLock.onForeground(2_000L, 2)
        assertFalse(AppLock.locked)

        // Va in secondo piano e torna dopo mezz'ora: si richiude.
        AppLock.onBackground(1_000L)
        AppLock.onForeground(1_000L + 1_800_000L, 2)
        assertTrue(AppLock.locked)
        AppLock.unlock()
    }
}
