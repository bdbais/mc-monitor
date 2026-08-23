package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il controllo di sicurezza: severo di proposito, e con le istruzioni accanto. */
class SecurityCheckTest {

    private val buono = ServerConfig(
        user = "mcserver",
        password = "x",
        lgsmDir = "~/server1",
        script = "mcserver",
        rconEnabled = true,
        rconPassword = "unaPasswordLungaAbbastanza",
        rconTunnel = true,
        hostKeyFingerprint = "SHA256:qualcosa"
    )

    private val propsBuone = mapOf(
        "online-mode" to "true",
        "white-list" to "true",
        "enforce-whitelist" to "true",
        "enable-rcon" to "true",
        "rcon.password" to "unaPasswordLungaAbbastanza",
        "broadcast-rcon-to-ops" to "false",
        "enable-command-block" to "false",
        "spawn-protection" to "16"
    )

    @Test
    fun `un server chiuso bene prende alta`() {
        val r = SecurityCheck.valuta(propsBuone, buono, lockConfigurato = true)
        assertEquals(Livello.ALTO, r.livello)
        assertTrue(r.problemi.isEmpty())
    }

    @Test
    fun `senza whitelist si scende in fondo`() {
        val r = SecurityCheck.valuta(propsBuone + ("white-list" to "false"), buono, true)
        assertEquals(Livello.BASSO, r.livello)
        assertTrue(r.problemi.any { it.titolo.contains("Whitelist") })
    }

    @Test
    fun `senza controllo degli account si scende in fondo`() {
        assertEquals(
            Livello.BASSO,
            SecurityCheck.valuta(propsBuone + ("online-mode" to "false"), buono, true).livello
        )
    }

    @Test
    fun `una password RCON corta e' un problema serio`() {
        val r = SecurityCheck.valuta(propsBuone + ("rcon.password" to "abc"), buono, true)
        assertEquals(Livello.BASSO, r.livello)
        assertTrue(r.problemi.any { it.titolo.contains("RCON") })
    }

    @Test
    fun `una password RCON ovvia non passa`() {
        assertEquals(
            Livello.BASSO,
            SecurityCheck.valuta(propsBuone + ("rcon.password" to "minecraft"), buono, true).livello
        )
    }

    @Test
    fun `collegarsi come root e' un problema serio`() {
        assertEquals(
            Livello.BASSO,
            SecurityCheck.valuta(propsBuone, buono.copy(user = "root"), true).livello
        )
    }

    @Test
    fun `senza password dell'app si scende a media`() {
        assertEquals(
            Livello.MEDIO,
            SecurityCheck.valuta(propsBuone, buono, lockConfigurato = false).livello
        )
    }

    @Test
    fun `ogni rilievo dice cosa vuol dire`() {
        val r = SecurityCheck.valuta(propsBuone + ("white-list" to "false"), buono, false)
        r.controlli.forEach {
            assertTrue(it.titolo, it.spiegazione.length > 20)
            // Quello che non va deve dire anche come si sistema.
            if (it.esito == Esito.MALE) assertTrue(it.titolo, it.rimedio.isNotBlank())
        }
    }

    @Test
    fun `il riassunto cambia con il voto`() {
        assertTrue(
            SecurityCheck.valuta(propsBuone, buono, true).riassunto().contains("Nessun problema")
        )
        assertTrue(
            SecurityCheck.valuta(propsBuone + ("white-list" to "false"), buono, true)
                .riassunto().contains("problemi")
        )
    }
}
